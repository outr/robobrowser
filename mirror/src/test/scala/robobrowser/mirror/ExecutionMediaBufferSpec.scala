package robobrowser.mirror

import java.io.IOException
import java.util.concurrent.{CompletableFuture,TimeUnit}
import java.util.concurrent.atomic.AtomicInteger
import org.scalatest.funsuite.AnyFunSuite

class ExecutionMediaBufferSpec extends AnyFunSuite {
  test("small chunks reach an HTTP reader before more output or completion exists") {
    val b=new ExecutionMediaBuffer(_=>(),_=>())
    val server=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0)
    server.createContext("/",e=>{
      val in=b.open()
      try{e.sendResponseHeaders(200,0);ExecutionMediaBuffer.transfer(in,e.getResponseBody)}finally{in.close();e.close()}
    })
    server.start()
    val connection=java.net.URI.create("http://127.0.0.1:"+server.getAddress.getPort+"/").toURL.openConnection().asInstanceOf[java.net.HttpURLConnection]
    connection.setReadTimeout(3000)
    val reader=new java.util.concurrent.atomic.AtomicReference[java.io.InputStream]()
    try{
      val first=CompletableFuture.supplyAsync(()=>{val in=connection.getInputStream;reader.set(in);in.read()})
      b.append(Array[Byte](7));assert(first.get(2,TimeUnit.SECONDS)==7)
      b.append(Array[Byte](8));b.complete();assert(reader.get().read()==8);assert(reader.get().read()== -1)
    }finally{Option(reader.get()).foreach(_.close());connection.disconnect();b.dispose();server.stop(0)}
  }

  test("a reader receives early bytes and continues across completion compaction") {
    val stored=new AtomicInteger();val b=new ExecutionMediaBuffer(n=>{stored.addAndGet(n);()},n=>{stored.addAndGet(-n);()})
    val reader=b.open()
    val first=CompletableFuture.supplyAsync(()=>reader.read())
    b.append(Array[Byte](7,8));assert(first.get(2,TimeUnit.SECONDS)==7)
    b.append(Array[Byte](9,10));b.complete()
    assert(reader.readAllBytes().toList==List[Byte](8,9,10));assert(b.bytes.toList==List[Byte](7,8,9,10))
    reader.close();b.dispose();b.dispose();assert(stored.get()==0)
  }
  test("failed conversion wakes pending readers and does not expose a successful truncated file") {
    val b=new ExecutionMediaBuffer(_=>(),_=>());val reader=b.open()
    val pending=CompletableFuture.supplyAsync(()=>scala.util.Try(reader.read()).failed.get)
    b.fail(new IOException("decoder failed"));assert(pending.get(2,TimeUnit.SECONDS).isInstanceOf[IOException])
    intercept[IOException](b.bytes);reader.close();b.dispose()
  }
  test("reader cancellation does not stop other readers and disposal wakes them") {
    val b=new ExecutionMediaBuffer(_=>(),_=>());val one=b.open();val two=b.open()
    one.close();b.append(Array[Byte](4));assert(two.read()==4)
    val pending=CompletableFuture.supplyAsync(()=>scala.util.Try(two.read()).failed.get)
    b.dispose();assert(pending.get(2,TimeUnit.SECONDS).isInstanceOf[IOException]);two.close()
  }
  test("cache reservation rejection leaves previously published bytes intact") {
    val b=new ExecutionMediaBuffer(n=>require(n<=2,"cache full"),_=>())
    b.append(Array[Byte](1,2));intercept[IllegalArgumentException](b.append(Array[Byte](3,4,5)))
    b.complete();assert(b.bytes.toList==List[Byte](1,2));b.dispose()
  }
  test("reader count is bounded and a closed reader releases its slot") {
    val b=new ExecutionMediaBuffer(_=>(),_=>());val readers=(0 until 8).map(_=>b.open())
    intercept[IllegalArgumentException](b.open());readers.head.close();val extra=b.open()
    extra.close();readers.tail.foreach(_.close());b.dispose()
  }
}
