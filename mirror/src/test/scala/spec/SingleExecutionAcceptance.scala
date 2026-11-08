package spec

import fabric.*
import fabric.io.{JsonFormatter,JsonParser}
import robobrowser.BrowserHost
import robobrowser.mirror.*
import java.net.URI
import java.net.http.{HttpClient,HttpRequest,HttpResponse}
import java.nio.file.{Files,Path}
import java.util.{Base64,UUID}

/** A real external HTTP agent controls the locally executing document; no second site execution. */
object SingleExecutionAcceptance {
  def main(args:Array[String]):Unit={
    val host=BrowserHost.start().sync();val front=BrowserHost.start().sync();val server=new ExecutionServer(0)
    val out=Path.of("target/single-execution");Files.createDirectories(out)
    val checks=scala.collection.mutable.ListBuffer.empty[String];var evidence:Json=obj();var captureFailure:()=>Unit=()=>()
    val http=HttpClient.newHttpClient()
    def checkOnce(ok:Boolean,message:String):Unit=require(ok,message)
    def q(s:String)=JsonFormatter.Compact(str(s))
    def check(name:String)(test: => Boolean):Unit={require(test,name);checks+=name;println("PASS "+name)}
    def until(name:String)(test: => Boolean):Unit={val end=System.nanoTime()+120000000000L;var ok=false;while(!ok&&System.nanoTime()<end){ok=scala.util.Try(test).getOrElse(false);if(!ok)Thread.sleep(100)};check(name)(ok)}
    def api(c:Json):Json={val request=HttpRequest.newBuilder(URI.create(server.address+"/execution/api")).header("Authorization","Bearer "+server.token).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(JsonFormatter.Compact(c))).build();JsonParser(http.send(request,HttpResponse.BodyHandlers.ofString()).body())}
    try{
      server.start();val existing=host.tabs.sync().map(_.id).toSet
      for(path<-List("/viewer.js","/projection","/api","/control","/canvas-viewer.js")) {
        val response=http.send(HttpRequest.newBuilder(URI.create(server.address+path)).GET().build(),HttpResponse.BodyHandlers.ofString())
        check("Retired route is absent: "+path)(response.statusCode()==404)
      }
      check("Dual execution is rejected")(api(obj("op"->str("open"),"mode"->str("dual")))("ok")==bool(false))
      val defaultSession=api(obj("op"->str("open")))("result")
      check("API defaults to one local executor")(defaultSession("mode")==str("local")&&defaultSession("tab")==Null)
      api(obj("op"->str("close"),"id"->defaultSession("id")))
      val view=front.open(server.address+"/#"+server.token).sync()
      view.send("Emulation.setDeviceMetricsOverride",obj("width"->num(1500),"height"->num(1100),"deviceScaleFactor"->num(1),"mobile"->bool(false))).sync()
      def js(s:String)=view.evaluate(s).sync()
      def local(s:String)=js("local('eval',{code:"+q(s)+"})")
      captureFailure=()=>{
        val diagnostic=scala.util.Try(js("Promise.all([local('state'),local('eval',{code:JSON.stringify('')+';({videos:[...document.querySelectorAll(\"video\")].map(v=>({src:v.currentSrc,ready:v.readyState,paused:v.paused,error:v.error?.code})),resources:performance.getEntriesByType(\"resource\").slice(-20).map(r=>({name:r.name,duration:r.duration}))})'})]).then(values=>({values,error:$('error').textContent}))")).getOrElse(Null)
        Files.writeString(out.resolve("failure-diagnostic.json"),JsonFormatter.Default(diagnostic))
        Files.write(out.resolve("failure.png"),Base64.getDecoder.decode(view.send("Page.captureScreenshot",obj("format"->str("png"))).sync()("data").asString))
      }
      until("Single execution shell ready")(js("typeof api==='function'").asBoolean)
      js("open('')")
      until("Local executor and agent relay connected")(js("agentEpoch!==null").asBoolean&&local("!!window.executionFixture").asBoolean)
      check("Navigation request metadata reaches the upstream server")(local("document.body.dataset.requestMode").asString=="navigate")
      check("HTML rewrite preserves adjacent text and element boundaries")(local("document.querySelector('#whitespace-check').childNodes.length===2&&document.querySelector('#whitespace-check').textContent==='onetwo'").asBoolean)
      val id=js("session.id");var epoch=js("agentEpoch")
      def command(c:Json,request:String=UUID.randomUUID().toString,ep:Json=epoch):Json=api(obj("op"->str("agent"),"id"->id,"epoch"->ep,"requestId"->str(request),"command"->c))
      def writes=api(obj("op"->str("counts"),"id"->id))("result")("writes").asInt
      check("No headless copy of the website is created")(host.tabs.sync().map(_.id).toSet==existing&&js("session.tab===null").asBoolean)
      val urls=local("(()=>{const s=document.querySelector('script[src$=\"fixture-execution.js\"]');return {attribute:s.getAttribute('src'),property:s.src,requests:performance.getEntriesByType('resource').map(r=>r.name)}})()")
      check("Website sees original script URL while browser fetches through proxy")(urls("attribute").asString=="/fixture-execution.js"&&urls("property").asString==server.address+"/fixture-execution.js"&&urls("requests").asVector.exists(u=>u.asString.contains("."+id.asString+".localhost:")&&u.asString.endsWith("/fixture-execution.js")))
      def auth(action:String,options:String="")=local("fetch('/fixture-execution-auth/"+action+"?run="+id.asString+"'"+options+").then(r=>r.json())")
      check("Cookie fixture begins signed out")(auth("me")("user")==Null)
      auth("login",",{method:'POST',body:'ignored-user',credentials:'omit'}")
      check("Credentials omit ignores response cookies")(auth("me")("user")==Null)
      auth("login",",{method:'POST',body:'fixture-user'}")
      check("Server cookie authenticates the next request")(auth("me")("user").asString=="fixture-user")
      check("Credentials omit sends no login cookie")(auth("me",",{credentials:'omit'}")("user")==Null)
      check("Request object preserves credentials semantics")(local("fetch(new Request('/fixture-execution-auth/me?run="+id.asString+"',{credentials:'omit'})).then(r=>r.json())")("user")==Null)
      val xhr=local("new Promise((resolve,reject)=>{const x=new XMLHttpRequest();x.open('GET','/fixture-execution-auth/me?run="+id.asString+"');x.responseType='json';x.onload=()=>resolve({data:x.response,url:x.responseURL});x.onerror=reject;x.send()})")
      check("XHR retains authenticated state and original response URL")(xhr("data")("user").asString=="fixture-user"&&xhr("url").asString==server.address+"/fixture-execution-auth/me?run="+id.asString)
      val visibleCookies=local("document.cookie").asString
      check("Document sees CSRF cookie but not HttpOnly login")(visibleCookies.contains("fixtureCSRF=visible")&& !visibleCookies.contains("fixtureLogin"))
      local("document.cookie='fromScript=written; Path=/';true")
      check("Script cookie reaches the upstream server")(auth("me")("scriptCookie").asBoolean)
      local("document.cookie='fixtureLogin=forged; Path=/';true")
      check("Script cannot replace the authenticated HttpOnly cookie")(auth("me")("user").asString=="fixture-user")
      auth("logout",",{method:'POST'}")
      check("Logout expires the server cookie")(auth("me")("user")==Null)
      val beforeFailure=local("executionFixture.state().identity")
      js("window.savedAPI=api;api=c=>c.op==='navigate'?Promise.reject(Error('Deliberate navigation failure')):savedAPI(c);open('https://example.com/')")
      js("api=savedAPI;true")
      until("Failed navigation reconnects the existing page without reload")(js("agentEpoch!==null").asBoolean&&local("executionFixture.state().identity")==beforeFailure)
      epoch=js("agentEpoch");js("$('error').textContent='';true")
      val concurrentId=UUID.randomUUID().toString
      val concurrent=obj("op"->str("eval"),"code"->str("window.concurrentCalls=(window.concurrentCalls||0)+1;new Promise(resolve=>setTimeout(()=>resolve(concurrentCalls),100))"))
      val first=java.util.concurrent.CompletableFuture.supplyAsync(()=>command(concurrent,concurrentId))
      val second=java.util.concurrent.CompletableFuture.supplyAsync(()=>command(concurrent,concurrentId))
      check("Concurrent retries share one execution")(first.get()("result").asInt==1&&second.get()("result").asInt==1&&local("concurrentCalls").asInt==1)
      val click=obj("op"->str("click"),"selector"->str("#increment"));val request=UUID.randomUUID().toString
      check("External agent click acknowledged")(command(click,request)("ok").asBoolean)
      until("Agent click submits exactly one POST")(writes==1&&local("executionFixture.state().count").asInt==1)
      check("Retry returns cached result")(command(click,request)("ok").asBoolean)
      Thread.sleep(250);check("Retry does not submit a second POST")(writes==1&&local("executionFixture.state().count").asInt==1)
      check("Reusing an ID with different content is rejected")(!command(obj("op"->str("eval"),"code"->str("1")),request)("ok").asBoolean)
      val r=local("document.querySelector('#increment').getBoundingClientRect().toJSON()");val f=js("frame.getBoundingClientRect().toJSON()")
      for(t<-List("mousePressed","mouseReleased"))view.send("Input.dispatchMouseEvent",obj("type"->str(t),"x"->num(f("x").asDouble+r("x").asDouble+30),"y"->num(f("y").asDouble+r("y").asDouble+20),"button"->str("left"),"clickCount"->num(1))).sync()
      until("Human and agent operate on the same state")(writes==2&&local("executionFixture.state().count").asInt==2)
      check("External agent edits native input")(command(obj("op"->str("type"),"selector"->str("#entry"),"value"->str("shared state")))("ok").asBoolean)
      check("Application input handler observes agent edit")(local("executionFixture.state().text").asString=="shared state")
      val read=command(obj("op"->str("read")))("result")
      check("External read observes the executing DOM")(read("text").asString.contains("Original JavaScript")&&read("elements").asVector.exists(e=>e("selector").asString=="#entry"&&e("value").asString=="shared state"))
      for(_<-0 until 130)require(command(obj("op"->str("eval"),"code"->str("true")))("ok").asBoolean)
      val expired=command(click,request)
      check("Evicted retry result never permits repeating an old action")(!expired("ok").asBoolean&&expired("code").asString=="expired_request"&&writes==2)
      val stuck=UUID.randomUUID().toString
      val pending=obj("op"->str("eval"),"code"->str("window.agentAttempts=(window.agentAttempts||0)+1;new Promise(()=>{})"))
      val timeout=command(pending,stuck)
      check("A timed-out dispatched operation reports unknown outcome")(!timeout("ok").asBoolean&&timeout("unknown").asBoolean)
      command(pending,stuck)
      check("Retrying a timed-out operation does not execute again")(local("agentAttempts").asInt==1)
      check("Further mutations are blocked after uncertain execution")(!command(click)("ok").asBoolean&&writes==2)
      local("window.focusTimer=setInterval(()=>document.querySelector('#entry').focus(),30);true")
      val bar=js("$('url').getBoundingClientRect().toJSON()")
      for(t<-List("mousePressed","mouseReleased"))view.send("Input.dispatchMouseEvent",obj("type"->str(t),"x"->num(bar("x").asDouble+40),"y"->num(bar("y").asDouble+20),"button"->str("left"),"clickCount"->num(1))).sync()
      js("$('url').select();true")
      for(c<-"github.com/focus-test"){
        Thread.sleep(50);checkOnce(js("document.activeElement===$('url')").asBoolean,"Local website stole toolbar focus")
        view.send("Input.insertText",obj("text"->str(c.toString))).sync()
      }
      check("Address typing retains focus while local site requests focus")(js("document.activeElement===$('url')&&$('url').value==='github.com/focus-test'").asBoolean&&local("executionFixture.state().text").asString=="shared state")
      local("clearInterval(focusTimer);true")
      val entry=local("document.querySelector('#entry').getBoundingClientRect().toJSON()")
      val owner=js("frame.getBoundingClientRect().toJSON()")
      for(t<-List("mousePressed","mouseReleased"))view.send("Input.dispatchMouseEvent",obj("type"->str(t),"x"->num(owner("x").asDouble+entry("x").asDouble+20),"y"->num(owner("y").asDouble+entry("y").asDouble+10),"button"->str("left"),"clickCount"->num(1))).sync()
      check("Real page interaction releases the toolbar focus guard")(local("document.querySelector('#increment').focus();document.activeElement.id==='increment'").asBoolean)

      val oldEpoch=epoch
      view.send("Page.reload").sync()
      until("Reload restores session and attaches a new document epoch")(js("session?.id")==id&&js("agentEpoch!==null").asBoolean&&js("agentEpoch")!=oldEpoch&&local("!!window.executionFixture").asBoolean)
      epoch=js("agentEpoch")
      check("Stale document action is rejected")(!command(click,UUID.randomUUID().toString,oldEpoch)("ok").asBoolean)
      check("Old request ID cannot be replayed into new document")(!command(click,request)("ok").asBoolean)
      check("New document action succeeds")(command(click)("ok").asBoolean)
      until("New document executes only the new action")(writes==3&&local("executionFixture.state().count").asInt==1)
      local("localStorage.setItem('navigation-check','retained');true")
      val previousLocation=js("session.local")
      val navigationEpoch=epoch
      val destination=server.address+"/fixture-execution?run="+id.asString+"&page=next"
      js("open("+q(destination)+")")
      until("Address navigation keeps session and reconnects agent")(js("session.id")==id&&js("agentEpoch!==null").asBoolean&&js("agentEpoch")!=navigationEpoch&&local("!!window.executionFixture").asBoolean)
      epoch=js("agentEpoch")
      check("Expected navigation cancellation does not show a viewer error")(js("$('error').textContent").asString.isEmpty)
      check("Navigation preserves origin storage")(local("localStorage.getItem('navigation-check')").asString=="retained")
      check("A delayed old document cannot overwrite the saved destination")(!api(obj("op"->str("location"),"id"->id,"epoch"->navigationEpoch,"url"->previousLocation))("ok").asBoolean&&api(obj("op"->str("resume"),"id"->id))("result")("url").asString==destination)
      check("Navigation rejects old document commands")(!command(click,UUID.randomUUID().toString,navigationEpoch)("ok").asBoolean)
      local("history.pushState({},'', '#section');true")
      until("Same-document URL is retained for resume")(api(obj("op"->str("resume"),"id"->id))("result")("url").asString==destination+"#section")
      check("Unregistered origin cannot claim executing document")(!api(obj("op"->str("location"),"id"->id,"url"->str("http://forged."+id.asString+".localhost:"+URI.create(server.address).getPort+"/")))("ok").asBoolean)
      local("history.replaceState({},'',"+q(server.address+"/nested/route")+");true")
      check("SPA relative URLs resolve against the current route")(local("(()=>{const a=document.createElement('a');a.href='child';return a.href})()").asString==server.address+"/nested/child")
      val fixture=local("executionFixture.state()")
      js("detach();frame.src='about:blank';true")
      until("Disconnect withdraws agent availability")(!api(obj("op"->str("agentState"),"id"->id))("result")("connected").asBoolean)
      check("Disconnected agent command fails without side effects")(!command(click)("ok").asBoolean&&writes==3)
      js("close()")
      evidence=obj("fixture"->fixture,"writes"->num(3),"headlessCopies"->num(0))
      if(args.contains("public")){
        js("open('https://github.com/')")
        until("GitHub script execution and relay ready")(js("agentEpoch!==null").asBoolean&&local("document.querySelectorAll('canvas').length>0").asBoolean)
        until("GitHub video decodes in local browser")(local("(()=>{const v=document.querySelector('video');return v&&v.readyState>=2&&!v.error})()").asBoolean)
        local("document.querySelector('video').scrollIntoView({block:'center'});true")
        until("GitHub plays its visible video")(local("!document.querySelector('video').paused").asBoolean)
        val before=local("document.querySelector('video').getVideoPlaybackQuality().totalVideoFrames").asLong
        Thread.sleep(3000)
        val after=local("document.querySelector('video').getVideoPlaybackQuality().totalVideoFrames").asLong
        check("GitHub native video frames advance")(after-before>=30)
        until("Media fallback completed without remaining errors"){
          val current=js("local('state')")
          evidence=obj((evidence.asObj.value.toList.filterNot(_._1=="github"):::List("github"->current))* )
          current("metrics")("errors").asVector.isEmpty&&current("metrics")("media").asVector.forall(m=>Set("recovered","superseded")(m("status").asString))
        }
        val state=js("local('state')")
        evidence=obj((evidence.asObj.value.toList:::List("github"->state,"decodedFramesOverThreeSeconds"->num(after-before)))* )
        Files.write(out.resolve("github.png"),Base64.getDecoder.decode(view.send("Page.captureScreenshot",obj("format"->str("png"))).sync()("data").asString))
        val publicId=js("session.id");val publicEpoch=js("agentEpoch")
        local("(()=>{const a=document.createElement('a');a.href='https://example.com/';a.textContent='Navigate';document.body.append(a);a.click();return true})()")
        until("Cross-origin link reconnects the same session")(js("session.id")==publicId&&js("agentEpoch!==null").asBoolean&&js("agentEpoch")!=publicEpoch&&local("document.title==='Example Domain'").asBoolean)
        until("Cross-origin URL appears in toolbar")(js("$('url').value==='https://example.com/'").asBoolean)
        check("Agent reads new cross-origin document")(js("agentCommand({op:'read'}).then(r=>r.title==='Example Domain')").asBoolean)
        view.send("Page.reload").sync()
        until("Shell reload resumes cross-origin destination")(js("session?.id")==publicId&&js("agentEpoch!==null").asBoolean&&local("document.title==='Example Domain'").asBoolean)
        js("open('facebook.com')")
        until("Facebook redirects to a working login page")(js("session?.id")==publicId&&js("agentEpoch!==null").asBoolean&&local("document.title.includes('Facebook')&&!!document.querySelector('input[type=password]')").asBoolean)
        Thread.sleep(3000)
        val facebookState=js("local('state')")
        Files.writeString(out.resolve("facebook-state.json"),JsonFormatter.Default(facebookState))
        check("Facebook startup has no script errors")(facebookState("metrics")("errors").asVector.isEmpty)
        check("Agent can read Facebook login controls")(js("agentCommand({op:'read'}).then(r=>r.elements.some(e=>e.tag==='input'))").asBoolean)
        Files.write(out.resolve("facebook.png"),Base64.getDecoder.decode(view.send("Page.captureScreenshot",obj("format"->str("png"))).sync()("data").asString))
        js("open('https://example.com/')")
        until("Navigation away from Facebook retains agent control")(js("session?.id")==publicId&&js("agentEpoch!==null").asBoolean&&local("document.title==='Example Domain'").asBoolean)
        val scriptEpoch=js("agentEpoch")
        local("setTimeout(()=>location.href='https://example.org/',10);true")
        until("Absolute script navigation stays in the managed frame")(js("session?.id")==publicId&&js("agentEpoch!==null").asBoolean&&js("agentEpoch")!=scriptEpoch&&js("local('state')")("url").asString=="https://example.org/")
        check("Agent follows script-driven cross-origin navigation")(js("agentCommand({op:'read'}).then(r=>r.title==='Example Domain')").asBoolean)
        js("close()")
      }
      check("No headless website copy leaked")(host.tabs.sync().map(_.id).toSet==existing)
      Files.writeString(out.resolve("result.json"),JsonFormatter.Default(obj("status"->str("passed"),"checks"->arr(checks.toList.map(str)*),"evidence"->evidence,"scope"->str("Local executor with server agent relay; not autonomous headless/CDP equivalence"))))
    }catch{case e:Throwable=>scala.util.Try(captureFailure());Files.writeString(out.resolve("result.json"),JsonFormatter.Default(obj("status"->str("failed"),"checks"->arr(checks.toList.map(str)*),"error"->str(e.toString),"evidence"->evidence)));throw e}
    finally{server.stop();front.stop().sync();host.stop().sync();http.close()}
  }
}
