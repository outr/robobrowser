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
import spice.http.{ConnectionStatus, WebSocket}

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}
import scala.annotation.tailrec
import scala.compiletime.uninitialized
import scala.jdk.CollectionConverters.CollectionHasAsScala

trait CommunicationManager extends EventManager {
  protected def ws: WebSocket

  private[robobrowser] var targetId: String = uninitialized
  private[robobrowser] var sessionId: String = uninitialized

  private val idGenerator = new AtomicInteger(0)
  private val callbacks = new ConcurrentHashMap[Int, Completable[WSResponse]]

  /** Set once, by the first close: a CDP request on a closed session never gets a response, so every pending `send`
    * is failed and every later one fails at once rather than waiting forever. */
  private val closedState = new AtomicReference[Option[SessionClosed]](None)

  /** The infrastructure-fatal events: the target is gone and no pending or
    * future request on this session can ever answer. */
  private val FatalMethods = Map(
    "Inspector.targetCrashed" -> SessionCloseCause.Crashed,
    "Target.targetCrashed" -> SessionCloseCause.Crashed,
    "Inspector.detached" -> SessionCloseCause.Detached
  )

  /** Why this session closed, once it has: crashed, detached, disposed, or its socket closed. */
  def closed: Option[SessionClosed] = closedState.get()

  /** What closed the session, unless its owner disposed it: the browser (or at least this tab's session) must be
    * recreated. */
  def crashed: Option[String] = closed.filter(_.cause != SessionCloseCause.Disposed).map(_.detail)

  /** Close the session for `cause`, failing every pending request. The first close wins; a later one (the socket
    * closing under a dispose) only fails what is still pending. Returns whether this call closed it. */
  protected def closeSession(cause: SessionCloseCause, detail: String): Boolean = {
    val closing = SessionClosed(cause, detail)
    val first = closedState.compareAndSet(None, Some(closing))
    if (first && cause != SessionCloseCause.Disposed) {
      scribe.error(s"CDP session closed ($cause: $detail) — failing ${callbacks.size()} pending request(s)")
    }
    if (first) sessionClosed(closing)
    failPending()
    first
  }

  /** Called once, when the session closes. */
  protected def sessionClosed(closed: SessionClosed): Unit = ()

  private def failPending(): Unit = closed.foreach { c =>
    callbacks.keySet().asScala.toList.foreach { id =>
      Option(callbacks.remove(id)).foreach(_.failure(c.exception))
    }
  }

  ws.status.attach {
    case ConnectionStatus.Closed => closeSession(SessionCloseCause.SocketClosed, "DevTools WebSocket closed"): Unit
    case _ => ()
  }
  ws.error.attach { t =>
    closeSession(SessionCloseCause.SocketClosed, s"DevTools WebSocket error: ${t.getMessage}"): Unit
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
      response.method.foreach { method =>
        FatalMethods.get(method).foreach(cause => closeSession(cause, method))
      }
      super.fire(response)
  }

  def send(method: String,
           params: Obj = Obj.empty,
           errorThrowsException: Boolean = true,
           clearNulls: Boolean = true): Task[WSResponse] = Task {
    // A request on a closed session never answers: callers see an error, not an infinite wait.
    closed.foreach(c => throw c.exception)
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
    // A close between the check above and the put has already drained the callbacks: this one is failed here.
    if (closed.nonEmpty) failPending()

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
