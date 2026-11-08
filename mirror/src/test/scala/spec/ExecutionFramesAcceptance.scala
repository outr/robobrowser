package spec

import fabric.*
import fabric.io.JsonFormatter
import okhttp3.mockwebserver.{Dispatcher,MockResponse,MockWebServer,RecordedRequest}
import robobrowser.{BrowserHost,BrowserNetworkPolicy}
import robobrowser.mirror.{ExecutionServer}
import java.nio.file.{Files,Path}

/** Agent access to a cross-origin child document, navigation and removal. */
object ExecutionFramesAcceptance {
  def main(args:Array[String]):Unit={
    val site=new MockWebServer();val embedded=new MockWebServer()
    site.start(java.net.InetAddress.getByName("127.0.0.1"),0);embedded.start(java.net.InetAddress.getByName("127.0.0.1"),0)
    val base="http://127.0.0.1:"+site.getPort;val childBase="http://127.0.0.1:"+embedded.getPort
    val writes=new java.util.concurrent.atomic.AtomicInteger()
    site.setDispatcher(new Dispatcher {override def dispatch(r:RecordedRequest)=new MockResponse().setHeader("Content-Type","text/html").setBody(s"<title>Frame owner</title><iframe id='embedded' src='$childBase/child'></iframe>")})
    embedded.setDispatcher(new Dispatcher {override def dispatch(r:RecordedRequest):MockResponse={
      if(r.getPath=="/write"){writes.incrementAndGet();new MockResponse().setBody("saved")}
      else new MockResponse().setHeader("Content-Type","text/html").setBody("<title>Child "+r.getPath+"</title><input id='entry'><button id='save' onclick=\"fetch('/write',{method:'POST'}).then(()=>window.saved=true)\">Save</button>")
    }})
    val host=BrowserHost.start().sync();val front=BrowserHost.start().sync()
    val server=new ExecutionServer(0,networkPolicy=BrowserNetworkPolicy(allowedHosts=List("127.0.0.1"),allowedPorts=List(site.getPort,embedded.getPort),allowPrivateNetworks=true))
    val out=Path.of("target/execution-frames");Files.createDirectories(out);val checks=scala.collection.mutable.ListBuffer.empty[String]
    def q(s:String)=JsonFormatter.Compact(str(s))
    def check(name:String)(test: => Boolean):Unit={require(test,name);checks+=name;println("PASS "+name)}
    def until(name:String)(test: => Boolean):Unit={val end=System.nanoTime()+20000000000L;var ok=false;while(!ok&&System.nanoTime()<end){ok=scala.util.Try(test).getOrElse(false);if(!ok)Thread.sleep(100)};check(name)(ok)}
    def report(status:String,error:Option[String]=None):Unit=Files.writeString(out.resolve("result.json"),JsonFormatter.Default(obj("status"->str(status),"checks"->arr(checks.toList.map(str)*),"error"->error.map(str).getOrElse(Null))))
    try {
      server.start();val page=front.open(server.address+"/execution#"+server.token).sync()
      def js(code:String)=page.evaluate(code).sync()
      def local(code:String)=js("local('eval',{code:"+q(code)+"})")
      until("Frames shell ready")(js("typeof api==='function'").asBoolean)
      js("open("+q(base+"/")+")");until("Frame owner ready")(js("agentEpoch!==null").asBoolean&&local("document.title==='Frame owner'").asBoolean)
      val epoch=js("agentEpoch")
      until("Cross-origin frame is available to the agent")(js("agentCommand({op:'frames'}).then(f=>f.length===1&&f[0].connected&&f[0].selector==='#embedded')").asBoolean)
      js("agentCommand({op:'frames'}).then(f=>window.frameHandle=f[0])")
      js("agentCommand({op:'frameCommand',frameId:frameHandle.id,frameEpoch:frameHandle.epoch,command:{op:'eval',code:\"window.steal=setInterval(()=>document.querySelector('#entry').focus(),20);true\"}})")
      js("$('url').focus();$('url').select();true")
      Thread.sleep(200)
      check("Embedded page cannot steal toolbar focus")(js("document.activeElement===$('url')").asBoolean)
      js("agentCommand({op:'frameCommand',frameId:frameHandle.id,frameEpoch:frameHandle.epoch,command:{op:'eval',code:'clearInterval(steal);true'}})")
      js("$('url').blur();true")
      check("Embedded frames are separate from popup windows")(js("agentCommand({op:'windows'}).then(w=>w.length===0)").asBoolean)
      check("Agent reads the real cross-origin document")(js("agentCommand({op:'frameCommand',frameId:frameHandle.id,frameEpoch:frameHandle.epoch,command:{op:'read'}}).then(r=>r.title==='Child /child')").asBoolean)
      js("agentCommand({op:'frameCommand',frameId:frameHandle.id,frameEpoch:frameHandle.epoch,command:{op:'type',selector:'#entry',value:'frame text'}})")
      check("Agent edits the child input")(js("agentCommand({op:'frameCommand',frameId:frameHandle.id,frameEpoch:frameHandle.epoch,command:{op:'read'}}).then(r=>r.elements.find(e=>e.selector==='#entry').value==='frame text')").asBoolean)
      val click="agentCommand({op:'frameCommand',frameId:frameHandle.id,frameEpoch:frameHandle.epoch,command:{op:'click',selector:'#save'}},'frame-save')"
      js(click);js(click);until("Child action executes once across retry")(writes.get()==1)
      local("document.querySelector('#embedded').src="+q(childBase+"/next")+";true")
      until("Frame navigation publishes a new document epoch")(js("agentCommand({op:'frames'}).then(f=>f.length===1&&f[0].connected&&f[0].epoch!==frameHandle.epoch)").asBoolean)
      check("Stale child-document command is rejected")(js("agentCommand({op:'frameCommand',frameId:frameHandle.id,frameEpoch:frameHandle.epoch,command:{op:'click',selector:'#save'}}).then(()=>false,()=>true)").asBoolean&&writes.get()==1)
      check("Child navigation preserves the owning agent epoch")(js("agentEpoch")==epoch)
      local("document.querySelector('#embedded').remove();true")
      check("Removed iframe disappears from agent frame list")(js("agentCommand({op:'frames'}).then(f=>f.length===0)").asBoolean)
      check("Detached child cannot receive further commands")(js("agentCommand({op:'frameCommand',frameId:frameHandle.id,frameEpoch:frameHandle.epoch,command:{op:'read'}}).then(()=>false,()=>true)").asBoolean)
      check("Frame workflow leaves no viewer or page errors")(js("local('state')")("metrics")("errors").asVector.isEmpty&&js("$('error').textContent").asString.isEmpty)
      js("close()");report("passed")
    }catch{case e:Throwable=>report("failed",Some(e.toString));throw e}
    finally{server.stop();front.stop().sync();host.stop().sync();site.shutdown();embedded.shutdown()}
  }
}
