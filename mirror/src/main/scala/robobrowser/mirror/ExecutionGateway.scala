package robobrowser.mirror

import com.sun.net.httpserver.HttpExchange
import fabric.*
import fabric.io.{JsonFormatter, JsonParser}
import robobrowser.{BrowserNetworkProxy, BrowserNetworkPolicy}
import org.jsoup.Jsoup
import org.jsoup.nodes.DataNode
import java.net.{URI, CookieManager, CookiePolicy, InetSocketAddress, Proxy}
import java.nio.charset.StandardCharsets.UTF_8
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import scala.jdk.CollectionConverters.*
import scala.io.Source

/** Local website execution with a resource gateway and an agent relay.
  * The loopback origin adapter is not yet a production multi-tenant gateway. */
final class ExecutionGateway(address:String, profileDirectory:Option[java.nio.file.Path]=None, networkPolicy:BrowserNetworkPolicy=BrowserNetworkPolicy(),maxOrigins:Int=4096,maxUploadBytes:Long=64L*1024*1024) {
  require(maxUploadBytes>=1&&maxUploadBytes<=1024L*1024*1024,"Upload limit must be between one byte and one GiB")
  require(maxOrigins>=1&&maxOrigins<=16384,"Invalid execution origin budget")
  private val profiles=profileDirectory.map(new ExecutionProfiles(_))
  private class Session(val id:String,val profile:Option[ExecutionProfiles#Lease]) {
    val cookies=new ExecutionCookies(()=>startURL,()=>persist())
    val network=new BrowserNetworkProxy(networkPolicy)
    val transport=new Proxy(Proxy.Type.SOCKS,new InetSocketAddress("127.0.0.1",network.port))
    val http=new okhttp3.OkHttpClient.Builder().proxy(transport)
      .protocols(java.util.List.of(okhttp3.Protocol.HTTP_1_1))
      .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
      .connectTimeout(15,java.util.concurrent.TimeUnit.SECONDS).readTimeout(30,java.util.concurrent.TimeUnit.SECONDS).build()
    val sockets=new ExecutionSockets(http,cookies)
    val fixtureHttp=http.newBuilder().proxy(Proxy.NO_PROXY).build()
    val origins=new ConcurrentHashMap[String,String]()
    val originKeys=new ConcurrentHashMap[String,String]()
    var fixture=false
    val agent=new ExecutionAgentRelay(if(profile.nonEmpty)new java.security.SecureRandom().nextLong(1L<<50)else 0L)
    val mode="local"
    var startURL=""
    var localURL=""
    private var profileReady=false
    @volatile private var storageData:Json=profile.flatMap(_.data.get("storage")).getOrElse(obj())
    val storage=new ExecutionStorage(storageData,next=>synchronized {
      val previous=storageData;storageData=next
      try persist() catch {case e:Throwable=>storageData=previous;throw e}
    })
    @volatile var fixtureLogin:Option[(String,String)]=None
    val media=new ExecutionMedia(url=>{
      val u=URI.create(url);require(u.getScheme=="http"&&u.getPort==URI.create(address).getPort,"Invalid media origin")
      val bits=Option(u.getHost).getOrElse("").split("\\.");require(bits.length==3&&bits(1)==id&&bits(2)=="localhost","Media outside session")
      val upstream=Option(origins.get(bits(0))).getOrElse(throw new IllegalArgumentException("Unknown media origin"))
      (upstream+u.getRawPath+Option(u.getRawQuery).fold("")("?"+_),if(fixture&&upstream==address)Proxy.NO_PROXY else transport)
    },cookies)
    def persist():Unit=synchronized {
      if(profileReady)profile.foreach(_.save(obj("id"->str(id),"origins"->obj(origins.asScala.toList.map((k,v)=>k->str(v))*),"cookies"->cookies.snapshot,"url"->str(startURL),"storage"->storageData)))
    }
    def restoreProfile():Unit={
      profile.foreach {p=>
        val entries=p.data("origins").asObj.value
        require(entries.size<=maxOrigins,"Profile origin budget exceeded")
        entries.foreach {case(k,v)=>
          val u=URI.create(v.asString)
          require(k.matches("[a-f0-9]{12}")&&Set("http","https")(u.getScheme)&&u.getHost!=null&&u.getUserInfo==null,"Invalid stored origin")
          origins.put(k,v.asString);originKeys.putIfAbsent(v.asString,k)
        }
        cookies.restore(p.data("cookies"))
        startURL=p.data.get("url").map(_.asString).getOrElse("")
      }
      profileReady=true
    }
    def mapped(url:String):String={
      val u=URI.create(url);require(Set("http","https")(u.getScheme)&&u.getHost!=null&&u.getUserInfo==null,"HTTP(S) URL required")
      val upstream=s"${u.getScheme}://${u.getRawAuthority}"
      var added=false
      val key=origins.synchronized {
        Option(originKeys.get(upstream)).getOrElse {
          require(origins.size()<maxOrigins,"Experiment origin budget exceeded")
          val k=UUID.randomUUID().toString.replace("-","").take(12);origins.put(k,upstream);originKeys.put(upstream,k);added=true;k
        }
      }
      if(added)persist()
      s"http://$key.$id.localhost:${URI.create(address).getPort}"+Option(u.getRawPath).filter(_.nonEmpty).getOrElse("/")+Option(u.getRawQuery).fold("")("?"+_)+Option(u.getRawFragment).fold("")("#"+_)
    }
  }
  private val sessions=new ConcurrentHashMap[String,Session]()
  private val writes=new ConcurrentHashMap[String,AtomicInteger]()
  private def resource(name:String):String={val s=Source.fromResource("robobrowser/mirror/"+name);try s.mkString finally s.close()}
  private def send(e:HttpExchange,status:Int,kind:String,body:Array[Byte]):Unit={
    e.getResponseHeaders.set("Content-Type",kind);e.getResponseHeaders.set("Cache-Control","no-store")
    e.getResponseHeaders.set("X-Content-Type-Options","nosniff")
    if(e.getRequestMethod=="HEAD"||status==204||status==304)e.sendResponseHeaders(status,-1)
    else{e.sendResponseHeaders(status,body.length);e.getResponseBody.write(body)}
  }
  private def json(e:HttpExchange,value:Json):Unit=send(e,200,"application/json",JsonFormatter.Compact(value).getBytes(UTF_8))
  def stop():Unit=sessions.values().asScala.toList.foreach(close)
  private def close(s:Session):Unit={
    sessions.remove(s.id,s)
    try {
      s.agent.close();s.sockets.close();s.media.close();s.http.dispatcher().cancelAll();s.http.connectionPool().evictAll();s.http.dispatcher().executorService().shutdown();writes.remove(s.id)
      s.network.close();s.persist()
    }finally s.profile.foreach(_.close())
  }
  def command(c:Json):Json=c("op").asString match {
    case "open" =>
      require(sessions.size()<3,"Close a session before opening another")
      val profile=c.get("profile").map(_.asString).filter(_.nonEmpty).map {name=>
        profiles.getOrElse(throw new IllegalArgumentException("Persistent profiles are not configured")).acquire(name)
      }
      val s=try {
        val id=profile.map(_.data("id").asString).getOrElse(UUID.randomUUID().toString.replace("-",""))
        require(id.matches("[a-f0-9]{32}"),"Invalid stored profile ID")
        new Session(id,profile)
      }catch{case e:Throwable=>profile.foreach(_.close());throw e}
      if(sessions.putIfAbsent(s.id,s)!=null){close(s);throw new IllegalArgumentException("Profile identity is already active")}
      try {
        s.restoreProfile()
        val url=c.get("url").map(_.asString).filter(_.nonEmpty).orElse(profile.flatMap(_.data.get("url")).map(_.asString).filter(_.nonEmpty)).getOrElse(address+"/fixture-execution?run="+s.id)
        s.fixture=url.startsWith(address+"/fixture-execution?")
        require(c.get("mode").forall(_.asString=="local"),"Only local execution is supported")
        val local=s.mapped(url);s.startURL=url;s.localURL=local;s.persist()
        describe(s)
      } catch{case e:Exception=>close(s);throw e}
    case "navigate" =>
      val s=session(c);require(s.mode=="local","Navigation requires the single local executor")
      val url=c("url").asString;val mapped=s.mapped(url)
      s.agent.detach(s.agent.state("epoch").asLong)
      s.synchronized {s.startURL=url;s.localURL=mapped;s.persist();describe(s)}
    case "location"|"resolveLocation" =>
      val s=session(c);val u=URI.create(c("url").asString)
      require(u.getScheme=="http"&&u.getPort==URI.create(address).getPort&&u.getUserInfo==null,"Invalid executing origin")
      val bits=Option(u.getHost).getOrElse("").split("\\.")
      require(bits.length==3&&bits(1)==s.id&&bits(2)=="localhost","Executing origin outside session")
      val upstream=Option(s.origins.get(bits(0))).getOrElse(throw new IllegalArgumentException("Unregistered executing origin"))
      val original=upstream+Option(u.getRawPath).getOrElse("/")+Option(u.getRawQuery).fold("")("?"+_)+Option(u.getRawFragment).fold("")("#"+_)
      if(c("op").asString=="resolveLocation")obj((describe(s).asObj.value.toList.filterNot((k,_)=>Set("url","local")(k)):::List("url"->str(original),"local"->str(u.toString)))* )
      else s.agent.withDocument(c("epoch").asLong) {
        s.synchronized {s.localURL=u.toString;s.startURL=original;s.persist();describe(s)}
      }
    case "resume" => describe(session(c))
    case "close" => close(session(c));obj()
    case "agentAttach" => session(c).agent.attach()
    case "agentDetach" => session(c).agent.detach(c("epoch").asLong);obj()
    case "agentState" => session(c).agent.state
    case "agentExchange" => session(c).agent.exchange(c("epoch").asLong,c.get("ack").filter(_!=Null))
    case "agent" => session(c).agent.call(c("epoch").asLong,c("requestId").asString,c("command"))
    case "counts" => obj("writes"->num(Option(writes.get(c("id").asString)).fold(0)(_.get())),"networkDenied"->num(session(c).network.denied.get()))
    case _=>throw new IllegalArgumentException("Unknown execution operation")
  }
  private def describe(s:Session):Json=s.synchronized {obj("id"->str(s.id),"profile"->s.profile.map(p=>str(p.name)).getOrElse(Null),"mode"->str(s.mode),"url"->str(s.startURL),"local"->str(s.localURL),"tab"->Null,"parent"->str(address))}
  private def session(c:Json):Session=Option(sessions.get(c("id").asString)).getOrElse(throw new IllegalArgumentException("Execution session expired"))
  private val cssUrl="(?i)url\\(\\s*(['\"]?)([^'\")]*)\\1\\s*\\)".r
  private val cssImport="(?i)@import\\s+(['\"])([^'\"]*)\\1".r
  private def resolve(s:Session,base:String,target:String):String={
    if(target.trim.isEmpty||target.startsWith("#"))target
    else scala.util.Try(URI.create(base).resolve(target)).toOption match {
      case Some(u) if Set("http","https")(u.getScheme)=>s.mapped(u.toString)
      case _=>target
    }
  }
  private def link(s:Session,base:String,target:String):String={
    if(target.trim.isEmpty||target.startsWith("#"))target
    else scala.util.Try(URI.create(base).resolve(target)).toOption match {
      case Some(u) if Set("http","https")(u.getScheme)=>
        val origin=s"${u.getScheme}://${u.getRawAuthority}"
        if(s.originKeys.containsKey(origin))s.mapped(u.toString)
        else URI.create(s.mapped(base)).resolve("/__rb_execution/navigate?target="+java.net.URLEncoder.encode(u.toString,UTF_8)).toString
      case _=>target
    }
  }
  private def css(s:Session,base:String,text:String):String={
    val urls=cssUrl.replaceAllIn(text,m=>java.util.regex.Matcher.quoteReplacement("url(\""+resolve(s,base,m.group(2))+"\")"))
    cssImport.replaceAllIn(urls,m=>java.util.regex.Matcher.quoteReplacement("@import \""+resolve(s,base,m.group(2))+"\""))
  }
  private def html(s:Session,url:String,bytes:Array[Byte]):String={
    val d=Jsoup.parse(new java.io.ByteArrayInputStream(bytes),null,url)
    val hasBase=d.selectFirst("base[href]")!=null
    val base=Option(d.selectFirst("base[href]")).map(_.absUrl("href")).filter(_.nonEmpty).getOrElse(url)
    d.select("base,meta[http-equiv~=(?i)^(content-security-policy|refresh)$]").remove()
    d.select("[integrity]").removeAttr("integrity")
    for(n<-d.getAllElements.asScala){
      val attributes=List("href","src","action","poster","formaction","data-src","srcset","imagesrcset").filter(n.hasAttr).map(a=>a->str(n.attr(a)))
      if(attributes.nonEmpty)n.attr("data-rb-original-urls",JsonFormatter.Compact(obj(attributes*)))
    }
    for(attr<-List("href","src","action","poster","formaction","data-src");n<-d.select(s"[$attr]").asScala)n.attr(attr,if(attr=="href"&&Set("a","area")(n.normalName()))link(s,base,n.attr(attr))else resolve(s,base,n.attr(attr)))
    for(attr<-List("srcset","imagesrcset");n<-d.select(s"[$attr]").asScala if !n.attr(attr).contains("data:")){
      n.attr(attr,n.attr(attr).split(",").map { item=>val parts=item.trim.split("\\s+",2);resolve(s,base,parts(0))+parts.drop(1).map(" "+_).mkString }.mkString(", "))
    }
    d.select("style").asScala.foreach(n=>{val value=css(s,base,n.data());n.empty();n.appendChild(new DataNode(value))})
    d.select("[style]").asScala.foreach(n=>n.attr("style",css(s,base,n.attr("style"))))
    val config=JsonFormatter.Compact(obj("session"->str(s.id),"upstream"->str(URI.create(url).resolve("/").toString.stripSuffix("/")),"parent"->str(address),"base"->str(base),"hasBase"->bool(hasBase),"origins"->obj(s.origins.asScala.toList.map{case(k,v)=>s"http://$k.${s.id}.localhost:${URI.create(address).getPort}"->str(v)}*)))
    // Configuration is JSON in an attribute, never interpolated into executable page code.
    d.head().prependElement("script").attr("src","/__rb_execution/bridge.js").attr("data-config",config)
    d.outputSettings().charset(UTF_8).prettyPrint(false)
    val page=JsonFormatter.Compact(str(d.outerHtml())).replace("<","\\u003c")
    s"""<!doctype html><html><head><meta charset="utf-8"><script id="rb-storage-document" type="application/json">$page</script><script src="/__rb_execution/storage.js"></script></head><body></body></html>"""
  }
  private def proxy(e:HttpExchange,s:Session,upstream:String):Unit={
    val path=e.getRequestURI.getRawPath
    if(path=="/__rb_execution/bridge.js")send(e,200,"text/javascript",("(()=>{"+resource("execution-sockets.js")+resource("execution-bridge.js")+"})();").getBytes(UTF_8))
    else if(path=="/__rb_execution/storage.js")send(e,200,"text/javascript",resource("execution-storage.js").getBytes(UTF_8))
    else if(path=="/__rb_execution/storage"){
      require(e.getRequestMethod=="POST","POST required")
      require(Option(e.getRequestHeaders.getFirst("Origin")).contains("http://"+e.getRequestHeaders.getFirst("Host")),"Storage access requires the owning origin")
      val bytes=e.getRequestBody.readNBytes(12*1024*1024+1);require(bytes.length<=12*1024*1024,"Storage request too large")
      val c=JsonParser(new String(bytes,UTF_8))
      json(e,if(c("op").asString=="read")s.storage.read(upstream)else {
        require(c("op").asString=="write","Unknown storage operation");s.storage.write(upstream,c)
      })
    }
    else if(path=="/__rb_execution/socket"){
      require(e.getRequestMethod=="POST","POST required")
      require(Option(e.getRequestHeaders.getFirst("Origin")).contains("http://"+e.getRequestHeaders.getFirst("Host")),"Socket access requires the owning origin")
      val bytes=e.getRequestBody.readNBytes(2*1024*1024+1);require(bytes.length<=2*1024*1024,"Socket operation too large")
      json(e,s.sockets.command(upstream,JsonParser(new String(bytes,UTF_8))))
    }
    else if(path=="/__rb_execution/cookies"){
      require(e.getRequestMethod=="POST","POST required")
      require(Option(e.getRequestHeaders.getFirst("Origin")).contains("http://"+e.getRequestHeaders.getFirst("Host")),"Cookie access requires the owning origin")
      val bytes=e.getRequestBody.readNBytes(8193);require(bytes.length<=8192,"Cookie operation too large")
      val c=JsonParser(new String(bytes,UTF_8));val suffix=c("path").asString
      require(suffix.startsWith("/")&& !suffix.startsWith("//")&& !suffix.contains('\\'),"Invalid cookie path")
      val url=upstream+suffix
      c.get("value").foreach(v=>s.cookies.scriptWrite(url,v.asString))
      json(e,obj("value"->str(s.cookies.scriptRead(url))))
    }
    else if(path=="/__rb_execution/recover"){
      require(e.getRequestMethod=="POST","POST required")
      val bytes=e.getRequestBody.readNBytes(8193);require(bytes.length<=8192,"Media URL too large")
      json(e,s.media.begin(new String(bytes,UTF_8),Option(e.getRequestURI.getRawQuery).contains("probe=1")))
    }else if(path.startsWith("/__rb_execution/media/")){
      val id=path.stripPrefix("/__rb_execution/media/")
      if(id.endsWith(".stream")){
        require(e.getRequestMethod=="GET","GET required")
        val (in,cors)=s.media.open(id.stripSuffix(".stream"),"http://"+e.getRequestHeaders.getFirst("Host"))
        try{
          cors.foreach(v=>e.getResponseHeaders.set("Access-Control-Allow-Origin",if(v=="*"||v=="null")v else URI.create(s.mapped(v)).resolve("/").toString.stripSuffix("/")))
          e.getResponseHeaders.set("Content-Type","video/mp4");e.getResponseHeaders.set("Cache-Control","no-store")
          e.getResponseHeaders.set("X-Content-Type-Options","nosniff")
          e.sendResponseHeaders(200,0);ExecutionMediaBuffer.transfer(in,e.getResponseBody)
        }finally in.close()
      }else if(!id.endsWith(".mp4"))json(e,s.media.status(id))
      else {
        val origin="http://"+e.getRequestHeaders.getFirst("Host")
        val (bytes,cors)=s.media.bytes(id.stripSuffix(".mp4"),origin)
        cors.foreach(v=>e.getResponseHeaders.set("Access-Control-Allow-Origin",if(v=="*"||v=="null")v else URI.create(s.mapped(v)).resolve("/").toString.stripSuffix("/")))
        e.getResponseHeaders.set("Accept-Ranges","bytes")
        val range=Option(e.getRequestHeaders.getFirst("Range"))
        ExecutionRange.range(range,bytes.length) match {
          case Some((a,b))=>if(range.nonEmpty)e.getResponseHeaders.set("Content-Range",s"bytes $a-$b/${bytes.length}");send(e,if(range.nonEmpty)206 else 200,"video/mp4",java.util.Arrays.copyOfRange(bytes,a,b+1))
          case None=>e.getResponseHeaders.set("Content-Range",s"bytes */${bytes.length}");send(e,416,"text/plain",Array.emptyByteArray)
        }
      }
    }
    else if(path=="/__rb_execution/navigate"){
      require(Set("GET","HEAD")(e.getRequestMethod),"GET or HEAD required")
      val query=Option(e.getRequestURI.getRawQuery).getOrElse("")
      require(query.startsWith("target=")&&query.length<=16384,"Invalid navigation target")
      val target=java.net.URLDecoder.decode(query.stripPrefix("target="),UTF_8)
      e.getResponseHeaders.set("Location",s.mapped(target))
      e.getResponseHeaders.set("Cache-Control","no-store")
      e.sendResponseHeaders(302,-1)
    }
    else if(path=="/__rb_execution/map"){
      require(e.getRequestMethod=="POST","POST required")
      val body=e.getRequestBody.readNBytes(8193);require(body.length<=8192,"URL too large")
      json(e,obj("url"->str(s.mapped(new String(body,UTF_8)))))
    } else {
      val mappedURL="http://"+e.getRequestHeaders.getFirst("Host")+e.getRequestURI.toString
      s.media.cached(mappedURL) match {case Some((bytes,cors))=>
        cors.foreach(v=>e.getResponseHeaders.set("Access-Control-Allow-Origin",if(v=="*"||v=="null")v else URI.create(s.mapped(v)).resolve("/").toString.stripSuffix("/")))
        e.getResponseHeaders.set("Accept-Ranges","bytes")
        val range=Option(e.getRequestHeaders.getFirst("Range"))
        ExecutionRange.range(range,bytes.length) match {
          case Some((a,b))=>if(range.nonEmpty)e.getResponseHeaders.set("Content-Range",s"bytes $a-$b/${bytes.length}");send(e,if(range.nonEmpty)206 else 200,"video/mp4",java.util.Arrays.copyOfRange(bytes,a,b+1))
          case None=>e.getResponseHeaders.set("Content-Range",s"bytes */${bytes.length}");send(e,416,"text/plain",Array.emptyByteArray)
        }
        return
        case None=>()
      }
      val credentialMode=if(path.startsWith("/__rb_execution/fetch/"))Some(path.stripPrefix("/__rb_execution/fetch/"))else None
      credentialMode.foreach(m=>require(Set("omit","same-origin","include")(m),"Invalid credential mode"))
      val requestPath=if(credentialMode.nonEmpty){
        val query=Option(e.getRequestURI.getRawQuery).getOrElse("")
        require(query.startsWith("target="),"Missing request target")
        val target=java.net.URLDecoder.decode(query.stripPrefix("target="),UTF_8)
        require(target.startsWith("/")&& !target.startsWith("//")&& !target.contains('\\'),"Invalid request target")
        require(!URI.create(target).getPath.startsWith("/__rb_execution/"),"Reserved request target")
        target
      }else e.getRequestURI.toString
      val url=upstream+requestPath
      val contentLength=Option(e.getRequestHeaders.getFirst("Content-Length")).map(_.toLong).getOrElse(-1L)
      require(contentLength>= -1,"Invalid upload length")
      if(contentLength>maxUploadBytes){send(e,413,"text/plain","Upload exceeds configured request limit".getBytes(UTF_8));return}
      // A public page cannot turn this gateway into access to host-local services. The
      // controlled fixture is the sole loopback exception. SOCKS policy pins checked IPs.
      val client=if(s.fixture&&upstream==address)s.fixtureHttp else s.http
      val request=new okhttp3.Request.Builder().url(url)
      val requestHeaders=new java.util.HashMap[String,java.util.List[String]]()
      def header(k:String,v:String):Unit={request.addHeader(k,v);requestHeaders.put(k,java.util.List.of(v))}
      val dropped=Set("host","cookie","connection","content-length","accept-encoding","authorization","proxy-authorization","origin","referer")
      // Preserve request mode and browser hints. Removing navigation metadata makes
      // some upstreams return an error document for an otherwise valid browser request.
      // Site/destination describe the proxy iframe, so do not forward those unchanged.
      val browserHeaders=Set("sec-fetch-mode","sec-fetch-user")
      e.getRequestHeaders.asScala.foreach{case(k,vs)=>
        val name=k.toLowerCase
        if(!dropped(name)&&(!name.startsWith("sec-")||browserHeaders(name)||name.startsWith("sec-ch-")))vs.asScala.foreach(v=>header(k,v))
      }
      header("Accept-Encoding","identity")
      // Map only origins belonging to this session. Never grant an unrelated caller an upstream origin.
      def realOrigin(v:String):Option[String]=scala.util.Try(URI.create(v)).toOption.flatMap {u=>
        Option(u.getHost).filter(_.endsWith("."+s.id+".localhost")).flatMap(h=>Option(s.origins.get(h.takeWhile(_!='.'))))
      }
      Option(e.getRequestHeaders.getFirst("Origin")).flatMap(realOrigin).foreach(v=>header("Origin",v))
      Option(e.getRequestHeaders.getFirst("Referer")).foreach {v=>realOrigin(v).foreach(o=>{val u=URI.create(v);header("Referer",o+Option(u.getRawPath).getOrElse("/")+Option(u.getRawQuery).fold("")("?"+_))})}
      // A website's explicit API authorization is distinct from the control-service credential.
      // Forward it only for requests originating from this session's mapped browser pages.
      val mappedCaller=Option(e.getRequestHeaders.getFirst("Origin")).flatMap(realOrigin).nonEmpty||
        (Option(e.getRequestHeaders.getFirst("Sec-Fetch-Site")).contains("same-origin")&&Option(e.getRequestHeaders.getFirst("Referer")).flatMap(realOrigin).nonEmpty)
      if(mappedCaller)Option(e.getRequestHeaders.getFirst("Authorization")).foreach(v=>header("Authorization",v))
      val sameOrigin=Option(e.getRequestHeaders.getFirst("Origin")).flatMap(realOrigin).contains(upstream)||
        Option(e.getRequestHeaders.getFirst("Sec-Fetch-Site")).contains("same-origin")
      val credentials=credentialMode.forall(m=>m=="include"||(m=="same-origin"&&sameOrigin))
      val cookie=if(credentials)s.cookies.header(url,e.getRequestMethod,Option(e.getRequestHeaders.getFirst("Sec-Fetch-Mode")).contains("navigate"))else ""
      if(cookie.nonEmpty)request.addHeader("Cookie",cookie)
      val method=e.getRequestMethod
      val hasBody=contentLength>0||e.getRequestHeaders.getFirst("Transfer-Encoding")!=null||Set("POST","PUT","PATCH","PROPPATCH","REPORT")(method)
      val payload=if(hasBody)new ExecutionUploadBody(e.getRequestBody,contentLength,maxUploadBytes)else null
      val response=client.newCall(request.method(method,payload).build()).execute()
      try {
      val status=response.code()
      val headers=response.headers().toMultimap.asScala.toMap
      if(credentials)s.cookies.put(URI.create(url),headers.asJava)
      def first(name:String):Option[String]=headers.find(_._1.equalsIgnoreCase(name)).flatMap(_._2.asScala.headOption)
      val kind=first("content-type").getOrElse("application/octet-stream")
      val attachment=first("content-disposition").exists(_.takeWhile(_!=';').trim.equalsIgnoreCase("attachment"))
      val rewritten= !attachment&&(kind.toLowerCase.startsWith("text/html")||kind.toLowerCase.startsWith("text/css"))
      val omit=Set("content-length","transfer-encoding","connection","set-cookie","content-security-policy","content-security-policy-report-only","x-frame-options","cross-origin-opener-policy","cross-origin-embedder-policy","cross-origin-resource-policy","strict-transport-security","content-encoding","location","access-control-allow-origin","clear-site-data","link")
      headers.foreach{case(k,vs)=>if(!omit(k.toLowerCase)||(!rewritten&&k.equalsIgnoreCase("content-encoding")))vs.asScala.foreach(v=>e.getResponseHeaders.add(k,v))}
      first("location").foreach {v=>
        val mapped=resolve(s,url,v)
        val redirect=credentialMode.fold(mapped){mode=>
          val u=URI.create(mapped)
          s"${u.getScheme}://${u.getRawAuthority}/__rb_execution/fetch/$mode?target="+java.net.URLEncoder.encode(Option(u.getRawPath).getOrElse("/")+Option(u.getRawQuery).fold("")("?"+_),UTF_8)
        }
        e.getResponseHeaders.set("Location",redirect)
      }
      first("access-control-allow-origin").foreach(v=>e.getResponseHeaders.set("Access-Control-Allow-Origin",if(v=="*"||v=="null")v else URI.create(s.mapped(v)).resolve("/").toString.stripSuffix("/")))
      e.getResponseHeaders.set("Referrer-Policy","same-origin")
      // Page scripts cannot frame or call the demo control origin; their capability only fetches site resources.
      e.getResponseHeaders.set("Content-Security-Policy",s"frame-ancestors $address http://*.${s.id}.localhost:${URI.create(address).getPort}; object-src 'none'")
      val in=Option(response.body()).map(_.byteStream()).getOrElse(java.io.InputStream.nullInputStream())
      try {
        if(rewritten){
          val decoded=first("content-encoding").map(_.toLowerCase) match {
            case None|Some("identity")=>in
            case Some("gzip")=>new java.util.zip.GZIPInputStream(in)
            case Some("deflate")=>new java.util.zip.InflaterInputStream(in)
            case _=>throw new IllegalArgumentException("Unsupported experiment document encoding")
          }
          val bytes=decoded.readNBytes(8*1024*1024+1);require(bytes.length<=8*1024*1024,"Experiment document limit")
          val text=if(kind.toLowerCase.startsWith("text/html"))html(s,url,bytes)else css(s,url,new String(bytes,UTF_8))
          send(e,status,kind.takeWhile(_!=';')+"; charset=utf-8",text.getBytes(UTF_8))
        }else {
          e.getResponseHeaders.set("Content-Type",kind)
          if(kind.toLowerCase.startsWith("video/"))e.getResponseHeaders.set("Cache-Control","no-store")
          if(e.getRequestMethod=="HEAD"||Set(204,304)(status))e.sendResponseHeaders(status,-1)
          else {e.sendResponseHeaders(status,0);in.transferTo(e.getResponseBody)}
        }
      }finally{in.close()}
      }finally response.close()
    }
  }
  /** Called before demo routes: a website origin must never fall through to /api or /control. */
  def route(e:HttpExchange):Boolean={
    val host=Option(e.getRequestHeaders.getFirst("Host")).getOrElse("").takeWhile(_!=':').toLowerCase
    if(host.endsWith(".localhost")){
      try {
        val bits=host.split("\\.");require(bits.length==3,"Unknown execution origin")
        val s=Option(sessions.get(bits(1))).getOrElse(throw new IllegalArgumentException("Execution session expired"))
        val upstream=Option(s.origins.get(bits(0))).getOrElse(throw new IllegalArgumentException("Unknown upstream origin"))
        proxy(e,s,upstream)
      }catch{case ex:Exception=>if(e.getResponseCode<0)send(e,502,"text/plain",Option(ex.getMessage).getOrElse("Proxy failed").take(500).getBytes(UTF_8))else e.close()}
      true
    }else e.getRequestURI.getPath match {
      case "/"|"/execution"=>
        e.getResponseHeaders.set("Content-Security-Policy",s"default-src 'self'; script-src 'self'; style-src 'unsafe-inline'; frame-src http://*.localhost:${URI.create(address).getPort}; frame-ancestors 'none'; object-src 'none'")
        send(e,200,"text/html; charset=utf-8",resource("execution.html").getBytes(UTF_8));true
      case "/execution.js"=>send(e,200,"text/javascript",resource("execution.js").getBytes(UTF_8));true
      case "/fixture-execution"=>
        val mode=Option(e.getRequestHeaders.getFirst("Sec-Fetch-Mode")).filter(Set("navigate","cors","same-origin","no-cors")).getOrElse("missing")
        val html=resource("execution-fixture.html").replace("<body>",s"<body data-request-mode=\"$mode\">")
        send(e,200,"text/html; charset=utf-8",html.getBytes(UTF_8));true
      case "/fixture-execution.js"=>send(e,200,"text/javascript",resource("execution-fixture.js").getBytes(UTF_8));true
      case p if p.startsWith("/fixture-execution-auth/")=>
        val run=Option(e.getRequestURI.getRawQuery).getOrElse("").stripPrefix("run=")
        val s=Option(sessions.get(run)).getOrElse(throw new IllegalArgumentException("Unknown fixture run"))
        p.stripPrefix("/fixture-execution-auth/") match {
          case "login"=>
            require(e.getRequestMethod=="POST","POST required")
            val username=new String(e.getRequestBody.readNBytes(128),UTF_8)
            val id=UUID.randomUUID().toString;s.fixtureLogin=Some(id->username)
            e.getResponseHeaders.add("Set-Cookie",s"fixtureLogin=$id; Path=/; HttpOnly; SameSite=Lax")
            e.getResponseHeaders.add("Set-Cookie","fixtureCSRF=visible; Path=/; SameSite=Lax")
            json(e,obj("ok"->bool(true)))
          case "me"=>
            val cookie=Option(e.getRequestHeaders.getFirst("Cookie")).getOrElse("")
            val user=s.fixtureLogin.filter((id,_)=>cookie.split("; ").contains("fixtureLogin="+id)).map(_._2)
            json(e,obj("user"->user.map(str).getOrElse(Null),"scriptCookie"->bool(cookie.split("; ").contains("fromScript=written"))))
          case "logout"=>
            require(e.getRequestMethod=="POST","POST required");s.fixtureLogin=None
            e.getResponseHeaders.add("Set-Cookie","fixtureLogin=; Path=/; Max-Age=0; HttpOnly; SameSite=Lax")
            json(e,obj("ok"->bool(true)))
          case _=>throw new IllegalArgumentException("Unknown fixture auth action")
        };true
      case "/fixture-execution-action"=>
        require(e.getRequestMethod=="POST","POST required")
        val run=Option(e.getRequestURI.getRawQuery).getOrElse("").stripPrefix("run=")
        require(run.matches("[a-f0-9]{32}")&&sessions.containsKey(run),"Unknown fixture run")
        val count=writes.computeIfAbsent(run,_=>new AtomicInteger()).incrementAndGet()
        json(e,obj("writes"->num(count)));true
      case _=>false
    }
  }
}
