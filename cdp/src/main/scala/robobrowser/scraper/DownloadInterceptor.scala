package robobrowser.scraper

import rapid.{Task, logger}
import rapid.logger._
import robobrowser.{DownloadState, RoboBrowser}
import robobrowser.event.{DownloadProgressEvent, DownloadWillBeginEvent, LoadingFailedEvent, LoadingFinishedEvent, ResponseBody, ResponseReceivedEvent}
import spice.net.{Parameters, Protocol, URL, URLPath}

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths, StandardCopyOption, StandardOpenOption}
import java.time.Instant
import java.util.Base64
import scala.collection.concurrent.TrieMap
import scala.collection.mutable
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success, Try}

class DownloadInterceptor(browser: RoboBrowser, config: DownloadConfig) {
  private case class PendingDownload(context: DownloadContext,
                                     startedAt: Instant,
                                     skipManualWrite: Boolean = false)
  private case class BrowserDownload(guid: String,
                                     url: URL,
                                     suggestedFilename: String,
                                     startedAt: Instant,
                                     frameId: String,
                                     context: Option[DownloadContext])

  private val inFlight = TrieMap.empty[String, PendingDownload]
  private val manualRequestsByUrl = TrieMap.empty[String, Set[String]]
  private val browserDownloads = TrieMap.empty[String, BrowserDownload]
  private val browserUrlToGuids = TrieMap.empty[String, Set[String]]
  private val pendingContextsByFrame = TrieMap.empty[String, Vector[DownloadContext]]
  private val pendingContextsByHost = TrieMap.empty[String, Vector[DownloadContext]]
  private val pendingContextQueue = mutable.ListBuffer.empty[DownloadContext]
  private val PlaceholderHost = "__download_placeholder__"
  private val downloadRoots: List[Path] = {
    val configured = config.downloadDirectory
    val systemDownloads = Option(System.getProperty("user.home"))
      .map(home => Paths.get(home, "Downloads"))
      .map(_.toAbsolutePath.normalize())
    (configured :: systemDownloads.toList).distinct
  }

  browser.event.network.responseReceived.attach(handleResponse)
  browser.event.network.loadingFinished.attach(handleFinished)
  browser.event.network.loadingFailed.attach(handleFailed)
  browser.event.page.downloadWillBegin.attach(handleDownloadWillBegin)
  browser.event.page.downloadProgress.attach(handleDownloadProgress)

  private def handleResponse(event: ResponseReceivedEvent): Unit = {
    Try(URL.parse(event.response.url)) match {
      case Failure(exception) =>
        scribe.warn(s"Unable to parse URL for download candidate: ${event.response.url}", exception)
      case Success(url) =>
        val context = DownloadContext(
          url = url,
          resourceType = event.`type`,
          response = event.response
        )
        if (config.shouldDownload(context)) {
          logContext("response-candidate", Some(context), extra = s"requestId=${event.requestId} frame=${event.frameId.getOrElse("none")}")
          if (attachBrowserContext(context)) {
            scribe.info(s"Browser-managed download detected for ${url.toString} (${context.response.mimeType})")
          } else {
            event.frameId.foreach(id => queueFrameContext(id, context))
            queueHostContext(context)
            queueGlobalContext(context)
            inFlight.put(event.requestId, PendingDownload(context, Instant.now()))
            registerManualRequest(event.requestId, context.url)
            DownloadState.mark(context.url, event.frameId)
            scribe.info(s"Queued manual download capture for ${url.toString} (${context.response.mimeType})")
          }
        }
    }
  }

  private def handleFinished(event: LoadingFinishedEvent): Unit = {
    inFlight.remove(event.requestId).foreach { pending =>
      unregisterManualRequest(event.requestId, pending.context.url)
      if (pending.skipManualWrite) {
        scribe.info(s"Skipping manual capture for ${pending.context.url} because Chrome is managing the download.")
      } else {
        val task = for {
          body <- browser.getResponseBody(event.requestId)
          result <- Task {
            val bytes = decodeBody(body)
            val fileName = config.fileNamer(pending.context)
            val path = writeFile(fileName, bytes)
            DownloadResult(
              url = pending.context.url,
              mimeType = pending.context.response.mimeType,
              resourceType = pending.context.resourceType,
              headers = pending.context.response.headers,
              path = path,
              size = bytes.length
            )
          }
          _ <- writeUrlCompanion(result.path, result.url)
          _ <- config.onDownloaded(result)
          _ = scribe.info(s"Downloaded ${pending.context.url} -> ${result.path} (${result.size} bytes)")
        } yield ()

        task.logErrors.start()
      }
    }
  }

