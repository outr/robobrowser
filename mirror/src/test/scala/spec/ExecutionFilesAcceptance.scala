package spec

import fabric.*
import fabric.io.JsonFormatter
import okhttp3.mockwebserver.{Dispatcher,MockResponse,MockWebServer,RecordedRequest}
import robobrowser.{BrowserHost,BrowserNetworkPolicy}
import robobrowser.mirror.{ExecutionServer}
import java.nio.file.{Files,Path}
import java.util.Base64
import scala.jdk.CollectionConverters.*

/** Real Chromium downloads and multipart uploads against an independent upstream. */
object ExecutionFilesAcceptance {
  def main(args:Array[String]):Unit={
    val out=Path.of("target/execution-files");Files.createDirectories(out)
    val downloads=Files.createTempDirectory("rb-execution-downloads-")
    val html="<!doctype html><html><head><title>Export</title></head><body><a href='/original'>unchanged export</a></body></html>"
    val binary=Array[Byte](0,1,2,127,-128,-1)
    val uploads=new java.util.concurrent.CopyOnWriteArrayList[RecordedRequest]()
    val upstream=new MockWebServer()
    upstream.setDispatcher(new Dispatcher {
      override def dispatch(r:RecordedRequest):MockResponse=r.getPath.takeWhile(_!='?') match {
        case "/"=>new MockResponse().setHeader("Content-Type","text/html").setBody("""<!doctype html><title>File workflow</title><a id="download" href="/export" download="export.html">Download</a><a id="binary" href="/binary">Binary download</a><input id="file" type="file" multiple><button id="upload">Upload</button><script>window.uploadResult=null;document.querySelector('#upload').onclick=async()=>{const data=new FormData();for(const file of document.querySelector('#file').files)data.append('files',file);uploadResult=await(await fetch('/upload',{method:'POST',body:data})).text();};</script>""")
        case "/export"=>new MockResponse().setHeader("Content-Type","text/html").setHeader("Content-Disposition","attachment; filename=export.html").setBody(html)
        case "/binary"=>new MockResponse().setHeader("Content-Type","application/octet-stream").setHeader("Content-Disposition","attachment; filename=bytes.bin").setBody(new okio.Buffer().write(binary))
        case "/upload"=>uploads.add(r);new MockResponse().setBody("received")
        case "/authorized"=>new MockResponse().setBody(Option(r.getHeader("Authorization")).getOrElse("missing"))
        case _=>new MockResponse().setResponseCode(404)
      }
    });upstream.start(java.net.InetAddress.getByName("127.0.0.1"),0)
    val host=BrowserHost.start().sync();val front=BrowserHost.start().sync()
    val server=new ExecutionServer(0,networkPolicy=BrowserNetworkPolicy(allowedHosts=List("127.0.0.1"),allowedPorts=List(upstream.getPort),allowPrivateNetworks=true),executionUploadLimit=8L*1024*1024)
    val checks=scala.collection.mutable.ListBuffer.empty[String]
    def check(name:String)(test: => Boolean):Unit={require(test,name);checks+=name;println("PASS "+name)}
    def until(name:String)(test: => Boolean):Unit={val end=System.nanoTime()+20000000000L;var ok=false;while(!ok&&System.nanoTime()<end){ok=scala.util.Try(test).getOrElse(false);if(!ok)Thread.sleep(100)};check(name)(ok)}
    def q(s:String)=JsonFormatter.Compact(str(s))
    def report(status:String,error:Option[String]=None):Unit=Files.writeString(out.resolve("result.json"),JsonFormatter.Default(obj("status"->str(status),"checks"->arr(checks.toList.map(str)*),"error"->error.map(str).getOrElse(Null))))
    try {
      server.start();front.send("Browser.setDownloadBehavior",obj("behavior"->str("allow"),"downloadPath"->str(downloads.toString))).sync()
      val page=front.open(server.address+"/execution#"+server.token).sync()
      page.enableFrames().sync()
      def js(code:String)=page.evaluate(code).sync()
      def local(code:String)=js("local('eval',{code:"+q(code)+"})")
      until("File shell ready")(js("typeof api==='function'").asBoolean)
      js("open("+q("http://127.0.0.1:"+upstream.getPort+"/")+")")
      until("File page and agent ready")(js("agentEpoch!==null").asBoolean&&local("!!document.querySelector('#download')").asBoolean)
      val id=js("session.id");val epoch=js("agentEpoch")
      js("agentCommand({op:'click',selector:'#download'})")
      until("HTML attachment downloads from the sandboxed page")(Files.exists(downloads.resolve("export.html")))
      check("Downloaded HTML is byte exact and has no injected bridge")(Files.readString(downloads.resolve("export.html"))==html)
      js("agentCommand({op:'click',selector:'#binary'})")
      until("Content-Disposition starts a binary download")(Files.exists(downloads.resolve("bytes.bin")))
      check("Downloaded binary bytes remain unchanged")(Files.readAllBytes(downloads.resolve("bytes.bin")).sameElements(binary))
      check("Downloads preserve the executing page and agent")(js("session.id")==id&&js("agentEpoch")==epoch&&local("document.title").asString=="File workflow")
      val payload=Base64.getEncoder.encodeToString("agent upload bytes\n".getBytes(java.nio.charset.StandardCharsets.UTF_8))
      js("agentCommand({op:'files',selector:'#file',files:[{name:'agent.txt',type:'text/plain',base64:"+q(payload)+"}]})")
      check("Agent-selected files are visible in the native input")(local("document.querySelector('#file').files[0].name==='agent.txt'").asBoolean)
      js("agentCommand({op:'click',selector:'#upload'},'upload-once')");js("agentCommand({op:'click',selector:'#upload'},'upload-once')")
      until("Multipart upload completes")(local("uploadResult==='received'").asBoolean)
      check("Agent retry uploads exactly once")(uploads.size()==1)
      val request=uploads.get(0);val body=request.getBody.readUtf8()
      check("Multipart headers filename and bytes reach upstream")(request.getHeader("Content-Type").startsWith("multipart/form-data; boundary=")&&body.contains("filename=\"agent.txt\"")&&body.contains("agent upload bytes\n"))
      check("Control credentials are not sent with the upload")(request.getHeader("Authorization")==null)
      // Use Chromium's file-input API in the real child frame, as a file picker would.
      val child=page.frames.sync().find(_.parent.nonEmpty).get
      val node=front.send("Runtime.evaluate",obj("expression"->str("document.querySelector('#file')"),"contextId"->num(child.contextId)),Some(child.sessionId)).sync()("result")("objectId")
      val chosen=downloads.resolve("chosen.txt");Files.writeString(chosen,"human chosen bytes")
      front.send("DOM.setFileInputFiles",obj("objectId"->node,"files"->arr(str(chosen.toString))),Some(child.sessionId)).sync()
      check("Browser file selection reaches the same page input")(local("document.querySelector('#file').files[0].name==='chosen.txt'").asBoolean)
      local("uploadResult=null;document.querySelector('#upload').click();true")
      until("Browser-selected file uploads")(uploads.size()==2&&local("uploadResult==='received'").asBoolean)
      check("Browser-selected file content reaches upstream")(uploads.get(1).getBody.readUtf8().contains("human chosen bytes"))
      val largeChosen=downloads.resolve("six-megabytes.bin");val largeBytes=Array.fill[Byte](6*1024*1024)(97);Files.write(largeChosen,largeBytes)
      front.send("DOM.setFileInputFiles",obj("objectId"->node,"files"->arr(str(largeChosen.toString))),Some(child.sessionId)).sync()
      local("uploadResult=null;document.querySelector('#upload').click();true")
      until("Native multipart upload exceeds the former four MiB ceiling")(uploads.size()==3&&local("uploadResult==='received'").asBoolean)
      val largeBody=uploads.get(2).getBody.readByteArray()
      check("Large multipart body preserves file bytes and boundaries")(largeBody.length>largeBytes.length&&new String(largeBody,java.nio.charset.StandardCharsets.UTF_8).contains("a"*(6*1024*1024)))
      check("Configured upload ceiling rejects a known oversized body before upstream dispatch")(local("fetch('/upload',{method:'POST',body:new Blob([new Uint8Array(9*1024*1024)])}).then(r=>r.status)").asInt==413&&uploads.size()==3)
      check("Agent can select a file larger than the ordinary command budget")(js("agentCommand({op:'files',selector:'#file',files:[{name:'large.txt',type:'text/plain',base64:btoa('z'.repeat(262144))}]}).then(f=>f[0].size===262144)").asBoolean)
      check("Agent read reports native file metadata")(js("agentCommand({op:'read'}).then(r=>r.elements.find(e=>e.selector==='#file').files[0].size===262144)").asBoolean)
      check("Oversized file is rejected without replacing the existing selection")(js("agentCommand({op:'files',selector:'#file',files:[{name:'oversize.txt',base64:btoa('x'.repeat(524289))}]}).then(()=>false,()=>true)").asBoolean&&local("document.querySelector('#file').files[0].name==='large.txt'").asBoolean)
      check("Invalid file names do not mutate the input")(js("agentCommand({op:'files',selector:'#file',files:[{name:'../bad.txt',base64:'eA=='}]}).then(()=>false,()=>true)").asBoolean&&local("document.querySelector('#file').files[0].name==='large.txt'").asBoolean)
      js("agentCommand({op:'files',selector:'#file',files:[]})")
      check("Agent can clear file selection")(local("document.querySelector('#file').files.length===0").asBoolean)
      check("Site bearer authorization survives fetch with omitted cookies")(local("fetch('/authorized',{headers:{Authorization:'Bearer site-token'},credentials:'omit'}).then(r=>r.text())").asString=="Bearer site-token")
      check("Site bearer authorization survives XMLHttpRequest")(local("new Promise(resolve=>{const r=new XMLHttpRequest();r.open('GET','/authorized');r.setRequestHeader('Authorization','Bearer xhr-token');r.onload=()=>resolve(r.responseText);r.send();})").asString=="Bearer xhr-token")
      check("File workflow leaves no page or viewer errors")(js("local('state')")("metrics")("errors").asVector.isEmpty&&js("$('error').textContent").asString.isEmpty)
      js("close()");report("passed")
    }catch{case e:Throwable=>report("failed",Some(e.toString));throw e}
    finally{server.stop();front.stop().sync();host.stop().sync();upstream.shutdown();val paths=Files.walk(downloads);try paths.sorted(java.util.Comparator.reverseOrder()).forEach(Files.deleteIfExists(_))finally paths.close()}
  }
}
