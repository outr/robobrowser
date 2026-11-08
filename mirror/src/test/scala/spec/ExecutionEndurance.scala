package spec

import fabric.*
import fabric.io.JsonFormatter
import robobrowser.BrowserHost
import robobrowser.mirror.{ExecutionServer}
import java.nio.file.{Files,Path}

/** Bounded repeat-navigation run. Timings are event-to-next-rAF, not physical display latency. */
object ExecutionEndurance {
  def main(args:Array[String]):Unit={
    val seconds=args.headOption.map(_.toInt).getOrElse(600);require(seconds>=10&&seconds<=3600)
    val public=args.contains("public")
    val memory=new ProcessMemorySampler
    val host=BrowserHost.start().sync();val front=BrowserHost.start().sync()
    val root=Files.createTempDirectory("rb-execution-endurance-")
    val server=new ExecutionServer(0,profileDirectory=Some(root))
    val out=Path.of("target/execution-endurance");Files.createDirectories(out)
    val rounds=scala.collection.mutable.ListBuffer.empty[Json];val samples=scala.collection.mutable.ListBuffer.empty[Double]
    val navigations=scala.collection.mutable.ListBuffer.empty[Json]
    var captureFailure:()=>Unit=()=>()
    var baseline=0L;var maximum=0L;var duration=0.0
    def q(value:String)=JsonFormatter.Compact(str(value))
    def until(name:String)(test: => Boolean):Unit={val end=System.nanoTime()+45000000000L;var ok=false;while(!ok&&System.nanoTime()<end){ok=scala.util.Try(test).getOrElse(false);if(!ok)Thread.sleep(100)};require(ok,name)}
    def report(status:String,error:Option[String]=None):Unit={
      val sorted=samples.sorted
      val result=obj("status"->str(status),"seconds"->num(duration),"rounds"->arr(rounds.toList*),"navigations"->arr(navigations.toList*),"sampleCount"->num(samples.size),
        "pointerToNextAnimationFrameP95Ms"->(if(sorted.nonEmpty)num(sorted((sorted.size*0.95).toInt.min(sorted.size-1)))else Null),
        "processMemory"->memory.summary,"parentHeapBaseline"->num(baseline),"parentHeapMaximum"->num(maximum),"error"->error.map(str).getOrElse(Null),
        "scope"->str("Mounted local executor; synthetic fixture authentication and public pages; not authenticated third-party acceptance or physical display latency"))
      Files.writeString(out.resolve("result.json"),JsonFormatter.Default(result))
    }
    try {
      server.start();val page=front.open(server.address+"/execution#"+server.token).sync()
      page.send("Emulation.setDeviceMetricsOverride",obj("width"->num(1500),"height"->num(1100),"deviceScaleFactor"->num(1),"mobile"->bool(false))).sync()
      def js(code:String)=page.evaluate(code).sync()
      def local(code:String)=js("local('eval',{code:"+q(code)+"})")
      captureFailure=()=>{
        val diagnostic=js("(async()=>{const d={url:$('url').value,error:$('error').textContent,epoch:agentEpoch};try{d.state=await local('state');}catch(e){d.stateError=String(e);}try{d.videos=await local('eval',{code:\"[...document.querySelectorAll('video')].map(v=>({src:v.currentSrc,ready:v.readyState,network:v.networkState,error:v.error?.message,paused:v.paused}))\"});}catch(e){d.videoError=String(e);}return d;})()")
        Files.writeString(out.resolve("failure-diagnostic.json"),JsonFormatter.Default(diagnostic))
        Files.write(out.resolve("failure.png"),java.util.Base64.getDecoder.decode(page.send("Page.captureScreenshot",obj("format"->str("png"))).sync()("data").asString))
      }
      until("Endurance shell")(js("typeof api==='function'").asBoolean)
      js("$('profile').value='endurance';open('')")
      until("Fixture ready")(js("agentEpoch!==null").asBoolean&&local("!!window.executionFixture").asBoolean)
      val id=js("session.id");val fixture=server.address+"/fixture-execution?run="+id.asString
      local("document.cookie='endurance=retained; Path=/; Max-Age=3600';localStorage.setItem('endurance','retained');true")
      val started=System.nanoTime();var cycle=0
      while(duration<seconds){
        cycle+=1;val begin=System.nanoTime()
        if(cycle>1){js("open("+q(fixture)+")");until("Fixture navigation")(js("agentEpoch!==null").asBoolean&&local("!!window.executionFixture").asBoolean)}
        require(js("session.id")==id,"Session replaced during endurance")
        require(local("document.cookie.includes('endurance=retained')&&localStorage.getItem('endurance')==='retained'").asBoolean,"Profile state lost")
        require(js("agentCommand({op:'type',selector:'#entry',value:'cycle-"+cycle+"'})").asBoolean,"Agent edit rejected")
        require(local("executionFixture.state().text").asString=="cycle-"+cycle,"Agent and page diverged")
        local("window.frameSamples=[];document.addEventListener('pointermove',e=>requestAnimationFrame(()=>frameSamples.push(performance.now()-e.timeStamp)));true")
        val frame=js("frame.getBoundingClientRect().toJSON()")
        for(i<-0 until 40){
          page.send("Input.dispatchMouseEvent",obj("type"->str("mouseMoved"),"x"->num(frame("x").asDouble+80+i*12),"y"->num(frame("y").asDouble+280+math.sin(i.toDouble/4)*30))).sync()
          Thread.sleep(16)
        }
        until("Pointer frames advance")(local("frameSamples.length>=35").asBoolean)
        samples++=local("frameSamples").asVector.map(_.asDouble)
        if(public){
          for((url,ready)<-List(
            "https://github.com/"->"document.title.includes('GitHub')&&document.querySelector('video')?.readyState>=2",
            "https://www.facebook.com/"->"document.title.includes('Facebook')&&!!document.querySelector('input[type=password]')",
            "https://www.wikipedia.org/"->"!!document.querySelector('input[type=search],input[name=search]')")){
            val navigationStarted=System.nanoTime()
            js("open("+q(url)+")");until("Public page "+url)(js("agentEpoch!==null").asBoolean&&local(ready).asBoolean)
            val readyMs=(System.nanoTime()-navigationStarted)/1e6
            Thread.sleep(3000)
            if(url.contains("github")){
              local("document.querySelector('video').scrollIntoView({block:'center'});true")
              until("GitHub video playing")(local("!document.querySelector('video').paused").asBoolean)
              val before=local("document.querySelector('video').getVideoPlaybackQuality().totalVideoFrames").asLong
              Thread.sleep(1000)
              require(local("document.querySelector('video').getVideoPlaybackQuality().totalVideoFrames").asLong-before>=15,"Video stalled")
            }
            val state=js("local('state')")
            navigations+=obj("cycle"->num(cycle),"url"->str(url),"readyMs"->num(readyMs),"media"->state("metrics")("media"))
            require(state("metrics")("errors").asVector.isEmpty,"Page errors at "+url+": "+JsonFormatter.Compact(state("metrics")("errors")))
            val viewerError=js("$('error').textContent").asString;require(viewerError.isEmpty,"Viewer error at "+url+": "+viewerError)
            require(js("agentCommand({op:'read'}).then(r=>!!r.title)").asBoolean,"Agent lost public page")
          }
        }
        page.send("HeapProfiler.collectGarbage").sync()
        val heap=page.send("Runtime.getHeapUsage").sync()("usedSize").asLong
        if(cycle==2||baseline==0)baseline=heap
        maximum=maximum.max(heap);require(heap-baseline<32L*1024*1024,"Parent retained heap grew more than 32 MiB")
        duration=(System.nanoTime()-started)/1e9
        rounds+=obj("cycle"->num(cycle),"seconds"->num(duration),"cycleMs"->num((System.nanoTime()-begin)/1e6),"parentHeap"->num(heap),"processMemory"->memory.sample())
        report("running");println(f"PASS cycle $cycle at $duration%.1f seconds; parent heap ${heap/1048576.0}%.2f MiB")
      }
      js("close()");report("passed")
    }catch{case e:Throwable=>scala.util.Try(captureFailure());report("failed",Some(e.toString));throw e}
    finally{memory.close();server.stop();front.stop().sync();host.stop().sync();val paths=Files.walk(root);try paths.sorted(java.util.Comparator.reverseOrder()).forEach(Files.deleteIfExists(_))finally paths.close()}
  }
}
