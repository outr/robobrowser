package robobrowser.mirror

import fabric.*
import java.net.{URI,HttpURLConnection,Proxy,CookieManager}
import java.util.concurrent.{CompletableFuture,TimeUnit}
import scala.collection.mutable
import scala.jdk.CollectionConverters.*

/** Origin-owned conversion cache. Evicted resource IDs reconstruct their output on demand. */
private[mirror] final class ExecutionMedia(resolve:String=>(String,Proxy),cookies:CookieManager,
    decoder:ExecutionMediaDecoder=new ExecutionMediaCodec,now:()=>Long=()=>System.currentTimeMillis()) extends AutoCloseable {
  private class Attempt {
    val result=new CompletableFuture[Unit]()
    val output=new ExecutionMediaBuffer(_=>(),_=>())
    val started=System.nanoTime();val admitted=now()
    @volatile var cors:Option[String]=None
    @volatile var fetchMs=0.0;@volatile var conversionMs=0.0
    @volatile var phase="fetching";@volatile var skipped=false
    @volatile var credentialed=false;@volatile var duration=0.0;@volatile var mime=""
    var reserved=0L;var users=0;var evicted=false
  }
  private class Entry(val id:String,val url:String){var attempt:Attempt=null;var used=0L}
  private val entries=mutable.LinkedHashMap.empty[String,Entry]
  private val attempts=mutable.ArrayBuffer.empty[Attempt]
  private var stored=0L;private var sequence=0L
  @volatile private var closed=false
  private val clipLimit=16L*1024*1024
  private val cacheLimit=32L*1024*1024
  private def touch(e:Entry):Unit={sequence+=1;e.used=sequence}
  private def retire(a:Attempt):Unit={stored-=a.reserved;a.reserved=0;a.evicted=true;a.output.dispose()}
  private def reserve(a:Attempt):Unit=synchronized {
    require(!closed,"Media recovery closed")
    while(stored+clipLimit>cacheLimit){
      val victim=entries.values.filter(e=>e.attempt!=a&&e.attempt.result.isDone&&e.attempt.users==0&&e.attempt.reserved>0).minByOption(_.used)
        .getOrElse(throw new IllegalArgumentException("Active media cache budget exceeded"))
      retire(victim.attempt)
    }
    stored+=clipLimit;a.reserved=clipLimit
  }
  private def start(e:Entry):Attempt={
    require(!closed,"Media recovery closed")
    attempts.filterInPlace(_.admitted>now()-60000)
    require(attempts.size<12,"Media recovery rate exceeded; retry after one minute")
    require(entries.values.count(x=>x.attempt!=null&& !x.attempt.result.isDone)<4,"Concurrent media recovery budget exceeded")
    val (real,transport)=resolve(e.url)
    val a=new Attempt;e.attempt=a;attempts+=a
    Thread.ofVirtual().start(()=>{
      try {
        val c=URI.create(real).toURL.openConnection(transport).asInstanceOf[HttpURLConnection]
        c.setConnectTimeout(10000);c.setReadTimeout(15000);c.setInstanceFollowRedirects(false);c.setRequestProperty("Accept-Encoding","identity")
        cookies.get(URI.create(real),java.util.Map.of[String,java.util.List[String]]()).asScala.foreach{case(k,vs)=>vs.asScala.foreach(v=>{if(k.equalsIgnoreCase("Cookie")&&v.nonEmpty)a.credentialed=true;c.addRequestProperty(k,v)})}
        try {
          require(c.getResponseCode==200,"Media recovery requires a complete successful response")
          require(c.getContentLengthLong<=clipLimit,"Media exceeds 16 MiB")
          val upstreamCors=Option(c.getHeaderField("Access-Control-Allow-Origin"))
          // Wildcard CORS never grants script access to a credentialed response.
          a.cors=upstreamCors.filter(v=> !a.credentialed||(v!="*"&&c.getHeaderField("Access-Control-Allow-Credentials")=="true"))
          val in=c.getInputStream;val bytes=try in.readNBytes(clipLimit.toInt+1)finally in.close()
          require(bytes.length<=clipLimit,"Media exceeds 16 MiB")
          a.fetchMs=(System.nanoTime()-a.started)/1e6;a.phase="converting"
          val conversionStarted=System.nanoTime()
          decoder.convert(bytes,(duration,mime)=>{reserve(a);a.duration=duration;a.mime=mime},a.output.append)
          a.conversionMs=(System.nanoTime()-conversionStarted)/1e6
          a.output.complete()
          synchronized {
            require(!closed,"Media recovery closed")
            stored-=a.reserved-a.output.size;a.reserved=a.output.size;a.result.complete(())
          }
        }finally c.disconnect()
      }catch{
        case ExecutionMediaCodec.NotNeeded=>synchronized {a.skipped=true;attempts-=a;a.output.fail(ExecutionMediaCodec.NotNeeded);retire(a);a.result.completeExceptionally(ExecutionMediaCodec.NotNeeded)}
        case ex:Throwable=>synchronized {a.output.fail(ex);retire(a);a.result.completeExceptionally(ex)}
      }
    })
    a
  }
  private def ensure(e:Entry,retry:Boolean=false):Attempt={
    val a=e.attempt
    if(a==null||(a.evicted&& !a.result.isCompletedExceptionally)||(retry&&a.result.isCompletedExceptionally&& !a.skipped))start(e)else a
  }
  def begin(url:String,probe:Boolean=false):Json=synchronized {
    require(!closed,"Media recovery closed");resolve(url)
    attempts.filterInPlace(_.admitted>now()-60000)
    val existing=entries.values.find(_.url==url)
    // Optional missing-video inspection must not turn playable audio into a budget error.
    if(probe&&(attempts.size>=12||entries.values.count(e=>e.attempt!=null&& !e.attempt.result.isDone)>=4)&&existing.forall(e=>e.attempt==null||e.attempt.evicted))return obj("status"->str("not-needed"))
    val e=existing.getOrElse {
      if(entries.size>=128){
        val victim=entries.values.filter(e=>e.attempt!=null&&e.attempt.result.isDone&&e.attempt.users==0).minByOption(_.used)
          .getOrElse(throw new IllegalArgumentException("Media history budget exceeded"))
        retire(victim.attempt);entries.remove(victim.id)
      }
      val entry=new Entry(java.util.UUID.randomUUID().toString,url);entries.put(entry.id,entry);entry
    }
    touch(e)
    try describe(e,ensure(e,retry=true))catch{case ex:Throwable=>if(e.attempt==null)entries.remove(e.id);throw ex}
  }
  private def entry(id:String):Entry=entries.getOrElse(id,throw new IllegalArgumentException("Unknown media recovery"))
  private def describe(e:Entry,a:Attempt):Json={
    val status=if(a.skipped)"not-needed"else if(!a.result.isDone){if(a.output.size>0)"streaming"else"converting"}else if(a.result.isCompletedExceptionally)"failed"else"ready"
    val reason=if(a.result.isCompletedExceptionally&& !a.skipped)try{a.result.join();""}catch{case ex:Exception=>Option(Option(ex.getCause).getOrElse(ex).getMessage).getOrElse("Media conversion failed").take(300)}else""
    obj("id"->str(e.id),"status"->str(status),"phase"->str(if(status=="converting"||status=="streaming")a.phase else status),
      "fetchMs"->num(a.fetchMs),"conversionMs"->num(a.conversionMs),"duration"->num(a.duration),"mime"->str(a.mime),"credentialed"->bool(a.credentialed),"cors"->a.cors.map(str).getOrElse(Null),
      "streamURL"->str(URI.create(e.url).resolve("/__rb_execution/media/"+e.id+".stream").toString),
      "url"->str(URI.create(e.url).resolve("/__rb_execution/media/"+e.id+".mp4").toString),"error"->str(reason))
  }
  def status(id:String):Json=synchronized {val e=entry(id);touch(e);describe(e,e.attempt)}
  private def owned(id:String,origin:String):Entry={val e=entry(id);require(URI.create(e.url).resolve("/").toString==origin+"/","Media must remain on its original mapped origin");e}
  private def pin(id:String,origin:String):Attempt=synchronized {val e=owned(id,origin);touch(e);val a=ensure(e);a.users+=1;a}
  private def unpin(a:Attempt):Unit=synchronized {a.users-=1}
  def open(id:String,origin:String):(java.io.InputStream,Option[String])={
    val a=pin(id,origin)
    try {
      val in=a.output.open()
      val reader=new java.io.FilterInputStream(in){private var ended=false;override def close():Unit=synchronized {if(!ended){ended=true;try super.close()finally unpin(a)}}}
      (reader,a.cors)
    }catch{case ex:Throwable=>unpin(a);throw ex}
  }
  def bytes(id:String,origin:String):(Array[Byte],Option[String])={
    val a=pin(id,origin)
    try {a.result.get(75,TimeUnit.SECONDS);(a.output.bytes,a.cors)}finally unpin(a)
  }
  def cached(url:String):Option[(Array[Byte],Option[String])]=synchronized {
    entries.values.find(e=>e.url==url&&e.attempt!=null&& !e.attempt.evicted&&e.attempt.result.isDone&& !e.attempt.result.isCompletedExceptionally)
      .map(e=>{touch(e);(e.attempt.output.bytes,e.attempt.cors)})
  }
  def close():Unit=synchronized {
    closed=true;decoder.close()
    entries.values.foreach(e=>if(e.attempt!=null){val a=e.attempt;a.output.fail(new java.io.IOException("Media recovery closed"));retire(a);a.result.completeExceptionally(new java.io.IOException("Media recovery closed"))})
    entries.clear();attempts.clear()
  }
}
