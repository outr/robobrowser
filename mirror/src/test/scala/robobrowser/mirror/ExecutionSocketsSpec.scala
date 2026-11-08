package robobrowser.mirror

import fabric.*
import okhttp3.{OkHttpClient,WebSocketListener}
import okhttp3.mockwebserver.{Dispatcher,MockResponse,MockWebServer,RecordedRequest}
import org.scalatest.funsuite.AnyFunSuite

class ExecutionSocketsSpec extends AnyFunSuite {
  test("disposed channels release capacity without a final poll and enforce origin ownership") {
    val upstream=new MockWebServer()
    upstream.setDispatcher(new Dispatcher {override def dispatch(r:RecordedRequest)=new MockResponse().withWebSocketUpgrade(new WebSocketListener {})})
    upstream.start()
    val client=new OkHttpClient()
    val owner=upstream.url("/").toString.stripSuffix("/")
    val sockets=new ExecutionSockets(client,new ExecutionCookies(()=>owner))
    def open()=sockets.command(owner,obj("op"->str("open"),"url"->str(owner.replaceFirst("http","ws")+"/socket"),"protocols"->arr()))("id").asString
    def command(id:String,op:String)=obj("op"->str(op),"id"->str(id))
    try {
      val first=open()
      intercept[IllegalArgumentException](sockets.command("http://other.example",command(first,"dispose")))
      sockets.command(owner,command(first,"dispose"))
      intercept[IllegalArgumentException](sockets.command(owner,command(first,"poll")))
      // More than the simultaneous-channel cap, without sleeping for the 45-second reaper.
      for(_<-0 until 32){val id=open();sockets.command(owner,command(id,"dispose"))}
    }finally {
      sockets.close();client.dispatcher().executorService().shutdownNow();client.connectionPool().evictAll();upstream.shutdown()
    }
  }
}
