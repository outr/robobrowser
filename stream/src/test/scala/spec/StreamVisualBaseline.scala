package spec

import fabric.*
import fabric.rw.*
import fabric.io.JsonFormatter
import robobrowser.{BrowserConfig,RoboBrowser,RoboBrowserConfig,TabSelector}
import robobrowser.display.VirtualDisplayConfig
import robobrowser.stream.{StreamConfig,Stream}
import robobrowser.stream.Stream.stream
import java.nio.file.{Files,Path}

/** Visual baseline only; latency is the existing stream timestamp estimator, not causal input-to-paint. */
object StreamVisualBaseline {
  def main(args:Array[String]):Unit={
    val out=Path.of("target/visual-baseline");Files.createDirectories(out)
    val source=RoboBrowser(RoboBrowserConfig(browserConfig=BrowserConfig(disableGPU=true),tabSelector=TabSelector.FirstPage,virtualDisplay=Some(VirtualDisplayConfig(width=1280,height=720)))).sync()
    val server=new StreamDemoServer(source,8899,StreamConfig(encoderOverride=Some("x264enc")))
    var viewer:Option[RoboBrowser]=None;var code=0
    try {
      val html=Files.readString(Path.of("../benchmarks/visual.html"))
      source.navigate("data:text/html;base64,"+java.util.Base64.getEncoder.encodeToString(html.getBytes("UTF-8"))).sync();source.waitForLoaded().sync();server.start().sync()
      val v=RoboBrowser(RoboBrowserConfig(browserConfig=BrowserConfig(disableGPU=true),tabSelector=TabSelector.FirstPage)).sync();viewer=Some(v)
      def js(c:String):Json=v.eval(c).sync()("result")("value")
      val started=System.nanoTime();v.navigate("http://127.0.0.1:8899/").sync()
      val end=System.nanoTime()+30000000000L
      while(!js("return !!window.viewer&&window.viewer.pc.connectionState==='connected'&&window.viewer.frames>10").asBoolean&&System.nanoTime()<end)Thread.sleep(100)
      require(js("return window.viewer.pc.connectionState==='connected'").asBoolean,"Stream failed to connect")
      val startup=(System.nanoTime()-started)/1000000.0
      def bytes:Long={js("window.viewer.pc.getStats().then(stats=>{window.measureBytes=[...stats.values()].filter(s=>s.type==='inbound-rtp').reduce((n,s)=>n+(s.bytesReceived||0),0)});return true");Thread.sleep(50);js("return window.measureBytes||0").asLong}
      js("""window.visualSamples=[];const v=document.querySelector('video'),c=document.createElement('canvas');let last=-1;function frame(){if(v.videoWidth){c.width=v.videoWidth;c.height=v.videoHeight;const x=c.getContext('2d',{willReadFrequently:true});x.drawImage(v,0,0);let stamp=0;for(let i=0;i<32;i++)if(x.getImageData(i*12+6,6,1,1).data[0]>128)stamp+=2**i;const delay=((Date.now()>>>0)-stamp)>>>0;if(stamp!==last&&delay<5000){window.visualSamples.push(delay);last=stamp}}v.requestVideoFrameCallback(frame)}v.requestVideoFrameCallback(frame);return true""")
      val initial=bytes;val samples=scala.collection.mutable.ListBuffer.empty[Double]
      val first=System.nanoTime()
      for(_<-1 to 20){Thread.sleep(500);val value=js("return window.viewer.latency===null?-1:window.viewer.latency").asDouble;if(value>=0)samples+=value}
      val received=bytes-initial;val elapsed=(System.nanoTime()-first)/1000000000.0
      require(samples.nonEmpty,"No stream latency samples")
      val values=samples.sorted
      val pixel=js("return window.visualSamples").asVector.map(_.asDouble).sorted
      require(pixel.size>=20,"Insufficient causally decoded timestamp frames")
      // A causal input baseline: a source-side binary counter changes only when the real input event arrives.
      // Focus is fixture setup; characters travel through the actual viewer DataChannel and InputRouter.
      source.eval("return "+Files.readString(Path.of("../benchmarks/input.js"))).sync()
      val channelEnd=System.nanoTime()+10000000000L
      while(js("return window.viewer.channelState").asString!="open"&&System.nanoTime()<channelEnd)Thread.sleep(25)
      require(js("return window.viewer.channelState").asString=="open","Input DataChannel unavailable")
      js("""window.inputSamples=[];window.inputPending={};const iv=document.querySelector('video'),ic=document.createElement('canvas');function inputFrame(){if(iv.videoWidth){ic.width=iv.videoWidth;ic.height=iv.videoHeight;const ctx=ic.getContext('2d',{willReadFrequently:true});ctx.drawImage(iv,0,0);let count=0;for(let i=0;i<8;i++)if(ctx.getImageData(i*12+6,38,1,1).data[0]>128)count+=1<<i;if(window.inputPending[count]!==undefined){window.inputSamples.push(performance.now()-window.inputPending[count]);delete window.inputPending[count]}}iv.requestVideoFrameCallback(inputFrame)}iv.requestVideoFrameCallback(inputFrame);return true""")
      for(i<-1 to 40){
        js(s"window.inputPending[$i]=performance.now();window.viewer.dcSend({type:'char',text:'x',key:'x',keyCode:88});return true")
        val until=System.nanoTime()+5000000000L
        while(js("return window.inputSamples.length").asInt<i&&System.nanoTime()<until)Thread.sleep(10)
        require(js("return window.inputSamples.length").asInt==i,s"Stream input $i did not reach decoded pixels")
      }
      require(source.eval("return document.querySelector('#input-baseline').value.length").sync()("result")("value").asInt==40,"Stream typing source state differs")
      val inputTimes=js("return window.inputSamples").asVector.map(_.asDouble).sorted
      val stats=source.stream.sessions.head.stats.sync().json
      val result=obj("status"->str("measured"),"inputToDecodedPixelSamples"->num(inputTimes.size),"inputToDecodedPixelP50Ms"->num(inputTimes(inputTimes.size/2)),"inputToDecodedPixelP95Ms"->num(inputTimes((inputTimes.size*.95).toInt)),"sourceTimestampToDecodedFrameP50Ms"->num(pixel(pixel.size/2)),"sourceTimestampToDecodedFrameP95Ms"->num(pixel(math.min(pixel.size-1,(pixel.size*.95).toInt))),"decodedSamples"->num(pixel.size),"startupToConnectedVideoMs"->num(startup),"sampleCount"->num(values.size),"estimatedGlassToGlassP50Ms"->num(values(values.size/2)),"estimatedGlassToGlassP95Ms"->num(values(math.min(values.size-1,(values.size*.95).toInt))),"measurementSeconds"->num(elapsed),"rtpPayloadBytes"->num(received),"stats"->stats,"limitations"->str("Local Xvfb, x264 software encoding, continuously changing text. Existing estimator correlates a DataChannel timestamp with the next video frame; it is not a causal pixel measurement. RTP bytes exclude signaling and packet overhead. Input samples send individual characters through the actual DataChannel and decode a source input counter from video pixels. Focus is set up directly; this does not certify click routing or resize behavior."))
      Files.writeString(out.resolve("result.json"),JsonFormatter.Default(result));println(JsonFormatter.Compact(result))
    }catch{case e:Throwable=>code=1;e.printStackTrace()}
    finally{viewer.foreach(_.dispose().sync());server.stop().sync();source.dispose().sync()}
    System.exit(code)
  }
}
