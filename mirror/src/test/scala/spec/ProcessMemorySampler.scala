package spec

import fabric.*
import java.nio.file.{Files,Path}
import java.util.concurrent.{Executors,TimeUnit}
import scala.jdk.CollectionConverters.*

/** Linux process-tree RSS, separate from renderer JS heap. RSS sums count shared pages more than once. */
final class ProcessMemorySampler extends AutoCloseable {
  private val root=ProcessHandle.current()
  private var count=0L
  private var peak=0L
  private var maxProcesses=0
  private var last:Json=Null
  private val executor=Executors.newSingleThreadScheduledExecutor(r=>{val t=new Thread(r,"acceptance-process-memory");t.setDaemon(true);t})
  def sample():Json=synchronized {
    val children=root.descendants()
    val processes=try root::children.iterator().asScala.toList finally children.close()
    var missing=0
    val readings=processes.flatMap {p=>
      scala.util.Try {
        val started=p.info().startInstant()
        val status=Files.readString(Path.of("/proc",p.pid().toString,"status"))
        val rss=status.linesIterator.find(_.startsWith("VmRSS:")).get.split("\\s+")(1).toLong*1024
        require(p.isAlive&&p.info().startInstant()==started,"Process changed while sampling")
        obj("pid"->num(p.pid()),"rssBytes"->num(rss))
      }.toOption match {case value@Some(_)=>value;case None=>missing+=1;None}
    }
    val total=readings.map(_("rssBytes").asLong).sum
    count+=1;peak=peak.max(total);maxProcesses=maxProcesses.max(readings.size)
    last=obj("rssSumBytes"->num(total),"processes"->arr(readings*),"unavailableProcesses"->num(missing))
    last
  }
  executor.scheduleAtFixedRate(()=>{scala.util.Try(sample());()},0,1,TimeUnit.SECONDS)
  def summary:Json=synchronized {obj("samples"->num(count),"peakRssSumBytes"->num(peak),"maximumProcessCount"->num(maxProcesses),"last"->last,
    "scope"->str("Acceptance JVM and its live descendants, including Chrome and codec processes; summed RSS double-counts shared pages; one-second samples can miss shorter peaks"))}
  def close():Unit={executor.shutdownNow();executor.awaitTermination(2,TimeUnit.SECONDS);()}
}
