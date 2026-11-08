package spec

import fabric.*
import fabric.io.JsonFormatter
import okhttp3.mockwebserver.{Dispatcher,MockResponse,MockWebServer,RecordedRequest}
import robobrowser.{BrowserHost,BrowserNetworkPolicy}
import robobrowser.mirror.{ExecutionServer}
import java.nio.file.{Files,Path}

/** Browser-native POST navigation must stay managed and preserve the submitted body once. */
object ExecutionFormsAcceptance {
  def main(args:Array[String]):Unit={
    val source=new MockWebServer();source.start(java.net.InetAddress.getByName("127.0.0.1"),0)
    val second=new MockWebServer();second.start(java.net.InetAddress.getByName("127.0.0.1"),0)
    val base="http://127.0.0.1:"+source.getPort
    val other="http://127.0.0.1:"+second.getPort
    val links=(0 until 100).map(i=>s"<a href='https://unvisited-$i.invalid/'>Language $i</a>").mkString+s"<a id='lazy' href='$other/lazy'>Visit</a>"
    val posts=new java.util.concurrent.CopyOnWriteArrayList[RecordedRequest]()
    val dispatcher=new Dispatcher {override def dispatch(r:RecordedRequest):MockResponse={
      if(r.getMethod=="POST")posts.add(r)
      new MockResponse().setHeader("Content-Type","text/html").setBody("<!doctype html><title>Forms "+r.getPath+"</title><form id='form' method='post' action='/posted'><input name='value' value='one & two'><button id='submit' type='submit'>Send</button><input id='image' type='submit' value='Input submit'></form>"+links)
    }}
    source.setDispatcher(dispatcher);second.setDispatcher(dispatcher)
    val host=BrowserHost.start().sync();val front=BrowserHost.start().sync()
    val server=new ExecutionServer(0,networkPolicy=BrowserNetworkPolicy(allowedHosts=List("127.0.0.1"),allowedPorts=List(source.getPort,second.getPort),allowPrivateNetworks=true),executionOriginLimit=8)
    val out=Path.of("target/execution-forms");Files.createDirectories(out);val checks=scala.collection.mutable.ListBuffer.empty[String]
    def q(s:String)=JsonFormatter.Compact(str(s))
    def check(name:String)(test: => Boolean):Unit={require(test,name);checks+=name;println("PASS "+name)}
    def until(name:String)(test: => Boolean):Unit={val end=System.nanoTime()+20000000000L;var ok=false;while(!ok&&System.nanoTime()<end){ok=scala.util.Try(test).getOrElse(false);if(!ok)Thread.sleep(100)};check(name)(ok)}
    def report(status:String,error:Option[String]=None):Unit=Files.writeString(out.resolve("result.json"),JsonFormatter.Default(obj("status"->str(status),"checks"->arr(checks.toList.map(str)*),"error"->error.map(str).getOrElse(Null))))
    try {
      server.start();val page=front.open(server.address+"/execution#"+server.token).sync()
      def js(code:String)=page.evaluate(code).sync()
      def local(code:String)=js("local('eval',{code:"+q(code)+"})")
      until("Forms shell ready")(js("typeof api==='function'").asBoolean)
      js("open("+q(base+"/")+")");until("Forms page ready")(js("agentEpoch!==null").asBoolean&&local("!!document.querySelector('#form')").asBoolean)
      check("Unvisited cross-origin links do not exhaust the configured origin budget")(local("document.querySelectorAll('a').length===101&&document.querySelector('#lazy').outerHTML.includes('/__rb_execution/navigate?')").asBoolean)
      val beforeLink=js("agentEpoch")
      js("agentCommand({op:'click',selector:'#lazy'})")
      until("Deferred cross-origin link resolves and reconnects the agent")(js("agentEpoch!==null").asBoolean&&js("agentEpoch")!=beforeLink&&local("document.title==='Forms /lazy'").asBoolean)
      check("Deferred navigation exposes the original destination URL")(js("$('url').value").asString==other+"/lazy")
      val id=js("session.id")
      val firstEpoch=js("agentEpoch")
      local("document.querySelector('#submit').formAction="+q(base+"/override")+";true")
      check("Button formAction exposes the original URL")(local("document.querySelector('#submit').formAction").asString==base+"/override")
      js("agentCommand({op:'click',selector:'#submit'})")
      until("POST submitter override reconnects the managed agent")(js("agentEpoch!==null").asBoolean&&js("agentEpoch")!=firstEpoch&&local("document.title==='Forms /override'").asBoolean)
      check("POST body and route reach the upstream exactly once")(posts.size()==1&&posts.get(0).getPath=="/override"&&posts.get(0).getBody.readUtf8()=="value=one+%26+two")
      val secondEpoch=js("agentEpoch")
      local("document.querySelector('#form').action="+q(base+"/script-submit")+";HTMLFormElement.prototype.submit.call(document.querySelector('#form'));true")
      until("Native form.submit preserves managed navigation")(js("agentEpoch!==null").asBoolean&&js("agentEpoch")!=secondEpoch&&local("document.title==='Forms /script-submit'").asBoolean)
      check("Native form.submit sends the original body once")(posts.size()==2&&posts.get(1).getPath=="/script-submit"&&posts.get(1).getBody.readUtf8()=="value=one+%26+two")
      val thirdEpoch=js("agentEpoch")
      local("document.querySelector('#image').formAction="+q(base+"/input-submit")+";true")
      check("Input formAction exposes the original URL")(local("document.querySelector('#image').formAction").asString==base+"/input-submit")
      js("agentCommand({op:'click',selector:'#image'})")
      until("Input submitter preserves managed navigation")(js("agentEpoch!==null").asBoolean&&js("agentEpoch")!=thirdEpoch&&local("document.title==='Forms /input-submit'").asBoolean)
      check("All form navigation retains the same session")(js("session.id")==id&&posts.size()==3)
      check("Form navigation leaves no page or viewer errors")(js("local('state')")("metrics")("errors").asVector.isEmpty&&js("$('error').textContent").asString.isEmpty)
      check("Exhausted origin mapping fails without replacing the prior native URL")(local("(()=>{const a=document.createElement('a');a.href='/safe';for(let i=0;i<70;i++){const before=a.getAttribute('href');try{a.href='https://budget-'+i+'.invalid/path';}catch(e){return i>0&&a.getAttribute('href')===before&&String(e).includes('Unable to map');}}return false;})()").asBoolean)
      js("close()");report("passed")
    }catch{case e:Throwable=>report("failed",Some(e.toString));throw e}
    finally{server.stop();front.stop().sync();host.stop().sync();source.shutdown();second.shutdown()}
  }
}