  private def handleFailed(event: LoadingFailedEvent): Unit = {
    inFlight.remove(event.requestId).foreach { pending =>
      unregisterManualRequest(event.requestId, pending.context.url)
      scribe.warn(
        s"Manual download failed for ${pending.context.url} (${pending.context.response.mimeType}) - ${event.errorText} (canceled=${event.canceled})"
      )
    }
  }

  private def handleDownloadWillBegin(event: DownloadWillBeginEvent): Unit = {
    val urlOpt = parseDownloadUrl(event.url)
    if (urlOpt.isEmpty) {
      scribe.warn(s"Unable to parse URL for browser download: ${event.url}. Falling back to placeholder context matching.")
    }
    val downloadUrl = urlOpt.getOrElse(placeholderUrl(event.guid))
    val download = BrowserDownload(
      guid = event.guid,
      url = downloadUrl,
      suggestedFilename = event.suggestedFilename,
      startedAt = Instant.now(),
      frameId = event.frameId,
      context = None
    )
    browserDownloads.put(event.guid, download)
    urlOpt.foreach(url => registerBrowserUrl(event.guid, url))
    urlOpt.foreach(url => DownloadState.mark(url, Some(event.frameId)))
        attachFrameContext(event.frameId, event.guid, downloadUrl)
        urlOpt.foreach(url => attachHostContext(event.guid, url))

    urlOpt.foreach { url =>
      manualRequestsByUrl.remove(url.toString).foreach { requestIds =>
        requestIds.foreach { requestId =>
          inFlight.updateWith(requestId) {
            case Some(pending) =>
              scribe.info(s"Switching manual download to browser-managed for ${url.toString}")
              attachBrowserContext(pending.context)
              attachContextToGuid(event.guid, pending.context, source = "manual-switch")
              Some(pending.copy(skipManualWrite = true))
            case None => None
          }
        }
      }
    }
  }

  private def handleDownloadProgress(event: DownloadProgressEvent): Unit = {
    browserDownloads.get(event.guid).foreach { download =>
      event.state.toLowerCase match {
        case "completed" =>
          finalizeBrowserDownload(download, event).logErrors.start()
        case "canceled" =>
          scribe.warn(s"Browser download canceled for ${download.url} (${download.suggestedFilename})")
          cleanupBrowserDownload(event.guid)
        case _ => // ignore intermediate states
      }
    }
  }

  private def decodeBody(body: ResponseBody): Array[Byte] = {
    if (body.base64Encoded) {
      Base64.getDecoder.decode(body.body)
    } else {
      body.body.getBytes(StandardCharsets.UTF_8)
    }
  }

  private def finalizeBrowserDownload(download: BrowserDownload,
                                      progress: DownloadProgressEvent): Task[Unit] = {
    val contextOpt = resolveContext(download)
    contextOpt match {
      case Some(ctx) =>
        logContext("finalize-context", Some(ctx), extra = s"guid=${download.guid} suggestion=${download.suggestedFilename}")
      case None =>
        scribe.warn(s"[download-context-missing] guid=${download.guid} url=${download.url} suggestion=${download.suggestedFilename}")
    }
    val desiredName = contextOpt.map(config.fileNamer).getOrElse {
      val urlName = DownloadConfig.fileNameFromUrl(download.url)
      val suggested = urlName.getOrElse(extractFileName(download.suggestedFilename))
      val sanitized = DownloadConfig.sanitizeFileName(suggested)
      val extensionHint = contextOpt.flatMap(_.extensionFromMime)
        .orElse {
          urlName.flatMap { name =>
            val dot = name.lastIndexOf('.')
            if (dot >= 0 && dot < name.length - 1) Some(name.substring(dot + 1)) else None
          }
        }
      DownloadConfig.ensureExtension(sanitized, extensionHint)
    }
    val targetPath = uniquePath(config.downloadDirectory.resolve(desiredName))
    val candidateNames = candidateSourceNames(download, desiredName)
    val candidatePaths = candidateNames.flatMap(name => downloadRoots.map(_.resolve(name)))

    waitForFile(candidatePaths).flatMap {
      case Some(source) =>
        moveBrowserDownload(download, progress, contextOpt, source, targetPath)
      case None =>
        cleanupBrowserDownload(download.guid)
        logger.warn(
          s"Browser reported completed download for ${download.url} but no file was found in ${downloadRoots.mkString(", ")}"
        )
    }
  }

