package robobrowser.scraper

import rapid.Task
import robobrowser.event.NetworkResponse
import spice.net.{ContentType, URL}

import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.util.Locale
import scala.util.Try

case class DownloadConfig(directory: Path,
                          shouldDownload: DownloadContext => Boolean = DownloadConfig.defaultShouldDownload,
                          fileNamer: DownloadContext => String = DownloadConfig.defaultFileName,
                          onDownloaded: DownloadResult => Task[Unit] = _ => Task.unit) {
  val downloadDirectory: Path = directory.toAbsolutePath.normalize()
  Files.createDirectories(downloadDirectory)
}

case class DownloadContext(url: URL,
                           resourceType: String,
                           response: NetworkResponse) {
  private lazy val normalizedHeaders: Map[String, String] = response.headers.map {
    case (key, value) => key.toLowerCase(Locale.ROOT) -> value
  }

  lazy val contentDisposition: Option[String] = normalizedHeaders.get("content-disposition")
  lazy val headerMime: Option[String] = normalizedHeaders.get("content-type")
  lazy val filenameFromDisposition: Option[String] = DownloadConfig.filenameFromDisposition(contentDisposition)
  lazy val filenameFromUrl: Option[String] = DownloadConfig.fileNameFromUrl(url)
  lazy val extensionFromMime: Option[String] = headerMime.flatMap(DownloadConfig.extensionFromMime)
    .orElse(DownloadConfig.extensionFromMime(response.mimeType))

  def hasAttachmentDisposition: Boolean = contentDisposition.exists(_.toLowerCase(Locale.ROOT).contains("attachment"))
}

case class DownloadResult(url: URL,
                          mimeType: String,
                          resourceType: String,
                          headers: Map[String, String],
                          path: Path,
                          size: Long)

object DownloadConfig {
  private val AllowedResourceTypes: Set[String] = Set("document", "media", "other")
  private val BinaryExtensions: Set[String] = Set(
    "pdf", "zip", "rar", "7z", "tar", "gz", "tgz",
    "bmp", "gif", "jpg", "jpeg", "png", "webp", "svg",
    "mp3", "wav", "ogg", "flac",
    "mp4", "mov", "avi", "mkv",
    "exe", "msi", "apk", "dmg", "pkg",
    "doc", "docx", "xls", "xlsx", "ppt", "pptx"
  )
  private val TextualMimePrefixes: Set[String] = Set(
    "text/",
    "application/json",
    "application/javascript",
    "application/xml",
    "application/xhtml+xml",
    "application/x-www-form-urlencoded"
  )
  private val TextualMimeExact: Set[String] = Set.empty
  private val UnsafeFileNameCharacters = "[\\\\/:*?\"<>|\\p{Cntrl}]".r

  def defaultShouldDownload(ctx: DownloadContext): Boolean = {
    val resourceTypeMatches = AllowedResourceTypes.contains(ctx.resourceType.toLowerCase(Locale.ROOT))
    if (!resourceTypeMatches) {
      return false
    }

    val isTextualMime = isTextMime(ctx.response.mimeType) || ctx.headerMime.exists(isTextMime)
    val urlLooksBinary = ctx.filenameFromUrl.exists(name => hasBinaryExtension(name))

    ctx.hasAttachmentDisposition || urlLooksBinary || !isTextualMime
  }

  def defaultFileName(ctx: DownloadContext): String = {
    val raw = ctx.filenameFromDisposition
      .orElse(ctx.filenameFromUrl)
      .getOrElse(s"download-${System.currentTimeMillis()}")

    val sanitized = sanitizeFileName(raw)
    ensureExtension(sanitized, ctx.extensionFromMime)
  }

  def sanitizeFileName(name: String): String = {
    val replaced = UnsafeFileNameCharacters.replaceAllIn(name.trim, "_")
      .replaceAll("\\s+", "_")
      .replaceAll("_+", "_")
      .stripPrefix(".")
      .stripSuffix(".")

    val truncated = if (replaced.length > 200) replaced.substring(0, 200) else replaced
    if (truncated.isEmpty) "download" else truncated
  }

  private[scraper] def fileNameFromUrl(url: URL): Option[String] = {
    val rawPath = url.path.toString()
    if (rawPath.isEmpty) {
      None
    } else {
      val idx = rawPath.lastIndexOf('/')
      val candidate = if (idx >= 0) rawPath.substring(idx + 1) else rawPath
      Option(candidate).filter(_.nonEmpty).map(decode)
    }
  }

  private[scraper] def filenameFromDisposition(disposition: Option[String]): Option[String] = disposition.flatMap { header =>
    val parts = header.split(";").map(_.trim).toList
    val lowerParts = parts.map(_.toLowerCase(Locale.ROOT))

    def find(prefix: String): Option[String] = {
      parts
        .zip(lowerParts)
        .collectFirst {
          case (original, lower) if lower.startsWith(prefix) =>
            original.substring(prefix.length)
        }
        .map(_.trim)
    }

    val star = find("filename*=").map { value =>
      val cleaned = value.stripPrefix("\"").stripSuffix("\"")
      val encoded = cleaned.split("'", 3) match {
        case Array(_, _, data) => data
        case _ => cleaned
      }
      decode(encoded)
    }

    star.orElse {
      find("filename=").map(value => decode(value.stripPrefix("\"").stripSuffix("\"")))
    }
  }

  private[scraper] def extensionFromMime(mime: String): Option[String] =
    parseContentType(mime).flatMap(_.extension)

  private[scraper] def isTextMime(mime: String): Boolean = {
    val normalized = mime.toLowerCase(Locale.ROOT)
    TextualMimePrefixes.exists(normalized.startsWith) || TextualMimeExact.contains(normalized)
  }

  private[scraper] def hasBinaryExtension(name: String): Boolean = {
    val dot = name.lastIndexOf('.')
    if (dot >= 0 && dot < name.length - 1) {
      val ext = name.substring(dot + 1).toLowerCase(Locale.ROOT)
      BinaryExtensions.contains(ext)
    } else {
      false
    }
  }

  private[scraper] def ensureExtension(fileName: String, extensionHint: Option[String]): String = {
    val hasExtension = {
      val idx = fileName.lastIndexOf('.')
      idx > 0 && idx < fileName.length - 1
    }
    extensionHint match {
      case Some(ext) if !hasExtension => s"$fileName.$ext"
      case _ => fileName
    }
  }

  private[scraper] def parseContentType(value: String): Option[ContentType] =
    Try(ContentType.parse(value)).toOption.orElse {
      if (value.contains("/")) Some(new ContentType(value)) else None
    }

  private def decode(value: String): String = URLDecoder.decode(value, StandardCharsets.UTF_8)
}

