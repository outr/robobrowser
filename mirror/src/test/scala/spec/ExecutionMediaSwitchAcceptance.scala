package spec

import fabric.*
import fabric.io.JsonFormatter
import okhttp3.mockwebserver.{Dispatcher,MockResponse,MockWebServer,RecordedRequest}
import robobrowser.{BrowserHost,BrowserNetworkPolicy}
import robobrowser.mirror.{ExecutionServer}
import java.nio.file.{Files,Path}

/** Change the same video's source while its first codec recovery is still fetching. */
object ExecutionMediaSwitchAcceptance {
  def main(args:Array[String]):Unit={
    val out=Path.of("target/execution-media-switch");Files.createDirectories(out)
    val movie=out.resolve("hevc.mp4")
    val encoder=new ProcessBuilder("ffmpeg","-y","-nostdin","-v","error","-f","lavfi","-i","testsrc2=size=320x180:rate=10","-t","2","-c:v","libx265","-x265-params","pools=1:frame-threads=1","-an",movie.toString).redirectErrorStream(true).redirectOutput(out.resolve("encode.log").toFile).start()
    require(encoder.waitFor(30,java.util.concurrent.TimeUnit.SECONDS)&&encoder.exitValue()==0,"HEVC fixture encoding failed")
    val bytes=Files.readAllBytes(movie);val firstReads=new java.util.concurrent.atomic.AtomicInteger()
    val source=new MockWebServer()
    source.setDispatcher(new Dispatcher {
      override def dispatch(request:RecordedRequest):MockResponse={
        val response=new MockResponse().setHeader("Content-Type","video/mp4").setHeader("Access-Control-Allow-Origin","*").setBody(new okio.Buffer().write(bytes))
        if(request.getPath.startsWith("/first")&&firstReads.incrementAndGet()>1)response.setBodyDelay(4,java.util.concurrent.TimeUnit.SECONDS)
        response
      }
    });source.start(java.net.InetAddress.getByName("127.0.0.1"),0)
    val host=BrowserHost.start().sync();val front=BrowserHost.start().sync()
    val server=new ExecutionServer(0,networkPolicy=BrowserNetworkPolicy(allowedHosts=List("127.0.0.1"),allowedPorts=List(source.getPort),allowPrivateNetworks=true))
    val checks=scala.collection.mutable.ListBuffer.empty[String]
    var diagnostic:()=>Json=()=>Null
    def until(name:String)(test: => Boolean):Unit={val end=System.nanoTime()+20000000000L;var ok=false;while(!ok&&System.nanoTime()<end){ok=scala.util.Try(test).getOrElse(false);if(!ok)Thread.sleep(50)};require(ok,name);checks+=name;println("PASS "+name)}
    def report(status:String,error:Option[String]=None):Unit=Files.writeString(out.resolve("result.json"),JsonFormatter.Default(obj("status"->str(status),"checks"->arr(checks.toList.map(str)*),"state"->scala.util.Try(diagnostic()).getOrElse(Null),"error"->error.map(str).getOrElse(Null))))
    try {
      server.start();val page=front.open(server.address+"/execution#"+server.token).sync()
      def js(code:String)=page.evaluate(code).sync()
      def local(code:String)=js("local('eval',{code:"+JsonFormatter.Compact(str(code))+"})")
      diagnostic=()=>js("local('state')")
      until("Media switch shell ready")(js("typeof api==='function'").asBoolean)
      js("open('')");until("Media switch fixture ready")(js("agentEpoch!==null").asBoolean&&local("!!window.executionFixture").asBoolean)
      val base="http://127.0.0.1:"+source.getPort
      local("window.switchedVideo=document.createElement('video');switchedVideo.muted=true;switchedVideo.autoplay=true;switchedVideo.loop=true;switchedVideo.src="+JsonFormatter.Compact(str(base+"/first.mp4"))+";document.body.append(switchedVideo);true")
      until("First recovery is in flight")(js("local('state')")("metrics")("media").asVector.exists(m=>m("status").asString=="converting"))
      local("switchedVideo.src="+JsonFormatter.Compact(str(base+"/second.mp4"))+";switchedVideo.load();true")
      until("Changed source recovers on the same video element")(local("switchedVideo.readyState>=2&&!switchedVideo.error").asBoolean)
      local("switchedVideo.play().then(()=>true)")
      val before=local("switchedVideo.getVideoPlaybackQuality().totalVideoFrames").asLong
      Thread.sleep(1000)
      until("Changed source presents native decoded frames")(local("switchedVideo.getVideoPlaybackQuality().totalVideoFrames").asLong>before+3)
      until("Old recovery retires without replacing the new source")(js("local('state')")("metrics")("media").asVector.exists(m=>m("status").asString=="superseded")&&local("switchedVideo.src.endsWith('/second.mp4')&&!switchedVideo.error").asBoolean)
      report("passed");js("close()")
    }catch{case e:Throwable=>report("failed",Some(e.toString));throw e}
    finally{server.stop();front.stop().sync();host.stop().sync();source.shutdown()}
  }
}