  private def attachBrowserContext(context: DownloadContext): Boolean = {
    val key = context.url.toString
    browserUrlToGuids.get(key) match {
      case Some(guids) if guids.nonEmpty =>
        guids.foreach { guid =>
          browserDownloads.updateWith(guid) {
            case Some(download) if download.context.isEmpty =>
              Some(download.copy(context = Some(context)))
            case existing => existing
          }
        }
        true
      case _ => false
    }
  }

  private def cleanupBrowserDownload(guid: String): Unit = {
    browserDownloads.remove(guid).foreach { download =>
      val key = download.url.toString
      browserUrlToGuids.updateWith(key) {
        case Some(set) =>
          val updated = set - guid
          if (updated.nonEmpty) Some(updated) else None
        case None => None
      }
    }
  }

  private def extractFileName(name: String): String = {
    val parts = name.split("[/\\\\]").filter(_.nonEmpty)
    if (parts.nonEmpty) parts.last else name
  }

  private def candidateSourceNames(download: BrowserDownload, targetName: String): List[String] = {
    val suggestion = extractFileName(download.suggestedFilename)
    val sanitized = DownloadConfig.sanitizeFileName(suggestion)
    val guidName = Option(download.guid).map(_.trim).filter(_.nonEmpty)
    val names = (List(suggestion, sanitized, targetName) ++ guidName)
      .map(_.trim)
      .filter(_.nonEmpty)
      .distinct
    names.flatMap { base =>
      if (base.endsWith(".crdownload")) List(base)
      else List(base, s"$base.crdownload")
    }.distinct
  }

  private def moveBrowserDownload(download: BrowserDownload,
                                  progress: DownloadProgressEvent,
                                  contextOpt: Option[DownloadContext],
                                  sourcePath: Path,
                                  targetPath: Path): Task[Unit] = Task {
    Files.createDirectories(targetPath.getParent)
    val pathWithoutCr = if (sourcePath.getFileName.toString.endsWith(".crdownload")) {
      val renamed = sourcePath.resolveSibling(sourcePath.getFileName.toString.stripSuffix(".crdownload"))
      Files.move(sourcePath, renamed, StandardCopyOption.REPLACE_EXISTING)
    } else {
      sourcePath
    }
    val finalPath =
      if (pathWithoutCr == targetPath) pathWithoutCr
      else Files.move(pathWithoutCr, targetPath, StandardCopyOption.REPLACE_EXISTING)
    val size = if (progress.receivedBytes > 0) progress.receivedBytes else Files.size(finalPath)
    val result = DownloadResult(
      url = contextOpt.map(_.url).getOrElse(download.url),
      mimeType = contextOpt.map(_.response.mimeType).getOrElse("application/octet-stream"),
      resourceType = contextOpt.map(_.resourceType).getOrElse("other"),
      headers = contextOpt.map(_.response.headers).getOrElse(Map.empty),
      path = finalPath,
      size = size
    )
    cleanupBrowserDownload(download.guid)
    result
  }.flatMap { result =>
    writeUrlCompanion(result.path, result.url)
      .flatMap(_ => config.onDownloaded(result))
      .flatMap(_ => logger.info(s"Downloaded ${result.url} -> ${result.path} (${result.size} bytes)"))
  }

  private def registerManualRequest(requestId: String, url: URL): Unit = {
    val key = url.toString
    manualRequestsByUrl.updateWith(key) {
      case Some(existing) => Some(existing + requestId)
      case None => Some(Set(requestId))
    }
  }

