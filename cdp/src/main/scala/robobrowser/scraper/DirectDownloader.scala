package robobrowser.scraper

import org.apache.tika.Tika
import rapid.Task
import spice.http.client.HttpClient
import spice.net.{ContentType, URL}

import java.nio.file.{Files, Path}
import java.util.Locale

/** Direct HTTP download of linked files — the fast path for document links
  * discovered during a scrape. Navigating a browser tab to every PDF works
  * (the download interceptor catches it) but costs a page load per file and
  * inherits every navigation flake; a plain GET is faster and steadier for
  * links that are visibly files. Ported from the original scrape-era
  * download/extension-fixup passes.
  *
  * Filename resolution: `Content-Disposition` filename, else the URL's last
  * path segment (query-stripped). Files that end up WITHOUT an extension
  * get one assigned from the response `Content-Type`, falling back to Tika
  * detection over the bytes — servers that hand out `application/octet-stream`
  * for everything are common, and extensionless files confuse every
  * downstream consumer. */
object DirectDownloader {
  private lazy val tika = new Tika()

  case class Downloaded(url: URL,
                        fileName: String,
                        mimeType: Option[String],
                        bytes: Array[Byte])

  /** Redirect hops followed before giving up — CDN-fronted files commonly
    * 302 from the pretty URL to the storage host (Squarespace /s/… did). */
  private val MaxRedirects = 5

  def download(url: URL): Task[Downloaded] = downloadFrom(url, url, MaxRedirects)

  private def downloadFrom(original: URL, current: URL, redirectsLeft: Int): Task[Downloaded] =
    HttpClient.url(current).get.send().flatMap { response =>
      if (response.status.code >= 300 && response.status.code < 400) {
        val location = response.headers.first(spice.http.Headers.Response.`Location`)
        (location, redirectsLeft) match {
          case (Some(loc), n) if n > 0 =>
            val next = if (loc.startsWith("http://") || loc.startsWith("https://")) URL.parse(loc)
                       else URL.parse(s"${current.protocol.scheme}://${current.host}$loc")
            downloadFrom(original, next, n - 1)
          case _ =>
            Task.error(new RuntimeException(s"download failed (${response.status.code}, redirects exhausted): $original"))
        }
      }
      else if (!response.status.isSuccess)
        Task.error(new RuntimeException(s"download failed (${response.status.code}): $original"))
      else response.content match {
        case None => Task.error(new RuntimeException(s"download had no content: $original"))
        case Some(content) => content.asStream.toList.map { byteList =>
          val bytes = byteList.toArray
          val headerMime = response.headers.first(spice.http.Headers.`Content-Type`)
            .map(_.takeWhile(_ != ';').trim.toLowerCase(Locale.ROOT))
            .filterNot(m => m.isEmpty || m == "application/octet-stream")
          val disposition = response.headers.first(spice.http.Headers.Response.`Content-Disposition`)
          val base = fileNameFor(original, disposition)
          val named = ensureExtension(base, headerMime, bytes)
          Downloaded(original, named, headerMime, bytes)
        }
      }
    }

  def downloadTo(url: URL, directory: Path): Task[Path] = download(url).map { d =>
    Files.createDirectories(directory)
    val target = directory.resolve(d.fileName)
    Files.write(target, d.bytes)
    target
  }

  /** `Content-Disposition` filename, else the URL basename (query-stripped),
    * else "download". */
  def fileNameFor(url: URL, contentDisposition: Option[String]): String = {
    val fromDisposition = contentDisposition.flatMap { cd =>
      val FilenamePattern = """(?i)filename\*?=(?:UTF-8''|")?([^";]+)"?""".r
      FilenamePattern.findFirstMatchIn(cd).map(_.group(1).trim)
        .map(raw => java.net.URLDecoder.decode(raw, "UTF-8"))
    }
    val fromUrl = url.path.parts.map(_.value).lastOption
      .map(_.takeWhile(c => c != '?' && c != '#'))
      .filter(_.nonEmpty)
    fromDisposition.orElse(fromUrl).getOrElse("download")
      .replaceAll("[/\\\\]", "-")
  }

  /** Append an extension when the name lacks one: response Content-Type
    * first, Tika byte detection as the fallback. Names that already carry
    * an extension pass through untouched. */
  def ensureExtension(fileName: String, headerMime: Option[String], bytes: Array[Byte]): String =
    if (fileName.contains('.')) fileName
    else {
      val mime = headerMime.orElse(Option(tika.detect(bytes)).filterNot(_ == "application/octet-stream"))
      val ext = mime.flatMap(m => ContentType.parse(m).extension)
      ext.fold(fileName)(e => s"$fileName.$e")
    }
}
