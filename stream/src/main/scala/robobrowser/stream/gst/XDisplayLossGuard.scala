package robobrowser.stream.gst

import com.sun.jna.{Callback, Library, Memory, Native, Pointer}
import org.freedesktop.gstreamer.Element
import org.freedesktop.gstreamer.glib.Natives

import java.util.concurrent.ConcurrentHashMap
import scala.util.{Failure, Success, Try}

/**
 * Keeps the loss of an X display from exiting the process.
 *
 * `ximagesrc` runs in-process and opens its own Xlib connection. When that
 * display goes away under it (Xvfb killed or crashed), Xlib runs the
 * connection's I/O error exit handler, and the default one calls `exit()` —
 * taking the whole JVM with it. libX11 1.7+ lets the exit handler be replaced
 * per connection with one that returns, after which the failing Xlib call
 * simply returns and the capture ends in a pipeline error instead.
 *
 * Two handlers are needed. libX11's default global I/O error handler exits on
 * its own, so a global one that only logs replaces it (it changes nothing for
 * an unguarded connection, whose default exit handler still exits); and the
 * per-connection exit handler has to be installed before the connection is
 * lost, since libX11 reads it before calling the global handler. The exit
 * handler also releases the display lock libX11 took for the exit it expected,
 * or the capture's own teardown would wait on that lock forever. GStreamer does not expose the
 * connection, so it is read from the element: `GstXImageSrc` holds its
 * `GstXContext*` as the first field after its `GstPushSrc` parent, and the
 * context's first field is the `Display*`. The parent's size comes from the
 * GObject type system rather than a constant, and the display found is only
 * used when libX11 reports the name the element was told to open — anything
 * else leaves the element untouched.
 */
private[stream] object XDisplayLossGuard {
  trait ExitHandler extends Callback {
    def invoke(display: Pointer, userData: Pointer): Unit
  }

  trait IoErrorHandler extends Callback {
    def invoke(display: Pointer): Int
  }

  trait X11 extends Library {
    def XDisplayString(display: Pointer): String
    def XSetIOErrorExitHandler(display: Pointer, handler: ExitHandler, userData: Pointer): Unit
    def XSetIOErrorHandler(handler: IoErrorHandler): Pointer
    def XUnlockDisplay(display: Pointer): Unit
  }

  trait GObject extends Library {
    def g_type_query(gtype: Long, query: Pointer): Unit
  }

  trait GstBase extends Library {
    def gst_push_src_get_type(): Long
  }

  /** What to run when a guarded connection is lost, by `Display*` address. */
  private val onLoss = new ConcurrentHashMap[java.lang.Long, () => Unit]()

  // Held for the life of the process: a collected callback is a native crash.
  private val handler: ExitHandler = new ExitHandler {
    override def invoke(display: Pointer, userData: Pointer): Unit = {
      scribe.error("X display connection lost under a capture; the stream ends, the process continues")
      // libX11 takes the connection's display lock before the handlers, expecting the process to exit;
      // returning with it held leaves the capture's teardown waiting on that lock forever.
      libraries.foreach { case (x11, _, _) => x11.XUnlockDisplay(display) }
      Option(onLoss.remove(Pointer.nativeValue(display))).foreach { lost =>
        Try(lost()).failed.foreach(t => scribe.warn(s"X display loss callback failed: ${t.getMessage}"))
      }
    }
  }

  private val ioErrorHandler: IoErrorHandler = new IoErrorHandler {
    override def invoke(display: Pointer): Int = {
      scribe.error("X display connection lost (fatal Xlib I/O error)")
      0
    }
  }

  private lazy val libraries: Try[(X11, GObject, GstBase)] = Try {
    val x11 = Native.load("X11", classOf[X11])
    x11.XSetIOErrorHandler(ioErrorHandler)
    (x11, Native.load("gobject-2.0", classOf[GObject]), Native.load("gstbase-1.0", classOf[GstBase]))
  }

  /** `(type name, instance size)` of a GType. `GTypeQuery` is `{ GType; const gchar*; guint; guint; }`. */
  private def query(gobject: GObject, gtype: Long): (String, Int) = {
    val q = new Memory(Native.POINTER_SIZE * 2L + 8L)
    q.clear()
    gobject.g_type_query(gtype, q)
    val name = Option(q.getPointer(Native.POINTER_SIZE)).map(_.getString(0)).orNull
    (name, q.getInt(Native.POINTER_SIZE * 2L + 4L))
  }

  /** Install the returning exit handler on the display connection `capture` (an
    * `ximagesrc` past READY) opened for `displayName`, running `lost` if that
    * connection is lost. The key to [[release]], when installed. */
  def install(capture: Element, displayName: String, lost: () => Unit): Option[Long] = libraries.flatMap { case (x11, gobject, gstBase) =>
    Try {
      val instance = Natives.getRawPointer(capture)
      val (typeName, elementSize) = query(gobject, instance.getPointer(0).getLong(0))
      val (_, parentSize) = query(gobject, gstBase.gst_push_src_get_type())
      if (typeName != "GstXImageSrc" || parentSize <= 0 || elementSize < parentSize + Native.POINTER_SIZE) {
        scribe.warn(s"X display loss guard: unexpected capture element layout ($typeName, $parentSize/$elementSize bytes)")
        None
      } else Option(instance.getPointer(parentSize)).flatMap(context => Option(context.getPointer(0))) match {
        case Some(display) if x11.XDisplayString(display) == displayName =>
          val key = Pointer.nativeValue(display)
          onLoss.put(key, lost)
          x11.XSetIOErrorExitHandler(display, handler, null)
          Some(key)
        case Some(display) =>
          scribe.warn(s"X display loss guard: capture display '${x11.XDisplayString(display)}' is not '$displayName'")
          None
        case None =>
          scribe.warn("X display loss guard: the capture has not opened its display")
          None
      }
    }
  } match {
    case Success(key) => key
    case Failure(t) =>
      scribe.warn(s"X display loss guard unavailable: ${t.getMessage}")
      None
  }

  /** Stop running the loss callback for a connection the capture is closing. */
  def release(key: Long): Unit = onLoss.remove(key)
}