  private def unregisterManualRequest(requestId: String, url: URL): Unit = {
    val key = url.toString
    manualRequestsByUrl.updateWith(key) {
      case Some(existing) =>
        val updated = existing - requestId
        if (updated.nonEmpty) Some(updated) else None
      case None => None
    }
  }

  private def waitForFile(paths: List[Path],
                          attempts: Int = 240,
                          delay: FiniteDuration = 500.millis): Task[Option[Path]] = {
    val existing = paths.find(p => Files.exists(p) && (!Files.isDirectory(p)))
    existing match {
      case some @ Some(_) => Task.pure(some)
      case None if attempts <= 0 => Task.pure(None)
      case None => Task.sleep(delay).flatMap(_ => waitForFile(paths, attempts - 1, delay))
    }
  }

  private def writeFile(fileName: String, bytes: Array[Byte]): Path = {
    val sanitized = DownloadConfig.sanitizeFileName(fileName)
    val target = uniquePath(config.downloadDirectory.resolve(sanitized))
    Files.createDirectories(target.getParent)
    Files.write(target, bytes)
  }

  private def uniquePath(path: Path, attempt: Int = 0): Path = {
    if (!Files.exists(path)) {
      path
    } else {
      val name = path.getFileName.toString
      val dotIndex = name.lastIndexOf('.')
      val (base, ext) = if (dotIndex > 0) {
        (name.substring(0, dotIndex), name.substring(dotIndex))
      } else {
        (name, "")
      }
      val candidate = path.getParent.resolve(s"${base}_$attempt$ext")
      uniquePath(candidate, attempt + 1)
    }
  }

