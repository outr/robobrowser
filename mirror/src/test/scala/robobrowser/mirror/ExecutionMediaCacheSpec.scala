package robobrowser.mirror

import java.net.{CookieManager,Proxy}
import java.util.concurrent.atomic.AtomicInteger
import okhttp3.mockwebserver.{Dispatcher,MockResponse,MockWebServer,RecordedRequest}
import org.scalatest.funsuite.AnyFunSuite

class ExecutionMediaCacheSpec extends AnyFunSuite {
  private def withCache(outputSize:Int)(test:(ExecutionMedia,AtomicInteger,()=>Unit)=>Unit):Unit={
    val source=new MockWebServer()
    source.setDispatcher(new Dispatcher{override def dispatch(r:RecordedRequest)=new MockResponse().setHeader("Content-Type","video/mp4").setBody(new okio.Buffer().write(Array(r.getPath.stripPrefix("/").toInt.toByte)))})
    source.start();val conversions=new AtomicInteger();var clock=100000L
    val decoder=new ExecutionMediaDecoder {
      def convert(bytes:Array[Byte],metadata:(Double,String)=>Unit,append:Array[Byte]=>Unit):Unit={
        conversions.incrementAndGet();metadata(1,"video/mp4");append(Array.fill[Byte](outputSize)(bytes.head))
      }
      def close():Unit=()
    }
    val cache=new ExecutionMedia(url=>(source.url(new java.net.URI(url).getPath).toString,Proxy.NO_PROXY),new CookieManager(),decoder,()=>clock)
    try test(cache,conversions,()=>clock+=61000)finally{cache.close();source.shutdown()}
  }
  private def ready(cache:ExecutionMedia,index:Int):String={
    val id=cache.begin(s"http://media.example/$index")("id").asString
    val deadline=System.nanoTime()+5000000000L
    while(Set("converting","streaming")(cache.status(id)("status").asString)&&System.nanoTime()<deadline)Thread.sleep(5)
    assert(cache.status(id)("status").asString=="ready",cache.status(id).toString);id
  }
  test("more than four clips recover and an evicted resource ID reconstructs identical bytes") {
    withCache(10*1024*1024){(cache,conversions,_)=>
      val first=ready(cache,1)
      for(i<-2 to 6)ready(cache,i)
      assert(conversions.get()==6)
      val (bytes,_)=cache.bytes(first,"http://media.example")
      assert(bytes.length==10*1024*1024&&bytes.head==1&&bytes.last==1)
      assert(conversions.get()==7)
      intercept[IllegalArgumentException](cache.bytes(first,"http://other.example"))
    }
  }
  test("active stream readers pin their output while another cache entry is evicted") {
    withCache(10*1024*1024){(cache,_,_)=>
      val first=ready(cache,1);val reader=cache.open(first,"http://media.example")._1
      try{
        ready(cache,2);ready(cache,3)
        assert(reader.read()==1)
        val remainder=reader.readAllBytes();assert(remainder.length==10*1024*1024-1&&remainder.last==1)
      }finally reader.close()
    }
  }
  test("recovery admission is a rolling rate limit rather than a lifetime limit") {
    withCache(32){(cache,_,advance)=>
      for(i<-1 to 12)ready(cache,i)
      intercept[IllegalArgumentException](cache.begin("http://media.example/13"))
      assert(cache.begin("http://media.example/14",probe=true)("status").asString=="not-needed")
      advance();ready(cache,13)
    }
  }
}
