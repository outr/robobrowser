package spec

import fabric.*
import fabric.io.JsonFormatter
import robobrowser.BrowserHost
import robobrowser.mirror.{ExecutionServer}
import java.nio.file.{Files,Path}

/** Force delayed old-document notifications across a new page handshake. */
object ExecutionHandshakeAcceptance {
  def main(args:Array[String]):Unit={
    val host=BrowserHost.start().sync();val front=BrowserHost.start().sync()
    val server=new ExecutionServer(0)
    val out=Path.of("target/execution-handshake");Files.createDirectories(out)
    val checks=scala.collection.mutable.ListBuffer.empty[String]
    def check(name:String)(test: => Boolean):Unit={require(test,name);checks+=name;println("PASS "+name)}
    def until(name:String)(test: => Boolean):Unit={val end=System.nanoTime()+10000000000L;var ok=false;while(!ok&&System.nanoTime()<end){ok=scala.util.Try(test).getOrElse(false);if(!ok)Thread.sleep(50)};check(name)(ok)}
    def report(status:String,error:Option[String]=None):Unit=Files.writeString(out.resolve("result.json"),JsonFormatter.Default(obj("status"->str(status),"checks"->arr(checks.toList.map(str)*),"error"->error.map(str).getOrElse(Null))))
    try{
      server.start();val page=front.open(server.address+"/execution#"+server.token).sync()
      def js(code:String)=page.evaluate(code).sync()
      until("Handshake shell ready")(js("typeof api==='function'").asBoolean)
      js("open('')");until("Initial agent attached")(js("agentEpoch!==null").asBoolean)
      val first=js("agentEpoch")
      js("window.oldHandler=port.onmessage;window.originalApi=api;window.held=false;api=async c=>{const r=await originalApi(c);if(c.op==='resolveLocation'){held=true;await new Promise(resolve=>window.releaseHandshake=resolve);}return r;};frame.contentWindow.postMessage({type:'rb-execution-reconnect',session:session.id},new URL(session.local).origin);true")
      until("Replacement handshake is held before attachment")(js("held").asBoolean)
      js("window.originalLocal=local;window.pollHeld=false;local=(op,args)=>op==='state'?new Promise((resolve,reject)=>{pollHeld=true;window.rejectOldPoll=()=>reject(Error('retired-state-error'));}):originalLocal(op,args);poll();true")
      until("Old-page poll is in flight during the replacement handshake")(js("pollHeld").asBoolean)
      js("oldHandler({data:{event:'leaving'}});rejectOldPoll();local=originalLocal;releaseHandshake();api=originalApi;true")
      until("Delayed old leaving does not cancel the replacement handshake")(js("agentEpoch!==null").asBoolean&&js("agentEpoch")!=first)
      val next=js("agentEpoch")
      js("oldHandler({data:{event:'leaving'}});true")
      check("Retired handler cannot detach the new document")(js("agentEpoch")==next)
      check("Agent commands reach the surviving document")(js("agentCommand({op:'read'}).then(r=>r.elements.some(e=>e.selector==='#increment'))").asBoolean)
      check("Handshake race leaves no viewer error")(js("$('error').textContent").asString.isEmpty)
      js("window.attachHeld=false;api=async c=>{const r=await originalApi(c);if(c.op==='agentAttach'){attachHeld=true;await new Promise(resolve=>window.releaseAttach=resolve);}return r;};frame.contentWindow.postMessage({type:'rb-execution-reconnect',session:session.id},new URL(session.local).origin);true")
      until("Server attachment acknowledgement is held")(js("attachHeld").asBoolean)
      val retiredAt=System.nanoTime()
      js("detach();releaseAttach();api=originalApi;true")
      until("Retired attachment releases server agent availability")(js("api({op:'agentState',id:session.id}).then(s=>!s.connected)").asBoolean)
      check("Retired attachment is released before the lease timeout")((System.nanoTime()-retiredAt)<2000000000L)
      js("frame.contentWindow.postMessage({type:'rb-execution-reconnect',session:session.id},new URL(session.local).origin);true")
      until("Agent reconnects after a retired attachment")(js("agentEpoch!==null").asBoolean)
      js("close()")
      js("window.openHeld=false;api=async c=>{const r=await originalApi(c);if(c.op==='open'){window.cancelledId=r.id;openHeld=true;await new Promise(resolve=>window.releaseOpen=resolve);}return r;};window.openTask=open('');true")
      until("New-session acknowledgement is held")(js("openHeld").asBoolean)
      js("close()")
      js("api=originalApi;releaseOpen();openTask")
      check("Close while opening cannot resurrect the cancelled session")(js("session===null&&frame.getAttribute('src')==='about:blank'&&$('status').textContent==='Closed'").asBoolean)
      check("Cancelled allocation is closed on the server")(js("api({op:'resume',id:cancelledId}).then(()=>false,()=>true)").asBoolean)
      js("open('')");until("Session opens after cancellation")(js("agentEpoch!==null").asBoolean)
      js("window.closeHeld=false;api=async c=>{const r=await originalApi(c);if(c.op==='close'){closeHeld=true;await new Promise(resolve=>window.releaseClose=resolve);}return r;};window.closeTask=close();true")
      until("Old-session close acknowledgement is held")(js("closeHeld").asBoolean)
      js("api=originalApi;open('')");until("Replacement connects before old close completes")(js("agentEpoch!==null").asBoolean)
      js("releaseClose();closeTask")
      check("Old close completion cannot overwrite replacement status")(js("$('status').textContent==='Local scripts running'&&agentEpoch!==null").asBoolean)
      js("close()");report("passed")
    }catch{case e:Throwable=>report("failed",Some(e.toString));throw e}
    finally{server.stop();front.stop().sync();host.stop().sync()}
  }
}
