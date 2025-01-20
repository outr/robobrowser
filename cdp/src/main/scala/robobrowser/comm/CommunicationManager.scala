package robobrowser.comm

import fabric.Obj
import fabric.filter.RemoveNullsFilter
import fabric.io.{JsonFormatter, JsonParser}
import fabric.rw.{Asable, Convertible}
import rapid.Task
import rapid.task.CompletableTask
import robobrowser.event.EventManager
import spice.UserException
import spice.http.WebSocket

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import scala.annotation.tailrec
import scala.jdk.CollectionConverters.CollectionHasAsScala

trait CommunicationManager extends EventManager {
  protected def ws: WebSocket

  private[robobrowser] var targetId: String = _
  private[robobrowser] var sessionId: String = _

  private val idGenerator = new AtomicInteger(0)
  private val callbacks = new ConcurrentHashMap[Int, CompletableTask[WSResponse]]

  ws.receive.text.attach { s =>
    if (debug) scribe.info(s"Received: $s")
    try {
      val json = JsonParser(s)
      val response = json.as[WSResponse]
      fire(response)
    } catch {
      case t: Throwable => scribe.error(s"Error receiving: $s", t)
    }
  }

  override def fire(response: WSResponse): Unit = response.id match {
    case Some(id) => retrieve(id, response)
    case None => super.fire(response)
  }

  def send(method: String,
           params: Obj = Obj.empty,
           errorThrowsException: Boolean = true): Task[WSResponse] = Task {
    val id = idGenerator.incrementAndGet()
    val request = WSRequest(
      id = id,
      method = method,
      params = params,
      sessionId = Option(sessionId)
    )
    val callback = Task.completable[WSResponse]
    scribe.debug(s"$method waiting for callback: $id")
    callbacks.put(id, callback)

    val json = request.json.filterOne(RemoveNullsFilter)
    val jsonString = JsonFormatter.Compact(json)
    if (debug) scribe.info(s"Sending: $jsonString}")
    ws.send.text := jsonString
    callback.map { response =>
      response.error match {
        case Some(error) if errorThrowsException => throw UserException(s"Method: $method, Error: ${error.message}")
        case _ => response
      }
    }
  }.flatten

  @tailrec
  private def retrieve(id: Int,
                       response: WSResponse,
                       tries: Int = 0): Unit = Option(callbacks.remove(id)) match {
//    case Some(callback) if response. => callback.success(response)
    case Some(callback) => callback.success(response)
    case None => if (tries > 2) {
      scribe.warn(s"No callback found for $id - $response (callbacks: ${callbacks.keySet().asScala.mkString(", ")})")
    } else {
      Thread.sleep(250)
      retrieve(id, response, tries + 1)
    }
  }
}
