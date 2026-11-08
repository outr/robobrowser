package robobrowser.mirror

import com.sun.net.httpserver.{HttpExchange,HttpServer}
import fabric.*
import fabric.io.{JsonFormatter,JsonParser}
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets.UTF_8
import java.util.concurrent.Executors

/** Loopback development server. Applications must supply their own authenticated transport adapter. */
final class ExecutionServer(port:Int=8898,val token:String=java.util.UUID.randomUUID().toString,
    profileDirectory:Option[java.nio.file.Path]=None,
    networkPolicy:robobrowser.BrowserNetworkPolicy=robobrowser.BrowserNetworkPolicy(),
    executionOriginLimit:Int=4096,executionUploadLimit:Long=64L*1024*1024) {
  private val http=HttpServer.create(new InetSocketAddress("127.0.0.1",port),0)
  private val workers=Executors.newVirtualThreadPerTaskExecutor()
  val address:String=s"http://127.0.0.1:${http.getAddress.getPort}"
  private val execution=new ExecutionGateway(address,profileDirectory,networkPolicy,executionOriginLimit,executionUploadLimit)
  http.setExecutor(workers)
  http.createContext("/",e=>handle(e))
  def start():Unit=http.start()
  def stop():Unit={http.stop(0);execution.stop();workers.shutdownNow()}
  private def reply(e:HttpExchange,status:Int,j:Json):Unit={
    val bytes=JsonFormatter.Compact(j).getBytes(UTF_8)
    e.getResponseHeaders.set("Content-Type","application/json")
    e.getResponseHeaders.set("Cache-Control","no-store")
    e.getResponseHeaders.set("X-Content-Type-Options","nosniff")
    e.sendResponseHeaders(status,bytes.length);e.getResponseBody.write(bytes)
  }
  private def handle(e:HttpExchange):Unit=try {
    if(!execution.route(e)) {
      if(e.getRequestURI.getPath=="/execution/api") {
        require(e.getRequestMethod=="POST","POST required")
        require(Option(e.getRequestHeaders.getFirst("Host")).contains(java.net.URI.create(address).getRawAuthority),"Invalid control host")
        require(Option(e.getRequestHeaders.getFirst("Authorization")).contains("Bearer "+token),"Unauthorized")
        require(Option(e.getRequestHeaders.getFirst("Origin")).forall(_==address),"Invalid origin")
        val body=e.getRequestBody.readNBytes(1024*1024+1);require(body.length<=1024*1024,"Request too large")
        reply(e,200,obj("ok"->bool(true),"result"->execution.command(JsonParser(new String(body,UTF_8)))))
      }else reply(e,404,obj("ok"->bool(false),"error"->str("Not found")))
    }
  }catch {
    case ex:ExecutionException=>reply(e,409,obj("ok"->bool(false),"error"->str(ex.getMessage.take(500)),"code"->str(ex.code),"unknown"->bool(ex.unknown)))
    case ex:Exception=>if(e.getResponseCode<0)reply(e,400,obj("ok"->bool(false),"error"->str(Option(ex.getMessage).getOrElse("Request failed").take(500))))
  }finally e.close()
}
