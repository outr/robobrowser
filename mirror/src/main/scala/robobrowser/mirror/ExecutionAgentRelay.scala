package robobrowser.mirror

import fabric.*
import fabric.io.JsonFormatter
import java.util.concurrent.{CompletableFuture,TimeUnit,TimeoutException}
import scala.collection.mutable

/** One attached executing document, serialized delivery, bounded retry history. No replay after detach. */
private[mirror] final class ExecutionAgentRelay(initialEpoch:Long=0L) {
  private case class Job(id:String,signature:String,command:Json,result:CompletableFuture[Json],var delivered:Boolean=false)
  private val history=mutable.LinkedHashMap.empty[String,Job]
  private val seen=mutable.HashSet.empty[String]
  private val queue=mutable.Queue.empty[Job]
  private var epoch=initialEpoch
  private var uncertain=false
  private var aliveUntil=0L
  private var inFlight:Option[Job]=None
  private def failJobs(reason:String):Unit={
    history.values.filterNot(_.result.isDone).foreach(j=>j.result.completeExceptionally(new ExecutionException("detached",reason,j.delivered)))
    queue.clear();inFlight=None;notifyAll()
  }
  def attach():Json=synchronized {failJobs("Executing document changed; dispatched effects may already have occurred");epoch+=1;uncertain=false;aliveUntil=System.currentTimeMillis()+5000;obj("epoch"->num(epoch))}
  def detach(expected:Long):Unit=synchronized {if(epoch==expected){aliveUntil=0;failJobs("Executing viewer disconnected")}}
  def close():Unit=synchronized {aliveUntil=0;epoch+=1;failJobs("Execution session closed");history.clear();seen.clear()}
  def withDocument[A](expected:Long)(f: => A):A=synchronized {
    require(expected==epoch&&System.currentTimeMillis()<aliveUntil,"Stale executing document")
    f
  }
  def state:Json=synchronized {obj("epoch"->num(epoch),"connected"->bool(System.currentTimeMillis()<aliveUntil),"uncertain"->bool(uncertain))}
  def exchange(expected:Long,ack:Option[Json]):Json=synchronized {
    require(expected==epoch&&aliveUntil>0,"Executing document changed")
    aliveUntil=System.currentTimeMillis()+5000
    ack.foreach {a=>
      val job=inFlight.filter(_.id==a("requestId").asString).getOrElse(throw new IllegalArgumentException("Unexpected command acknowledgement"))
      require(JsonFormatter.Compact(a).length<=131072,"Agent result exceeds 128 KiB")
      if(a("ok").asBoolean)job.result.complete(a.get("result").getOrElse(Null))
      else job.result.completeExceptionally(new ExecutionException("execution_failed",a("error").asString.take(500),true))
      inFlight=None
      if(a.get("unknown").exists(_.asBoolean)){
        uncertain=true
        queue.foreach(j=>j.result.completeExceptionally(new ExecutionException("uncertain","Prior operation outcome is unknown; reload before further mutations")))
        queue.clear()
      }
    }
    if(inFlight.isEmpty&&queue.isEmpty)wait(1000)
    require(expected==epoch&&aliveUntil>0,"Executing document changed")
    aliveUntil=System.currentTimeMillis()+5000
    if(inFlight.nonEmpty)obj() // A lost exchange response is not silently delivered a second time.
    else {
      while(queue.headOption.exists(_.result.isDone))queue.dequeue()
      if(queue.isEmpty)obj()
      else {val job=queue.dequeue();job.delivered=true;inFlight=Some(job);obj("requestId"->str(job.id),"command"->job.command,"epoch"->num(epoch))}
    }
  }
  private def readOnly(command:Json,depth:Int=0):Boolean={
    depth<=8&&command.get("op").exists {op=>
      Set("read","state","windows","frames")(op.asString)||
        (Set("windowCommand","frameCommand")(op.asString)&&command.get("command").exists(c=>readOnly(c,depth+1)))
    }
  }
  def call(expected:Long,id:String,command:Json):Json={
    require(id.nonEmpty&&id.length<=128,"Agent request ID required")
    require(Set("state","read","click","type","scroll","eval","files","windows","windowCommand","frames","frameCommand")(command("op").asString),"Unsupported local agent operation")
    val fileCommand=command("op").asString=="files"||(Set("windowCommand","frameCommand")(command("op").asString)&&command.get("command").exists(_.get("op").contains(str("files"))))
    val commandLimit=if(fileCommand)768*1024 else 65536
    require(JsonFormatter.Compact(command).length<=commandLimit,"Agent command exceeds its payload limit")
    val job=synchronized {
      require(expected==epoch,"Stale executing document")
      val signature=s"$epoch:"+JsonFormatter.Compact(command)
      history.get(id) match {
        case Some(j)=>require(j.signature==signature,"Request ID reused with a different command or document");j
        case None=>
          if(seen(id))throw new ExecutionException("expired_request","Result is no longer retained; the operation will not be replayed")
          require(seen.size<8192,"Agent request budget exhausted; open a new session")
          if(uncertain&& !readOnly(command))throw new ExecutionException("uncertain","Prior operation outcome is unknown; inspect state or reload before further mutations")
          if(System.currentTimeMillis()>=aliveUntil)throw new ExecutionException("disconnected","No executing viewer is connected")
          require(queue.size<16,"Agent queue is full")
          while(history.size>=128&&history.head._2.result.isDone)history.remove(history.head._1)
          require(history.size<128,"Agent retry history is full")
          val j=Job(id,signature,command,new CompletableFuture[Json]());seen.add(id);history.put(id,j);queue.enqueue(j);notifyAll();j
      }
    }
    try job.result.get(10,TimeUnit.SECONDS)
    catch {
      case _:TimeoutException=>synchronized {
        val e=new ExecutionException("timeout","Agent operation timed out; inspect state before issuing a new action",job.delivered)
        job.result.completeExceptionally(e)
        // A dispatched operation may still be executing. Block further delivery until its
        // acknowledgement or a new document, instead of allowing unordered effects.
        throw e
      }
      case e:java.util.concurrent.ExecutionException=>throw e.getCause
    }
  }
}