  private def writeUrlCompanion(path: Path, url: URL): Task[Unit] = Task {
    val companion = path.resolveSibling(path.getFileName.toString + ".url")
    Files.writeString(companion, url.toString, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
  }

  private def parseDownloadUrl(raw: String): Option[URL] = {
    val normalized = normalizeDownloadUrl(raw)
    Try(URL.parse(normalized, validateTLD = false)).toOption
  }

  private def normalizeDownloadUrl(raw: String): String = {
    if (raw.startsWith("blob:")) {
      raw.stripPrefix("blob:")
    } else {
      raw
    }
  }

  private def placeholderUrl(guid: String): URL = {
    URL(
      protocol = Protocol.Https,
      host = PlaceholderHost,
      port = Protocol.Https.defaultPort.getOrElse(443),
      path = URLPath.parse(s"/$guid"),
      parameters = Parameters.empty,
      fragment = None
    )
  }

  private def queueFrameContext(frameId: String, context: DownloadContext): Unit = {
    pendingContextsByFrame.updateWith(frameId) {
      case Some(queue) => Some(queue :+ context)
      case None => Some(Vector(context))
    }
    logContext("queued-frame", Some(context), extra = s"frame=$frameId")
  }

  private def attachFrameContext(frameId: String, guid: String, url: URL): Unit = {
    consumeFrameContext(frameId, url).foreach(context => attachContextToGuid(guid, context, source = "frame"))
  }

  private def queueHostContext(context: DownloadContext): Unit = {
    val key = hostKey(context.url)
    pendingContextsByHost.updateWith(key) {
      case Some(queue) => Some(queue :+ context)
      case None => Some(Vector(context))
    }
    logContext("queued-host", Some(context), extra = s"hostKey=$key")
  }

  private def queueGlobalContext(context: DownloadContext): Unit = pendingContextQueue.synchronized {
    pendingContextQueue += context
    logContext("queued-global", Some(context), extra = s"globalSize=${pendingContextQueue.size}")
  }

  private def attachHostContext(guid: String, downloadUrl: URL): Unit = {
    consumeHostContext(downloadUrl).foreach(context => attachContextToGuid(guid, context, source = "host"))
  }

  private def consumeFrameContext(frameId: String, downloadUrl: URL): Option[DownloadContext] = {
    pendingContextsByFrame.synchronized {
      pendingContextsByFrame.get(frameId) match {
        case Some(queue) if queue.nonEmpty =>
          val idx = queue.indexWhere(ctx => urlsRoughlyMatch(ctx.url, downloadUrl))
          val targetIdx = if (idx >= 0) idx else 0
          val selected = queue(targetIdx)
          val updated = queue.patch(targetIdx, Nil, 1)
          if (updated.nonEmpty) pendingContextsByFrame.put(frameId, updated) else pendingContextsByFrame.remove(frameId)
          removeGlobalContext(selected)
          logContext("consume-frame", Some(selected), extra = s"frame=$frameId guidUrl=$downloadUrl")
          Some(selected)
        case _ => None
      }
    }
  }

  private def consumeHostContext(downloadUrl: URL): Option[DownloadContext] = {
    val key = hostKey(downloadUrl)
    pendingContextsByHost.synchronized {
      pendingContextsByHost.get(key) match {
        case Some(queue) if queue.nonEmpty =>
          val idx = queue.indexWhere(ctx => urlsRoughlyMatch(ctx.url, downloadUrl))
          val targetIdx = if (idx >= 0) idx else 0
          val selected = queue(targetIdx)
          val updated = queue.patch(targetIdx, Nil, 1)
          if (updated.nonEmpty) pendingContextsByHost.put(key, updated) else pendingContextsByHost.remove(key)
          removeGlobalContext(selected)
          logContext("consume-host", Some(selected), extra = s"hostKey=$key guidUrl=$downloadUrl")
          Some(selected)
        case _ => None
      }
    }
  }

  private def consumeGlobalContext(): Option[DownloadContext] = pendingContextQueue.synchronized {
    pendingContextQueue.headOption.map { ctx =>
      pendingContextQueue.remove(0)
      logContext("consume-global", Some(ctx), extra = s"remaining=${pendingContextQueue.size}")
      ctx
    }
  }

  private def removeGlobalContext(context: DownloadContext): Unit = pendingContextQueue.synchronized {
    val idx = pendingContextQueue.indexWhere(_ == context)
    if (idx >= 0) {
      pendingContextQueue.remove(idx)
    }
  }

  private def attachContextToGuid(guid: String,
                                  context: DownloadContext,
                                  source: String = "direct"): Unit = {
    browserDownloads.updateWith(guid) {
      case Some(download) if download.context.isEmpty =>
        removeGlobalContext(context)
        val finalUrl = if (isPlaceholder(download.url)) context.url else download.url
        if (isPlaceholder(download.url)) {
          registerBrowserUrl(guid, context.url)
          DownloadState.mark(context.url, Some(download.frameId))
        }
        logContext("context-attached", Some(context),
          extra = s"guid=$guid source=$source suggestion=${download.suggestedFilename}")
        Some(download.copy(url = finalUrl, context = Some(context)))
      case existing => existing
    }
  }

  private def resolveContext(download: BrowserDownload): Option[DownloadContext] = {
    download.context.orElse {
      val resolved = consumeFrameContext(download.frameId, download.url)
        .orElse(consumeHostContext(download.url))
        .orElse(consumeGlobalContext())
      resolved.foreach(context => attachContextToGuid(download.guid, context, source = "resolve"))
      resolved
    }
  }

  private def logContext(event: String,
                         contextOpt: Option[DownloadContext],
                         extra: String = ""): Unit = {
    contextOpt.foreach { context =>
      val filenameTry = Try(config.fileNamer(context)).toOption.getOrElse("<error>")
      val disposition = context.contentDisposition.getOrElse("<none>")
      val suffix = if (extra.nonEmpty) s" $extra" else ""
      scribe.info(
        s"[$event] url=${context.url} mime=${context.response.mimeType} resource=${context.resourceType} " +
          s"filename=$filenameTry disposition=$disposition$suffix"
      )
    }
  }

  private def urlsRoughlyMatch(a: URL, b: URL): Boolean = {
    (a == b) || (a.host.equalsIgnoreCase(b.host) && a.path == b.path)
  }

  private def hostKey(url: URL): String = s"${url.host.toLowerCase}|${url.path}"

  private def registerBrowserUrl(guid: String, url: URL): Unit = {
    browserUrlToGuids.updateWith(url.toString) {
      case Some(existing) => Some(existing + guid)
      case None => Some(Set(guid))
    }
  }

  private def isPlaceholder(url: URL): Boolean = url.host == PlaceholderHost
}

