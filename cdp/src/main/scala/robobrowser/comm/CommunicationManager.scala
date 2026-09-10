package robobrowser.comm

import fabric.Obj
import fabric.filter.RemoveNullsFilter
import fabric.io.{JsonFormatter, JsonParser}
import fabric.rw.{Asable, Convertible}
import rapid.*
import rapid.task.*
import robobrowser.event.EventManager
import robobrowser.fetch.Fetch
import spice.UserException
import spice.http.WebSocket

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import scala.annotation.tailrec
import scala.compiletime.uninitialized
import scala.jdk.CollectionConverters.CollectionHasAsScala

trait CommunicationManager extends EventManager {
  protected def ws: WebSocket

  private[robobrowser] var targetId: String = uninitialized
  private[robobrowser] var sessionId: String = uninitialized

  private val idGenerator = new AtomicInteger(0)
  private val callbacks = new ConcurrentHashMap[Int, Completable[WSResponse]]

  /** Set once the target dies. A CDP request against a dead target never
    * gets a response, so without this every pending `send` waits FOREVER —
    * a production crawl once hung two hours on the request that followed an
    * `Inspector.targetCrashed` nobody was listening for. */
  @volatile private var crashedReason: Option[String] = None

  /** The infrastructure-fatal events: the target is gone and no pending or
    * future request on this session can ever answer. */
  private val FatalMethods = Set("Inspector.targetCrashed", "Target.targetCrashed", "Inspector.detached")

  /** True once the target has crashed / detached; the browser (or at least
    * this tab's session) must be recreated. */
  def crashed: Option[String] = crashedReason

  private def failEverything(reason: String): Unit = {
    crashedReason = Some(reason)
    callbacks.keySet().asScala.toList.foreach { id =>
      Option(callbacks.remove(id)).foreach(_.failure(new TargetCrashedException(reason)))
    }
  }

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

  lazy val fetch: Fetch = new Fetch(this)

  override def fire(response: WSResponse): Unit = response.id match {
    case Some(id) => retrieve(id, response)
    case None =>
      response.method.filter(FatalMethods.contains).foreach { method =>
        scribe.error(s"CDP target lost ($method) — failing ${callbacks.size()} pending request(s)")
        failEverything(method)
      }
      super.fire(response)
  }

  def send(method: String,
           params: Obj = Obj.empty,
           errorThrowsException: Boolean = true,
           clearNulls: Boolean = true): Task[WSResponse] = Task {
    // Fail fast once the target is gone — a request to a dead target never
    // answers, and callers must see an error, not an infinite wait.
    crashedReason.foreach(reason => throw new TargetCrashedException(reason))
    val id = idGenerator.incrementAndGet()
    val request = WSRequest(
      id = id,
      method = method,
      params = params.filterOne(RemoveNullsFilter),
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
