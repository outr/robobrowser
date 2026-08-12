package spec

import fabric.io.{JsonFormatter, JsonParser}
import fabric.rw._
import rapid._
import robobrowser.RoboBrowser
import robobrowser.stream.Stream.stream
import robobrowser.stream.{SignalMessage, StreamConfig}
import spice.http.content.Content
import spice.http.server.MutableHttpServer
import spice.http.server.config.HttpServerListener
import spice.http.server.handler.WebSocketHandler
import spice.http.{ConnectionStatus, HttpExchange, WebSocketListener}
import spice.net.{ContentType, URL}

import scala.io.Source

/** Serves the viewer page at `/` and bridges `SignalMessage` JSON between a
  * `/signal` websocket and a per-viewer [[robobrowser.stream.StreamSession]].
  * Used by [[TestStreamDemo]] (manual) and [[StreamE2ETest]] (automated). */
class StreamDemoServer(browser: RoboBrowser, port: Int = 8888, streamConfig: StreamConfig = StreamConfig())
  extends MutableHttpServer {
  config.clearListeners().addListeners(HttpServerListener(host = "0.0.0.0", port = Some(port)))

  private val viewerHtml: String = {
    val source = Source.fromURL(getClass.getClassLoader.getResource("viewer.html"))
    try {
      source.mkString
    } finally {
      source.close()
    }
  }

  handler.matcher((url: URL) => url.path.encoded == "/").content(
    Content.string(viewerHtml, ContentType.`text/html`)
  )

  handler.matcher((url: URL) => url.path.encoded == "/signal").wrap(new WebSocketHandler {
    // The session must not start (and flush its offer) until the websocket is
    // physically open — Undertow only attaches its send.text forwarder in
    // onConnect, which happens after this handler returns. The listener's
    // status flips to Open right after that attach.
    override def connect(exchange: HttpExchange, listener: WebSocketListener): Task[Unit] = Task {
      val started = new java.util.concurrent.atomic.AtomicBoolean(false)
      def begin(): Unit = if (started.compareAndSet(false, true)) {
        browser.stream.start(streamConfig).flatMap { session =>
          listener.receive.text.attach { text =>
            session.fromClient(JsonParser(text).as[SignalMessage]).start()
          }
          listener.send.close.on {
            session.stop().start()
          }
          session.connect { message =>
            listener.send.text @= JsonFormatter.Compact(message.json)
          }.map { _ =>
            scribe.info("Viewer websocket open; stream session started")
          }
        }.start()
      }
      listener.status.attach { status =>
        if (status == ConnectionStatus.Open) {
          begin()
        }
      }
      if (listener.status() == ConnectionStatus.Open) {
        begin()
      }
    }
  })
}
