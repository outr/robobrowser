package spec

import fabric.*
import fabric.io.JsonFormatter
import okhttp3.{Response,WebSocket,WebSocketListener}
import okhttp3.mockwebserver.{MockResponse,MockWebServer}
import robobrowser.{BrowserHost,BrowserNetworkPolicy}
import robobrowser.mirror.{ExecutionServer}
import java.nio.file.{Files,Path}

/** Real browser API, gateway transport and independent upstream echo server. */
object ExecutionSocketAcceptance {
  def main(args:Array[String]):Unit={
    val echo=new MockWebServer();echo.start(java.net.InetAddress.getByName("127.0.0.1"),0)
    val received=new java.util.concurrent.CopyOnWriteArrayList[String]()
    echo.enqueue(new MockResponse().setHeader("Sec-WebSocket-Protocol","echo-test").setHeader("Set-Cookie","socketCookie=received; Path=/").withWebSocketUpgrade(new WebSocketListener {
      override def onMessage(socket:WebSocket,text:String):Unit={received.add(text);socket.send(text)}
      override def onMessage(socket:WebSocket,bytes:okio.ByteString):Unit={received.add(bytes.hex());socket.send(bytes)}
      override def onClosing(socket:WebSocket,code:Int,reason:String):Unit={socket.close(code,reason)}
    }))
    val host=BrowserHost.start().sync();val front=BrowserHost.start().sync()
    val server=new ExecutionServer(0,networkPolicy=BrowserNetworkPolicy(allowedHosts=List("127.0.0.1"),allowedPorts=List(echo.getPort),allowPrivateNetworks=true))
    val out=Path.of("target/execution-sockets");Files.createDirectories(out)
    val checks=scala.collection.mutable.ListBuffer.empty[String]
    def check(name:String)(test: => Boolean):Unit={require(test,name);checks+=name;println("PASS "+name)}
    def until(name:String)(test: => Boolean):Unit={val end=System.nanoTime()+30000000000L;var ok=false;while(!ok&&System.nanoTime()<end){ok=scala.util.Try(test).getOrElse(false);if(!ok)Thread.sleep(50)};check(name)(ok)}
    try {
      server.start();val page=front.open(server.address+"/execution#"+server.token).sync()
      def js(code:String)=page.evaluate(code).sync()
      def local(code:String)=js("local('eval',{code:"+JsonFormatter.Compact(str(code))+"})")
      until("Socket shell ready")(js("typeof api==='function'").asBoolean)
      js("open('')");until("Socket fixture connected")(js("agentEpoch!==null").asBoolean&&local("!!window.executionFixture").asBoolean)
      local("document.cookie='socketAuth=approved; Path=/';true")
      val url="ws://127.0.0.1:"+echo.getPort+"/echo"
      local(s"""(()=>{
        const state=window.socketResult={events:[],messages:[],errors:0};
        const w=new WebSocket(${JsonFormatter.Compact(str(url))},['echo-test']);w.binaryType='arraybuffer';
        state.initial=w.readyState;state.url=w.url;state.instance=w instanceof WebSocket;
        try{w.send('premature');}catch(e){state.early=e.name;}
        w.addEventListener('open',()=>state.events.push('listener-open'));
        w.onopen=()=>{state.events.push('open');state.protocol=w.protocol;w.send('hello');w.send(new Uint8Array([0,1,255]));w.send(new Blob([new Uint8Array([2,3])]));};
        w.onmessage=e=>{state.messages.push(typeof e.data==='string'?e.data:[...new Uint8Array(e.data)]);if(state.messages.length===3)w.close(3001,'finished');};
        w.onerror=()=>state.errors++;
        w.onclose=e=>{state.close={code:e.code,reason:e.reason,clean:e.wasClean};state.final=w.readyState;state.buffered=w.bufferedAmount;};return true;
      })()""")
      until("Text, binary and Blob frames round-trip and close")(local("!!socketResult.close").asBoolean)
      val state=local("socketResult")
      check("Browser WebSocket states and early-send exception match")(state("initial").asInt==0&&state("final").asInt==3&&state("early").asString=="InvalidStateError"&&state("instance").asBoolean)
      check("Original URL and negotiated protocol remain visible")(state("url").asString==url&&state("protocol").asString=="echo-test")
      check("Frame payloads preserve type and order")(state("messages")==arr(str("hello"),arr(num(0),num(1),num(255)),arr(num(2),num(3))))
      check("Close code, reason and send buffer are correct")(state("close")("code").asInt==3001&&state("close")("reason").asString=="finished"&&state("close")("clean").asBoolean&&state("buffered").asLong==0&&state("errors").asInt==0)
      check("Upstream receives each frame once")(received.toArray.toList==List("hello","0001ff","0203"))
      val handshake=echo.takeRequest(5,java.util.concurrent.TimeUnit.SECONDS)
      check("Upstream receives original origin and scoped cookies")(handshake.getHeader("Origin")==server.address&&Option(handshake.getHeader("Cookie")).exists(_.contains("socketAuth=approved"))&&handshake.getHeader("Authorization")==null)
      check("Handshake response cookies reach the page")(local("document.cookie").asString.contains("socketCookie=received"))
      local("window.deniedSocket=null;const denied=new WebSocket('ws://127.0.0.1:1/private');denied.onclose=e=>window.deniedSocket=e.code;true")
      until("Network policy denies a socket outside allowed destinations")(local("deniedSocket===1006").asBoolean)
      check("Socket denial is enforced at the server connection")(js("api({op:'counts',id:session.id})")("networkDenied").asLong>0)
      val socketScript=new String(getClass.getResourceAsStream("/robobrowser/mirror/execution-sockets.js").readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)
      local("window.socketRealm=document.createElement('iframe');document.body.append(socketRealm);true")
      val lifecycleScript=socketScript+"""
        window.requests=[];window.events=[];
        installExecutionSockets({origin:location.origin,base:()=>location.href,fetch:async(u,o)=>{
          const c=JSON.parse(o.body);requests.push(c);
          if(c.op==='open')return new Promise(resolve=>window.releaseOpen=()=>resolve({ok:true,json:async()=>({id:'delayed'})}));
          if(c.op==='poll')return new Promise(resolve=>window.releasePoll=(events=[{event:'error'},{event:'close',code:1006,reason:'',clean:false}])=>resolve({ok:true,json:async()=>({events})}));
          return {ok:true,json:async()=>({})};
        }});
        window.makeSocket=()=>{const w=new WebSocket('ws://example.test/socket');w.onerror=()=>events.push('error');w.onclose=()=>events.push('close');return w;};
        window.pending=makeSocket();pending.dispose();releaseOpen();
      """
      local("socketRealm.contentWindow.eval("+JsonFormatter.Compact(str(lifecycleScript))+");true")
      until("Disposal before open acknowledgement releases the newly allocated server channel")(local("socketRealm.contentWindow.requests.filter(r=>r.op==='dispose').length===1").asBoolean)
      check("Disposed connecting socket never starts polling")(local("!socketRealm.contentWindow.requests.some(r=>r.op==='poll')").asBoolean)
      local("socketRealm.contentWindow.eval('requests.length=0;events.length=0;window.pending=makeSocket();releaseOpen();');true")
      until("Second socket has a pending relay poll")(local("socketRealm.contentWindow.requests.some(r=>r.op==='poll')").asBoolean)
      local("socketRealm.contentWindow.eval('pending.dispose();releasePoll();');true")
      // Drain the response continuation before inspecting events.
      local("new Promise(resolve=>setTimeout(()=>resolve(true),50))")
      check("Late poll failure cannot emit events after disposal")(local("JSON.stringify(socketRealm.contentWindow.events)==='[\"close\"]'").asBoolean)
      local("socketRealm.contentWindow.eval('requests.length=0;events.length=0;window.pending=makeSocket();pending.onopen=()=>pending.dispose();releaseOpen();');true")
      until("Callback-disposal socket has a pending relay poll")(local("socketRealm.contentWindow.requests.some(r=>r.op==='poll')").asBoolean)
      local("socketRealm.contentWindow.eval(\"releasePoll([{event:'open',protocol:'',extensions:''},{event:'error'}]);\");true")
      local("new Promise(resolve=>setTimeout(()=>resolve(true),50))")
      check("Disposal inside an event handler stops the remainder of its poll batch")(local("JSON.stringify(socketRealm.contentWindow.events)==='[\"close\"]'").asBoolean)
      local("socketRealm.remove();true")
      js("close()")
      Files.writeString(out.resolve("result.json"),JsonFormatter.Default(obj("status"->str("passed"),"checks"->arr(checks.toList.map(str)*),"state"->state)))
    }catch{case e:Throwable=>Files.writeString(out.resolve("result.json"),JsonFormatter.Default(obj("status"->str("failed"),"checks"->arr(checks.toList.map(str)*),"error"->str(e.toString))));throw e}
    finally{server.stop();front.stop().sync();host.stop().sync();echo.shutdown()}
  }
}
