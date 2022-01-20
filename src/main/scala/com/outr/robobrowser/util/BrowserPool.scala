package com.outr.robobrowser.util

import com.outr.robobrowser.RoboBrowser
import io.youi.net._

import java.util.concurrent.ConcurrentLinkedQueue
import scala.annotation.tailrec
import scala.concurrent.duration._
import scala.util.Try

trait BrowserPool {
  def max: Int = 4
  def keepAlivePing: FiniteDuration = 3.minutes
  def keepAliveURL: URL = url"https://google.com"

  private var created = 0
  private val cache = new ConcurrentLinkedQueue[RoboBrowser]
  private val thread = new Thread {
    setDaemon(true)

    override def run(): Unit = keepAlive()
  }
  thread.start()

  def use[Return](f: RoboBrowser => Return): Return = {
    val b = Option(cache.poll()).getOrElse(createOrWait())
    try {
      val result = f(b)
      cache.add(b)
      result
    } catch {
      case t: Throwable =>
        b.dispose()
        synchronized(created -= 1)
        throw t
    }
  }

  private def next(): Option[RoboBrowser] = Option(cache.poll())

  protected def createOrWait(): RoboBrowser = synchronized {
    if (created < max) {
      scribe.info("Creating new RoboBrowser instance!")
      val browser = create()
      created += 1
      browser
    } else {
      scribe.warn(s"Maximum connections created, waiting for next available...")
      waitFor()
    }
  }

  protected def create(): RoboBrowser = RoboBrowser.Remote.create()

  @tailrec
  private def waitFor(): RoboBrowser = Option(cache.poll()) match {
    case Some(b) => b
    case None =>
      Thread.sleep(1000)
      waitFor()
  }

  @tailrec
  private def keepAlive(): Unit = {
    Thread.sleep(keepAlivePing.toMillis) // Every three minutes
    try {
      (0 until max).toList.flatMap(_ => next()).foreach { browser =>
        try {
          browser.load(keepAliveURL)
          cache.add(browser)
        } catch {
          case t: Throwable =>
            scribe.error(s"Error in browser instance: ${t.getMessage}, disposing")
            Try(browser.dispose())
        }
      }
    } catch {
      case t: Throwable => scribe.warn(s"Error while keeping alive: ${t.getMessage}")
    }
    keepAlive()
  }

  @tailrec
  final def dispose(): Unit = Option(cache.poll()) match {
    case None => // Nothing left to dispose
    case Some(browser) =>
      Try(browser.dispose())
      dispose()
  }
}
