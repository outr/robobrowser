package robobrowser.mirror

import fabric.*
import okhttp3.{OkHttpClient,Request,Response,WebSocket,WebSocketListener}
import java.util.concurrent.ConcurrentHashMap
import scala.collection.mutable
import scala.jdk.CollectionConverters.*

/** Bounded, origin-owned WebSocket relay. Delivery failures close the channel; frames are not replayed. */
private[mirror] final class ExecutionSockets(client:OkHttpClient,cookies:ExecutionCookies) extends AutoCloseable {
  private class Channel(val owner:String) {
    val queue=mutable.Queue.empty[Json]
    var bytes=0
    var socket:WebSocket=null
    var lastSeen=System.nanoTime()
    var closed=false
    var sequence=0L
    var lastSignature=""
    def add(event:Json,size:Int=0):Unit=synchronized {
      if(!closed){
        if(queue.size>=256||bytes+size>4*1024*1024){
          queue.clear();bytes=0;queue.enqueue(obj("event"->str("error")));finish(1009,"Receive buffer exceeded",false);if(socket!=null)socket.cancel()
        }else {queue.enqueue(event);bytes+=size;notifyAll()}
      }
    }
    def finish(code:Int,reason:String,clean:Boolean):Unit=synchronized {
      if(!closed){closed=true;queue.enqueue(obj("event"->str("close"),"code"->num(code),"reason"->str(reason),"clean"->bool(clean)));notifyAll()}
    }
  }
  private val channels=new ConcurrentHashMap[String,Channel]()
  private val reaper=java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r=>{val t=new Thread(r,"execution-sockets");t.setDaemon(true);t})
  reaper.scheduleAtFixedRate(()=>channels.asScala.foreach {case(id,c)=>c.synchronized {
    if(System.nanoTime()-c.lastSeen>45000000000L){channels.remove(id,c);if(c.socket!=null)c.socket.cancel();c.finish(1006,"",false)}
  }},5,5,java.util.concurrent.TimeUnit.SECONDS)
  private def channel(owner:String,c:Json):Channel={
    val found=Option(channels.get(c("id").asString)).getOrElse(throw new IllegalArgumentException("Socket expired"))
    require(found.owner==owner,"Socket belongs to a different origin");found
  }
  def command(owner:String,c:Json):Json=c("op").asString match {
    case "open"=>synchronized {
      require(channels.size()<16,"Socket budget exceeded")
      val u=java.net.URI.create(c("url").asString)
      require(Set("ws","wss")(u.getScheme)&&u.getHost!=null&&u.getUserInfo==null&&u.getRawFragment==null,"Invalid WebSocket URL")
      require(!owner.startsWith("https:")||u.getScheme=="wss","Insecure socket from a secure page")
      val protocols=c("protocols").asVector.map(_.asString)
      require(protocols.size<=32&&protocols.distinct==protocols&&protocols.forall(_.matches("[!#$%&'*+.^_`|~0-9A-Za-z-]{1,128}")),"Invalid socket protocols")
      val id=java.util.UUID.randomUUID().toString;val state=new Channel(owner);channels.put(id,state)
      val request=new Request.Builder().url(u.toString).header("Origin",owner)
      if(protocols.nonEmpty)request.header("Sec-WebSocket-Protocol",protocols.mkString(", "))
      val httpURL=u.toString.replaceFirst("^ws","http")
      val cookie=cookies.header(httpURL);if(cookie.nonEmpty)request.header("Cookie",cookie)
      try {
        state.socket=client.newWebSocket(request.build(),new WebSocketListener {
          override def onOpen(socket:WebSocket,response:Response):Unit={
            val selected=Option(response.header("Sec-WebSocket-Protocol")).getOrElse("")
            if((protocols.nonEmpty&& !protocols.contains(selected))||(protocols.isEmpty&&selected.nonEmpty)){
              state.add(obj("event"->str("error")));state.finish(1006,"",false);socket.cancel()
            }else {
              cookies.put(java.net.URI.create(httpURL),response.headers().toMultimap)
              state.add(obj("event"->str("open"),"protocol"->str(selected),"extensions"->str(Option(response.header("Sec-WebSocket-Extensions")).getOrElse(""))))
            }
          }
          override def onMessage(socket:WebSocket,value:String):Unit={
            val size=value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length
            if(size>1024*1024){state.finish(1009,"Message limit exceeded",false);socket.cancel()}
            else state.add(obj("event"->str("message"),"text"->str(value)),size)
          }
          override def onMessage(socket:WebSocket,value:okio.ByteString):Unit={
            if(value.size()>1024*1024){state.finish(1009,"Message limit exceeded",false);socket.cancel()}
            else state.add(obj("event"->str("message"),"binary"->str(value.base64())),value.size())
          }
          override def onClosing(socket:WebSocket,code:Int,reason:String):Unit={socket.close(code,reason)}
          override def onClosed(socket:WebSocket,code:Int,reason:String):Unit=state.finish(code,reason,true)
          override def onFailure(socket:WebSocket,error:Throwable,response:Response):Unit={state.add(obj("event"->str("error")));state.finish(1006,"",false)}
        })
        obj("id"->str(id))
      }catch{case e:Throwable=>channels.remove(id);throw e}
    }
    case "poll"=>
      val state=channel(owner,c)
      state.synchronized {
        state.lastSeen=System.nanoTime()
        if(state.queue.isEmpty&& !state.closed)state.wait(15000)
        val events=state.queue.dequeueAll(_=>true).toList;state.bytes=0;state.lastSeen=System.nanoTime()
        if(state.closed)channels.remove(c("id").asString,state)
        obj("events"->arr(events*))
      }
    case "send"=>
      val state=channel(owner,c)
      state.synchronized {
        require(!state.closed&&state.socket!=null,"Socket closed")
        val sequence=c("sequence").asLong;val signature=fabric.io.JsonFormatter.Compact(c)
        if(sequence!=state.sequence){
          require(sequence==state.sequence+1,"Out-of-order socket send")
          val sent=c.get("text") match {
            case Some(value)=>val text=value.asString;require(text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length<=1024*1024,"Message limit exceeded");state.socket.send(text)
            case None=>val bytes=Option(okio.ByteString.decodeBase64(c("binary").asString)).getOrElse(throw new IllegalArgumentException("Invalid binary frame"));require(bytes.size()<=1024*1024,"Message limit exceeded");state.socket.send(bytes)
          }
          require(sent,"Socket send queue full");state.sequence=sequence;state.lastSignature=signature
        }else require(signature==state.lastSignature,"Socket sequence reused with different data")
        state.lastSeen=System.nanoTime();obj()
      }
    case "dispose"=>
      val state=channel(owner,c);state.synchronized {
        channels.remove(c("id").asString,state)
        state.finish(1001,"Document closed",false)
        if(state.socket!=null)state.socket.cancel()
        obj()
      }
    case "close"=>
      val state=channel(owner,c);state.synchronized {
        val code=c.get("code").map(_.asInt).getOrElse(1000);val reason=c.get("reason").map(_.asString).getOrElse("")
        require((code==1000||code>=3000&&code<=4999)&&reason.getBytes(java.nio.charset.StandardCharsets.UTF_8).length<=123,"Invalid socket close")
        if(state.socket!=null)state.socket.close(code,reason);obj()
      }
    case _=>throw new IllegalArgumentException("Unknown socket operation")
  }
  override def close():Unit={reaper.shutdownNow();channels.values().asScala.foreach(c=>{if(c.socket!=null)c.socket.cancel();c.finish(1006,"",false)});channels.clear()}
}
