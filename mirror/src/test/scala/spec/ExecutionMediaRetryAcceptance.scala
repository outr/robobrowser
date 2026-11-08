package spec

import fabric.*
import fabric.io.JsonFormatter
import okhttp3.mockwebserver.{Dispatcher,MockResponse,MockWebServer,RecordedRequest}
import robobrowser.{BrowserHost,BrowserNetworkPolicy}
import robobrowser.mirror.{ExecutionServer}
import java.nio.file.{Files,Path}

/** Retry a transient media recovery failure on the same video element. */
object ExecutionMediaRetryAcceptance {
  def main(args:Array[String]):Unit={
    val out=Path.of("target/execution-media-retry");Files.createDirectories(out)
    val movie=out.resolve("hevc.mp4")
    val encoder=new ProcessBuilder("ffmpeg","-y","-nostdin","-v","error","-filter_threads","1","-f","lavfi","-i","testsrc2=size=320x180:rate=10","-t","2","-c:v","libx265","-pix_fmt","yuv420p10le","-x265-params","pools=1:frame-threads=1","-an",movie.toString).redirectErrorStream(true).redirectOutput(out.resolve("encode.log").toFile).start()
    require(encoder.waitFor(30,java.util.concurrent.TimeUnit.SECONDS)&&encoder.exitValue()==0,"HEVC fixture encoding failed")
    val bytes=Files.readAllBytes(movie);val firstReads=new java.util.concurrent.atomic.AtomicInteger()
    val source=new MockWebServer()
    source.setDispatcher(new Dispatcher {
      override def dispatch(request:RecordedRequest):MockResponse={
        if(firstReads.incrementAndGet()==2)new MockResponse().setResponseCode(503).setBody("temporary failure")
        else new MockResponse().setHeader("Content-Type","video/mp4").setHeader("Access-Control-Allow-Origin","*").setBody(new okio.Buffer().write(bytes))
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
      until("First recovery reports its upstream failure")(js("local('state')")("metrics")("media").asVector.exists(m=>m("status").asString=="failed"))
      local("switchedVideo.load();true")
      until("Reload retries the same failed video source")(local("switchedVideo.readyState>=2&&!switchedVideo.error").asBoolean)
      local("switchedVideo.play().then(()=>true)")
      val before=local("switchedVideo.getVideoPlaybackQuality().totalVideoFrames").asLong
      Thread.sleep(1000)
      until("Recovered retry produces native video frames")(local("switchedVideo.getVideoPlaybackQuality().totalVideoFrames").asLong>before)
      until("Latest recovery is successful")(js("local('state')")("metrics")("media").asVector.last("status").asString=="recovered")
      report("passed");js("close()")
    }catch{case e:Throwable=>report("failed",Some(e.toString));throw e}
    finally{server.stop();front.stop().sync();host.stop().sync();source.shutdown()}
  }
}
