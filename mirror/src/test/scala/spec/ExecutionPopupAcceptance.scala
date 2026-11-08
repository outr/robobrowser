package spec

import fabric.*
import fabric.io.JsonFormatter
import okhttp3.mockwebserver.{Dispatcher,MockResponse,MockWebServer,RecordedRequest}
import robobrowser.{BrowserHost,BrowserNetworkPolicy}
import robobrowser.mirror.{ExecutionServer}
import java.nio.file.{Files,Path}

/** Controlled cross-origin authorization redirect, opener identity and cookie continuity. */
object ExecutionPopupAcceptance {
  def main(args:Array[String]):Unit={
    val site=new MockWebServer();val auth=new MockWebServer()
    site.start(java.net.InetAddress.getByName("127.0.0.1"),0);auth.start(java.net.InetAddress.getByName("127.0.0.1"),0)
    val base="http://127.0.0.1:"+site.getPort;val identity="http://127.0.0.1:"+auth.getPort
    def q(s:String)=JsonFormatter.Compact(str(s))
    val calls=new java.util.concurrent.atomic.AtomicInteger()
    site.setDispatcher(new Dispatcher {override def dispatch(r:RecordedRequest):MockResponse={
      if(r.getPath=="/auth-state")return new MockResponse().setBody(Option(r.getHeader("Cookie")).getOrElse(""))
      val body=if(r.getPath=="/callback"){
        calls.incrementAndGet();s"<title>Callback</title><script>opener.postMessage({authorized:true},${q(base)});window.close();</script>"
      }else s"""<title>Popup owner</title><button id="login" onclick="window.child=window.open('$identity/auth','authorization','width=500,height=500')">Authorize</button><script>window.callback=null;window.identityMessage=null;addEventListener('message',e=>{if(e.origin===${q(base)})callback={data:e.data,sourceMatches:e.source===child,origin:e.origin};if(e.origin===${q(identity)})identityMessage={data:e.data,sourceMatches:e.source===child,origin:e.origin};});</script>"""
      val response=new MockResponse().setHeader("Content-Type","text/html").setBody(body)
      if(r.getPath=="/callback")response.setHeader("Set-Cookie","popupAuth=approved; Path=/; HttpOnly")else response
    }})
    auth.setDispatcher(new Dispatcher {override def dispatch(r:RecordedRequest)=new MockResponse().setHeader("Content-Type","text/html").setBody(s"""<title>Authorization provider</title><button id="message" onclick="opener.postMessage({provider:true},'$base')">Message</button><button id="approve" onclick="location.href='$base/callback'">Approve</button>""")})
    val host=BrowserHost.start().sync();val front=BrowserHost.start().sync()
    val server=new ExecutionServer(0,networkPolicy=BrowserNetworkPolicy(allowedHosts=List("127.0.0.1"),allowedPorts=List(site.getPort,auth.getPort),allowPrivateNetworks=true))
    val out=Path.of("target/execution-popups");Files.createDirectories(out);val checks=scala.collection.mutable.ListBuffer.empty[String]
    def check(name:String)(test: => Boolean):Unit={require(test,name);checks+=name;println("PASS "+name)}
    def until(name:String)(test: => Boolean):Unit={val end=System.nanoTime()+20000000000L;var ok=false;while(!ok&&System.nanoTime()<end){ok=scala.util.Try(test).getOrElse(false);if(!ok)Thread.sleep(100)};check(name)(ok)}
    def report(status:String,error:Option[String]=None):Unit=Files.writeString(out.resolve("result.json"),JsonFormatter.Default(obj("status"->str(status),"checks"->arr(checks.toList.map(str)*),"error"->error.map(str).getOrElse(Null))))
    try {
      server.start();val main=front.open(server.address+"/execution#"+server.token).sync()
      def js(code:String)=main.evaluate(code).sync()
      def local(code:String)=js("local('eval',{code:"+q(code)+"})")
      until("Popup shell ready")(js("typeof api==='function'").asBoolean)
      js("open("+q(base+"/")+")")
      until("Popup owner ready")(js("agentEpoch!==null").asBoolean&&local("!!document.querySelector('#login')").asBoolean)
      check("Self-targeted window.open preserves native identity")(local("window.open('','_self')===window").asBoolean)
      val epoch=js("agentEpoch");val initial=front.tabs.sync().map(_.id).toSet
      val frame=js("frame.getBoundingClientRect().toJSON()")
      val button=local("document.querySelector('#login').getBoundingClientRect().toJSON()")
      val x=frame("x").asDouble+button("x").asDouble+10;val y=frame("y").asDouble+button("y").asDouble+10
      for(kind<-List("mousePressed","mouseReleased"))main.send("Input.dispatchMouseEvent",obj("type"->str(kind),"button"->str("left"),"clickCount"->num(1),"x"->num(x),"y"->num(y))).sync()
      until("Website popup opens")(front.tabs.sync().exists(t=> !initial(t.id)))
      val popup=front.tabs.sync().find(t=> !initial(t.id)).get
      until("Popup loads the mapped authorization provider")(popup.evaluate("document.title==='Authorization provider'").sync().asBoolean)
      check("Popup remains on a mapped session origin")(popup.evaluate("location.hostname").sync().asString.endsWith("."+js("session.id").asString+".localhost"))
      until("Popup is available to the server agent")(js("agentCommand({op:'windows'}).then(w=>w.length===1&&w[0].connected)").asBoolean)
      js("agentCommand({op:'windows'}).then(w=>window.popupHandle=w[0])")
      check("Agent reads the cross-origin popup document")(js("agentCommand({op:'windowCommand',windowId:popupHandle.id,windowEpoch:popupHandle.epoch,command:{op:'read'}}).then(r=>r.title==='Authorization provider')").asBoolean)
      js("agentCommand({op:'windowCommand',windowId:popupHandle.id,windowEpoch:popupHandle.epoch,command:{op:'click',selector:'#message'}})")
      until("Cross-origin popup message preserves original origin and source")(local("identityMessage?.data.provider&&identityMessage.sourceMatches").asBoolean)
      val listen="window.ownerMessage=null;addEventListener('message',e=>{if(e.origin==="+q(base)+")ownerMessage={data:e.data,source:e.source===opener};});true"
      js("agentCommand({op:'windowCommand',windowId:popupHandle.id,windowEpoch:popupHandle.epoch,command:{op:'eval',code:"+q(listen)+"}})")
      local("child.postMessage({owner:true},"+q(identity)+");true")
      until("Owner-to-popup message preserves origin and opener identity")(js("agentCommand({op:'windowCommand',windowId:popupHandle.id,windowEpoch:popupHandle.epoch,command:{op:'eval',code:'!!ownerMessage?.data.owner&&ownerMessage.source'}})").asBoolean)
      popup.send("Page.reload").sync()
      until("Popup reload publishes a new document epoch")(js("agentCommand({op:'windows'}).then(w=>w.length===1&&w[0].connected&&w[0].epoch!==popupHandle.epoch)").asBoolean)
      check("Stale popup commands are rejected before execution")(js("agentCommand({op:'windowCommand',windowId:popupHandle.id,windowEpoch:popupHandle.epoch,command:{op:'click',selector:'#approve'}}).then(()=>false,()=>true)").asBoolean&&calls.get()==0)
      js("agentCommand({op:'windows'}).then(w=>window.popupHandle=w[0])")
      js("agentCommand({op:'windowCommand',windowId:popupHandle.id,windowEpoch:popupHandle.epoch,command:{op:'click',selector:'#approve'}})")
      until("Authorization callback reaches the owning page")(local("callback?.data.authorized&&callback.sourceMatches").asBoolean)
      until("Popup closes after callback")(local("child.closed").asBoolean)
      check("Closed popup is removed from agent window list")(js("agentCommand({op:'windows'}).then(w=>w.length===0)").asBoolean)
      check("Callback executes once")(calls.get()==1)
      check("Callback does not replace or detach the main agent")(js("agentEpoch")==epoch&&local("document.title").asString=="Popup owner")
      check("Popup HttpOnly cookie is shared with the owner but hidden from scripts")(!local("document.cookie").asString.contains("popupAuth")&&local("fetch('/auth-state').then(r=>r.text())").asString.contains("popupAuth=approved"))
      local("document.querySelector('#login').onclick=()=>{window.child=window.open('','blank-authorization');child.location="+q(identity+"/auth")+";};true")
      for(kind<-List("mousePressed","mouseReleased"))main.send("Input.dispatchMouseEvent",obj("type"->str(kind),"button"->str("left"),"clickCount"->num(1),"x"->num(x),"y"->num(y))).sync()
      until("Initial blank popup navigates through the managed session")(js("agentCommand({op:'windows'}).then(w=>w.length===1&&w[0].connected)").asBoolean)
      js("agentCommand({op:'windows'}).then(w=>window.popupHandle=w[0])")
      check("Agent reads a popup opened blank then navigated")(js("agentCommand({op:'windowCommand',windowId:popupHandle.id,windowEpoch:popupHandle.epoch,command:{op:'read'}}).then(r=>r.title==='Authorization provider')").asBoolean)
      val delayed="agentCommand({op:'windowCommand',windowId:popupHandle.id,windowEpoch:popupHandle.epoch,command:{op:'eval',code:\"window.late=(window.late||0)+1;document.title='Late '+late;new Promise(()=>{})\"}},'delayed-popup-once')"
      check("Timed-out popup mutation reports an unknown result")(js(delayed+".then(()=>false,()=>true)").asBoolean)
      until("Unknown popup outcome blocks further mutations")(js("api({op:'agentState',id:session.id}).then(s=>s.uncertain)").asBoolean)
      until("Read-only inspection remains available after a popup timeout")(js("agentCommand({op:'windowCommand',windowId:popupHandle.id,windowEpoch:popupHandle.epoch,command:{op:'read'}}).then(r=>r.title==='Late 1')").asBoolean)
      check("Retry of a timed-out popup command does not run it again")(js(delayed+".then(()=>false,()=>true)").asBoolean)
      check("A new popup mutation is rejected while its outcome is uncertain")(js("agentCommand({op:'windowCommand',windowId:popupHandle.id,windowEpoch:popupHandle.epoch,command:{op:'eval',code:\"document.body.innerText+='FORBIDDEN MUTATION';true\"}}).then(()=>false,()=>true)").asBoolean)
      Thread.sleep(250)
      check("Unacknowledged mutation ran once and rejected mutations had no effect")(js("agentCommand({op:'windowCommand',windowId:popupHandle.id,windowEpoch:popupHandle.epoch,command:{op:'read'}}).then(r=>r.title==='Late 1'&&!r.text.includes('FORBIDDEN MUTATION'))").asBoolean)
      local("child.close();true")
      until("Closing a managed window releases its agent handle")(js("agentCommand({op:'windows'}).then(w=>w.length===0)").asBoolean)
      check("Popup leaves no page or viewer errors")(js("local('state')")("metrics")("errors").asVector.isEmpty&&js("$('error').textContent").asString.isEmpty)
      js("close()");report("passed")
    }catch{case e:Throwable=>report("failed",Some(e.toString));throw e}
    finally{server.stop();front.stop().sync();host.stop().sync();site.shutdown();auth.shutdown()}
  }
}
