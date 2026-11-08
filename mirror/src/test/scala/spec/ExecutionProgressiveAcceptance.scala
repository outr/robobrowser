package spec

import fabric.*
import fabric.io.JsonFormatter
import okhttp3.mockwebserver.{Dispatcher,MockResponse,MockWebServer,RecordedRequest}
import robobrowser.{BrowserHost,BrowserNetworkPolicy}
import robobrowser.mirror.{ExecutionServer}
import java.nio.file.{Files,Path}

/** Real codec/HTTP/MediaSource path: early frames, seeking, cancellation and CORS fallback. */
object ExecutionProgressiveAcceptance {
  def main(args:Array[String]):Unit={
    val out=Path.of("target/execution-progressive");Files.createDirectories(out)
    val movie=out.resolve("hevc.mp4")
    val encoder=new ProcessBuilder("ffmpeg","-y","-nostdin","-v","error","-filter_threads","1","-f","lavfi","-i","testsrc2=size=1280x720:rate=30","-f","lavfi","-i","sine=frequency=440:sample_rate=48000","-t","12","-c:v","libx265","-preset","ultrafast","-x265-params","pools=2:frame-threads=2","-c:a","aac","-b:a","96k",movie.toString).redirectErrorStream(true).redirectOutput(out.resolve("encode.log").toFile).start()
    require(encoder.waitFor(90,java.util.concurrent.TimeUnit.SECONDS)&&encoder.exitValue()==0,"HEVC fixture encoding failed")
    val audioFile=out.resolve("audio-only.mp4")
    val extractor=new ProcessBuilder("ffmpeg","-y","-nostdin","-v","error","-i",movie.toString,"-vn","-t","2","-c:a","copy",audioFile.toString).redirectErrorStream(true).redirectOutput(out.resolve("audio.log").toFile).start()
    require(extractor.waitFor(15,java.util.concurrent.TimeUnit.SECONDS)&&extractor.exitValue()==0,"Audio fixture failed")
    val audioBytes=Files.readAllBytes(audioFile)
    // A valid, playable MP4 whose padding exceeds the optional conversion input budget.
    val padding=java.nio.ByteBuffer.allocate(17*1024*1024).putInt(17*1024*1024).put("free".getBytes(java.nio.charset.StandardCharsets.US_ASCII)).array()
    val largeAudio=audioBytes++padding
    val audioReads=new java.util.concurrent.atomic.AtomicInteger()
    val bytes=Files.readAllBytes(movie);require(bytes.length<16*1024*1024,"Fixture exceeds media budget")
    val source=new MockWebServer();val media=new MockWebServer()
    val cookieSeen=new java.util.concurrent.atomic.AtomicBoolean()
    source.setDispatcher(new Dispatcher {override def dispatch(r:RecordedRequest):MockResponse=new MockResponse().setHeader("Content-Type","text/html").setBody("<!doctype html><title>Progressive acceptance</title><body><h1>Progressive video</h1></body>")})
    media.setDispatcher(new Dispatcher {override def dispatch(r:RecordedRequest):MockResponse={
      if(Option(r.getPath).contains("/invalid.mp4"))return new MockResponse().setHeader("Content-Type","video/mp4").setBody("invalid media fixture")
      if(Option(r.getPath).exists(_.startsWith("/cookie"))&&Option(r.getHeader("Cookie")).exists(_.contains("privateMedia=1")))cookieSeen.set(true)
      val audio=Option(r.getPath).exists(_.startsWith("/audio-only"));if(audio)audioReads.incrementAndGet()
      val response=new MockResponse().setHeader("Content-Type","video/mp4").setBody(new okio.Buffer().write(if(Option(r.getPath).exists(_.startsWith("/audio-only-large")))largeAudio else if(audio)audioBytes else bytes))
      if(!Option(r.getPath).exists(_.startsWith("/no-cors")))response.setHeader("Access-Control-Allow-Origin","*")else response
    }})
    source.start(java.net.InetAddress.getByName("127.0.0.1"),0);media.start(java.net.InetAddress.getByName("127.0.0.1"),0)
    val host=BrowserHost.start().sync();val front=BrowserHost.start().sync()
    val server=new ExecutionServer(0,networkPolicy=BrowserNetworkPolicy(allowedHosts=List("127.0.0.1"),allowedPorts=List(source.getPort,media.getPort),allowPrivateNetworks=true))
    val checks=scala.collection.mutable.ListBuffer.empty[String];var diagnostic:()=>Json=()=>Null
    def q(s:String)=JsonFormatter.Compact(str(s))
    def check(name:String)(test: => Boolean):Unit={require(test,name);checks+=name;println("PASS "+name)}
    def until(name:String)(test: => Boolean):Unit={val end=System.nanoTime()+45000000000L;var ok=false;while(!ok&&System.nanoTime()<end){ok=scala.util.Try(test).getOrElse(false);if(!ok)Thread.sleep(50)};check(name)(ok)}
    def report(status:String,error:Option[String]=None):Unit=Files.writeString(out.resolve("result.json"),JsonFormatter.Default(obj("status"->str(status),"checks"->arr(checks.toList.map(str)*),"state"->scala.util.Try(diagnostic()).getOrElse(Null),"error"->error.map(str).getOrElse(Null))))
    try{
      server.start();val page=front.open(server.address+"/execution#"+server.token).sync()
      def js(code:String)=page.evaluate(code).sync()
      def local(code:String)=js("local('eval',{code:"+q(code)+"})")
      diagnostic=()=>js("local('state')")
      until("Progressive shell ready")(js("typeof api==='function'").asBoolean)
      js("open("+q("http://127.0.0.1:"+source.getPort+"/")+")");until("Progressive document attached")(js("agentEpoch!==null").asBoolean)
      val base="http://127.0.0.1:"+media.getPort
      local("window.audioOnly=document.createElement('video');audioOnly.muted=true;audioOnly.autoplay=true;document.body.append(audioOnly);audioOnly.src="+q(base+"/audio-only.mp4")+";true")
      until("Legitimate audio-only video is inspected without a playback error")(audioReads.get()>=2&&local("audioOnly.currentTime>0.1&&!audioOnly.error&&audioOnly.videoWidth===0").asBoolean&&diagnostic()("metrics")("media").asVector.isEmpty)
      check("Audio-only probe leaves application source unchanged")(local("audioOnly.currentSrc").asString==base+"/audio-only.mp4")
      local("window.audioErrors=0;audioOnly.addEventListener('error',()=>audioErrors++);audioOnly.loop=true;audioOnly.src="+q(base+"/audio-only-large.mp4")+";true")
      until("Oversized audio inspection reaches a terminal diagnostic")(diagnostic()("metrics")("media").asVector.exists(m=>Set("failed","inspection-unavailable").contains(m("status").asString)))
      check("Optional inspection failure does not send a false playback error")(local("audioErrors===0&&!audioOnly.error").asBoolean&&diagnostic()("metrics")("errors").asVector.isEmpty)
      until("Oversized audio continues native playback")(local("!audioOnly.paused&&audioOnly.currentTime>0.1").asBoolean)
      local("audioOnly.remove();true")
      local("window.revoked=[];const revoke=URL.revokeObjectURL.bind(URL);URL.revokeObjectURL=u=>{revoked.push(u);return revoke(u);};window.v=document.createElement('video');v.muted=true;v.autoplay=true;v.loop=true;v.width=640;document.body.append(v);window.firstDuration=null;v.addEventListener('playing',()=>{if(firstDuration===null)firstDuration=v.duration;});v.src="+q(base+"/public.mp4")+";true")
      until("Native frames arrive while progressive recovery is still in progress")(local("v.getVideoPlaybackQuality().totalVideoFrames>3").asBoolean&&diagnostic()("metrics")("media").asVector.exists(m=>m("status").asString=="streaming"))
      until("Progressive recovery completes without changing the video element")(diagnostic()("metrics")("media").asVector.last("status").asString=="recovered")
      val first=diagnostic()("metrics")("media").asVector.last
      check("First frame precedes completed conversion")(first("firstFrameMs").asDouble<first("fetchMs").asDouble+first("conversionMs").asDouble)
      check("Original source URL stays visible to application code")(local("v.currentSrc").asString==base+"/public.mp4")
      check("Duration is stable from initial playback")(local("Math.abs(firstDuration-12)<0.2&&Math.abs(v.duration-12)<0.2").asBoolean)
      local("v.currentTime=8;v.play();true")
      until("Native seeking advances from the requested position")(local("!v.seeking&&v.currentTime>8.1&&v.currentTime<11").asBoolean)
      local("v.currentTime=11.7;v.play();true")
      until("Native loop restarts after the completed timeline")(local("!v.paused&&v.currentTime<2").asBoolean)
      local("window.other=document.createElement('video');other.muted=true;other.autoplay=true;other.width=640;document.body.append(other);other.src="+q(base+"/cancel.mp4")+";true")
      until("Second conversion starts progressive playback")(local("other.getVideoPlaybackQuality().totalVideoFrames>0").asBoolean&&diagnostic()("metrics")("media").asVector.last("status").asString=="streaming")
      local("other.remove();true")
      until("Removed progressive video revokes its object URL")(local("revoked.length>=1").asBoolean)
      local("v.src="+q(base+"/no-cors.mp4")+";true")
      until("Cross-origin media without CORS retains completed-file playback")(diagnostic()("metrics")("media").asVector.last.get("path").exists(_.asString=="completed-file")&&local("v.readyState>=2&&!v.error").asBoolean)
      check("Cross-origin fallback remains canvas-tainted")(local("(()=>{const c=document.createElement('canvas');c.getContext('2d').drawImage(v,0,0);try{c.toDataURL();return false}catch(e){return e.name==='SecurityError'}})()").asBoolean)
      local("document.cookie='privateMedia=1; Path=/';v.src="+q(base+"/cookie.mp4")+";true")
      until("Credentialed cross-origin media uses completed-file playback")(diagnostic()("metrics")("media").asVector.last.get("path").exists(_.asString=="completed-file")&&local("v.readyState>=2&&!v.error").asBoolean)
      check("Credentialed recovery reaches the upstream without granting pixel access")(cookieSeen.get()&&local("(()=>{const c=document.createElement('canvas');c.getContext('2d').drawImage(v,0,0);try{c.toDataURL();return false}catch(e){return e.name==='SecurityError'}})()").asBoolean)
      val privateOutput=diagnostic()("metrics")("media").asVector.last("outputURL").asString
      check("Converted credentialed media cannot be fetched through wildcard CORS")(local("(async()=>{const f=document.createElement('iframe');document.body.append(f);try{await f.contentWindow.fetch("+q(privateOutput)+",{credentials:'omit'});return false}catch(e){return e.name==='TypeError'}finally{f.remove()}})()").asBoolean)
      check("Progressive resources retire on removal and source replacement")(local("revoked.length>=2").asBoolean)
      local("v.src="+q(base+"/later.mp4")+";true")
      until("A fifth distinct clip recovers in the same browsing session"){
        val last=diagnostic()("metrics")("media").asVector.last
        last("url").asString.endsWith("/later.mp4")&&last("status").asString=="recovered"&&local("v.videoWidth>0&&!v.error").asBoolean
      }
      check("No unexpected page or viewer errors")(diagnostic()("metrics")("errors").asVector.isEmpty&&js("$('error').textContent").asString.isEmpty)
      local("window.invalidErrors=0;v.addEventListener('error',()=>invalidErrors++);v.src="+q(base+"/invalid.mp4")+";true")
      until("Real unsupported playback failure remains visible to the application")(local("invalidErrors===1&&v.error?.code===4").asBoolean&&diagnostic()("metrics")("media").asVector.last("status").asString=="failed")
      check("Failed required recovery retains its error diagnostic")(diagnostic()("metrics")("errors").asVector.nonEmpty)
      report("passed");js("close()")
    }catch{case e:Throwable=>report("failed",Some(e.toString));throw e}
    finally{server.stop();front.stop().sync();host.stop().sync();source.shutdown();media.shutdown()}
  }
}
