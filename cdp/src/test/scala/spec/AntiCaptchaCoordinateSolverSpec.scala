package spec
import org.scalatest.funsuite.AnyFunSuite
import robobrowser.AntiCaptchaCoordinateSolver
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import scala.concurrent.duration.*

class AntiCaptchaCoordinateSolverSpec extends AnyFunSuite {
  private def withProvider(response:String)(f:String=>Unit):Unit={
    val server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0)
    server.createContext("/",e=>{e.getRequestBody.readAllBytes();val bytes=response.getBytes("UTF-8");e.getResponseHeaders.set("Content-Type","application/json");e.sendResponseHeaders(200,bytes.length);e.getResponseBody.write(bytes);e.close()})
    server.start();try f(s"http://127.0.0.1:${server.getAddress.getPort}")finally server.stop(0)
  }
  test("provider errors cannot echo credentials or response payload into caller errors") {
    withProvider("""{"errorId":1,"errorDescription":"private-key-do-not-echo","clientKey":"private-key-do-not-echo"}"""){url=>
      val failure=intercept[IllegalStateException]{new AntiCaptchaCoordinateSolver("private-key-do-not-echo",url,1.millis,1.second).classifyCoordinates("image","instruction").sync()}
      assert(failure.getMessage=="Anti-Captcha image classification failed")
      assert(failure.getCause==null)
    }
  }
  test("valid coordinates are decoded without exposing request details") {
    withProvider("""{"errorId":0,"taskId":1,"status":"ready","solution":{"coordinates":[[12,34],[56,78]]}}"""){url=>
      assert(new AntiCaptchaCoordinateSolver("private-key",url,1.millis,1.second).classifyCoordinates("image","instruction").sync()==List((12.0,34.0),(56.0,78.0)))
    }
  }
  test("a bounded provider wait reports a sanitized timeout code") {
    withProvider("""{"errorId":0,"taskId":1,"status":"processing"}"""){url=>
      val failure=intercept[IllegalStateException]{new AntiCaptchaCoordinateSolver("private-key",url,1.millis,1.millis).classifyCoordinates("image","instruction").sync()}
      assert(failure.getMessage=="Anti-Captcha image classification failed (TIMEOUT)")
    }
  }
  test("provider error codes survive without provider descriptions") {
    withProvider("""{"errorId":1,"errorCode":"ERROR_NO_SLOT_AVAILABLE","errorDescription":"private-key-do-not-echo"}"""){url=>
      val failure=intercept[IllegalStateException]{new AntiCaptchaCoordinateSolver("private-key",url,1.millis,1.second).classifyCoordinates("image","instruction").sync()}
      assert(failure.getMessage=="Anti-Captcha image classification failed (ERROR_NO_SLOT_AVAILABLE)")
    }
  }

  test("provider task identifiers retain all 64 bits when polling") {
    val id=4294967301L
    val observed=new java.util.concurrent.atomic.AtomicLong()
    val server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0)
    server.createContext("/",e=>{
      val request=fabric.io.JsonParser(new String(e.getRequestBody.readAllBytes(),"UTF-8"))
      if(e.getRequestURI.getPath=="/getTaskResult")observed.set(request("taskId").asLong)
      val bytes=s"""{"errorId":0,"taskId":$id,"status":"ready","solution":{"coordinates":[[12,34]]}}""".getBytes("UTF-8")
      e.getResponseHeaders.set("Content-Type","application/json");e.sendResponseHeaders(200,bytes.length);e.getResponseBody.write(bytes);e.close()
    });server.start()
    try {
      new AntiCaptchaCoordinateSolver("private-key",s"http://127.0.0.1:${server.getAddress.getPort}",1.millis,1.second).classifyCoordinates("image","instruction").sync()
      assert(observed.get()==id)
    }finally server.stop(0)
  }

}
