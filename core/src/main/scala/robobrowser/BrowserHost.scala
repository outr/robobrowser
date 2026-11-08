package robobrowser

import fabric.*
import fabric.io.{JsonFormatter, JsonParser}
import rapid.Task
import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse, WebSocket}
import java.nio.file.{Files, Path}
import java.time.Duration
import java.util.concurrent.{CompletableFuture, ConcurrentHashMap, CopyOnWriteArrayList, TimeUnit}
import java.util.concurrent.atomic.{AtomicBoolean, AtomicLong}
import scala.jdk.CollectionConverters.*

/** Owns one Chrome process. Tab handles never own or destroy that process.
  * Uses the browser-level socket and flattened CDP sessions. */
final class BrowserHost private(val profile: Path, process: scala.sys.process.Process,
                                socket: WebSocket, pending: ConcurrentHashMap[Long, CompletableFuture[Json]],
                                listeners: CopyOnWriteArrayList[Json => Unit], alive: AtomicBoolean, temporaryProfile: Boolean, profileLock:java.nio.channels.FileLock, profileChannel:java.nio.channels.FileChannel, networkProxy:Option[BrowserNetworkProxy]) {
  val generation:String=java.util.UUID.randomUUID().toString
  private val stopped = new AtomicBoolean(false)
  private val ids = new AtomicLong()
  private val handles = new ConcurrentHashMap[String, BrowserTab]()
  private val sendLock = new Object
  private val inputLock = new Object
  private[robobrowser] def activeInput[A](tab:BrowserTab)(action: => A):A=inputLock.synchronized {
    tab.send("Page.bringToFront").sync();action
  }
  private val shutdownHook=new Thread(()=>stop().sync(),"robobrowser-host-shutdown")
  Runtime.getRuntime.addShutdownHook(shutdownHook)

  def isAlive:Boolean=alive.get() && !stopped.get()

  def send(method: String, params: Json = obj(), session: Option[String] = None, timeoutSeconds:Int=20): Task[Json] = Task {
    require(timeoutSeconds>=1&&timeoutSeconds<=60,"Invalid CDP request deadline")
    if (!alive.get() || (stopped.get() && method != "Browser.close")) throw new IllegalStateException("Browser host is closed")
    val id = ids.incrementAndGet()
    val answer = new CompletableFuture[Json]()
    pending.put(id, answer)
    var awaitingResponse=false
    try {
      val fields = List("id" -> num(id), "method" -> str(method), "params" -> params) :::
        session.toList.map(s => "sessionId" -> str(s))
      sendLock.synchronized {
        if(Thread.currentThread().isInterrupted)throw new InterruptedException("CDP request cancelled before dispatch")
        socket.sendText(JsonFormatter.Compact(obj(fields*)), true).get(10, TimeUnit.SECONDS) }
      awaitingResponse=true
      val response = answer.get(timeoutSeconds, TimeUnit.SECONDS)
      response.get("error").foreach(e => throw new RuntimeException(s"CDP $method: ${e("message").asString}"))
      response.get("result").getOrElse(obj())
    } catch {
      case _:java.util.concurrent.TimeoutException =>
        val phase=if(awaitingResponse)"response"else"send"
        val seconds=if(awaitingResponse)timeoutSeconds else 10
        throw new java.util.concurrent.TimeoutException(s"CDP $method $phase timed out after $seconds seconds; outcome unknown")
    } finally pending.remove(id)
  }

  def onEvent(f: Json => Unit): () => Unit = {
    listeners.add(f)
    () => { listeners.remove(f); () }
  }

  def tabs: Task[List[BrowserTab]] = Task { handles.synchronized {
    val result = send("Target.getTargets").sync()
    val targets = result("targetInfos").asVector.filter(_("type").asString == "page")
    val current = targets.map(_("targetId").asString).toSet
    handles.keySet().asScala.filterNot(current).toList.foreach(id => Option(handles.remove(id)).foreach(_.detachListeners()))
    targets.map { t =>
      val id = t("targetId").asString
      val handle = Option(handles.get(id)).getOrElse {
        val session = send("Target.attachToTarget", obj("targetId" -> str(id), "flatten" -> bool(true))).sync()("sessionId").asString
        val tab = new BrowserTab(this, id, session, t.get("browserContextId").map(_.asString),t.get("openerId").map(_.asString))
        handles.put(id, tab)
        tab.send("Page.enable").sync()
        tab.send("Runtime.enable").sync()
        tab
      }
      handle.targetTitle=t.get("title").map(_.asString).getOrElse("")
      handle
    }.toList
  }}

  def createContext():Task[String]=send("Target.createBrowserContext",obj("disposeOnDetach"->bool(true))).map(_("browserContextId").asString)
  def closeContext(id:String):Task[Unit]=send("Target.disposeBrowserContext",obj("browserContextId"->str(id))).map(_=>())

  def open(url: String = "about:blank", context:Option[String]=None): Task[BrowserTab] = for {
    result <- send("Target.createTarget", obj((List("url" -> str(url)):::context.toList.map(c=>"browserContextId"->str(c)))*))
    all <- tabs
  } yield all.find(_.id == result("targetId").asString).get

  def stop(): Task[Unit] = Task {
    if (stopped.compareAndSet(false, true)) {
      val exited=new CompletableFuture[Unit]()
      Thread.ofVirtual().start(new Runnable{def run():Unit={
        try {
          process.exitValue()
          profileLock.release();profileChannel.close()
          if(temporaryProfile)BrowserHost.removeProfile(profile)
          exited.complete(())
        }catch{case e:Throwable=>exited.completeExceptionally(e)}
      }})
      try {
        // Chrome must flush cookies and storage before the process is terminated.
        if(alive.get())try send("Browser.close",timeoutSeconds=2).sync() catch{case _:Exception=>()}
        try exited.get(5,TimeUnit.SECONDS) catch {
          case _:java.util.concurrent.TimeoutException=>
            process.destroy()
            exited.get(5,TimeUnit.SECONDS)
        }
      } finally {
        alive.set(false);socket.abort()
        pending.values().asScala.foreach(_.completeExceptionally(new IllegalStateException("Browser host stopped")))
        pending.clear()
        handles.values().asScala.foreach(_.detachListeners());handles.clear();listeners.clear()
        try Runtime.getRuntime.removeShutdownHook(shutdownHook) catch{case _:IllegalStateException=>()}
        networkProxy.foreach(_.close())
      }

    }
  }
}

object BrowserHost {
  private def removeProfile(profile:Path):Unit=try {
    val paths=Files.walk(profile)
    try paths.sorted(java.util.Comparator.reverseOrder()).forEach(p=>Files.deleteIfExists(p))finally paths.close()
  }catch{case _:Exception=>()}
  private val processRecord=".robobrowser-process.json"
  /** Called only while holding this managed profile's exclusive owner lock. */
  private def recoverOrphan(profile:Path):Unit={
    val record=profile.resolve(processRecord)
    if(Files.exists(record)){
      require(Files.size(record)<=8192,"Invalid managed browser process record")
      val saved=JsonParser(Files.readString(record))
      val previous=ProcessHandle.of(saved("pid").asLong)
      if(previous.isPresent){
        val process=previous.get();val info=process.info()
        if(process.isAlive&&info.startInstant().isPresent&&info.startInstant().get().toString==saved("started").asString){
          val procCommand=try Files.readString(Path.of("/proc",process.pid().toString,"cmdline"))catch{case _:Exception=>""}
          val command=procCommand+" "+info.commandLine().orElse("")+" "+info.arguments().orElse(Array.empty[String]).mkString(" ")
          require(command.contains("--user-data-dir="+profile.toAbsolutePath.toString),"Cannot verify ownership of previous managed browser")
          process.destroy()
          try process.onExit().get(5,TimeUnit.SECONDS)catch{case _:java.util.concurrent.TimeoutException=>process.destroyForcibly();process.onExit().get(5,TimeUnit.SECONDS)}
        }
      }
      Files.deleteIfExists(record)
    }
  }
  def start(config: BrowserConfig = BrowserConfig(), profile: Option[Path] = None, networkPolicy:Option[BrowserNetworkPolicy] = None): Task[BrowserHost] = Task {
    val browser = Browser.auto().resolvePort()
    val directory = profile.getOrElse(Files.createTempDirectory("robobrowser-host-")).toAbsolutePath.normalize()
    Files.createDirectories(directory)
    val channel=java.nio.channels.FileChannel.open(directory.resolve(".robobrowser.lock"),java.nio.file.StandardOpenOption.CREATE,java.nio.file.StandardOpenOption.WRITE)
    val profileLock=try Option(channel.tryLock()).getOrElse(throw new IllegalStateException("Browser profile is already in use"))
      catch{case e:Exception=>channel.close();throw new IllegalStateException("Browser profile is already in use",e)}
    var networkProxy:Option[BrowserNetworkProxy]=None
    val process = try {
      recoverOrphan(directory)
      networkProxy=networkPolicy.map(new BrowserNetworkProxy(_))
      val base=config.copy(userDataDir=directory.toFile)
      val cfg=networkProxy.map(_.configure(base)).getOrElse(base)
      cfg.prepareUserDataDir();BrowserLauncher.createProcess(browser,cfg).sync()
    }catch{case e:Throwable=>networkProxy.foreach(_.close());profileLock.release();channel.close();if(profile.isEmpty)removeProfile(directory);throw e}
    try {
      val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()
      var endpoint: Option[String] = None
      val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
      while (endpoint.isEmpty && System.nanoTime() < deadline) {
        try {
          val request = HttpRequest.newBuilder(URI.create(s"http://127.0.0.1:${browser.port}/json/version")).timeout(Duration.ofSeconds(2)).build()
          endpoint = Some(JsonParser(client.send(request, HttpResponse.BodyHandlers.ofString()).body())("webSocketDebuggerUrl").asString)
        } catch { case _: Exception => Thread.sleep(100) }
      }
      val pending = new ConcurrentHashMap[Long, CompletableFuture[Json]]()
      val events = new CopyOnWriteArrayList[Json => Unit]()
      val alive = new AtomicBoolean(true)
      val listener = new WebSocket.Listener {
        private val buffer = new StringBuilder
        override def onOpen(ws: WebSocket): Unit = ws.request(1)
        override def onText(ws: WebSocket, text: CharSequence, last: Boolean): java.util.concurrent.CompletionStage[?] = {
          if(buffer.length+text.length()>32*1024*1024){buffer.clear();fail();ws.abort();return CompletableFuture.completedFuture(null)}
          buffer.append(text)
          if (last) {
            try {
              val msg = JsonParser(buffer.toString)
              msg.get("id") match {
                case Some(id) => Option(pending.get(id.asLong)).foreach(_.complete(msg))
                case None => events.asScala.foreach(f => try f(msg) catch { case _: Exception => () })
              }
            } finally buffer.clear()
          }
          ws.request(1)
          CompletableFuture.completedFuture(null)
        }
        override def onError(ws: WebSocket, error: Throwable): Unit = fail()
        override def onClose(ws: WebSocket, status: Int, reason: String): java.util.concurrent.CompletionStage[?] = {
          fail(); CompletableFuture.completedFuture(null)
        }
        private def fail(): Unit = {
          alive.set(false)
          pending.values().asScala.foreach(_.completeExceptionally(new IllegalStateException("Browser connection lost")))
        }
      }
      val socket = client.newWebSocketBuilder().buildAsync(URI.create(endpoint.getOrElse(throw new RuntimeException("Chrome startup timed out"))), listener).get(10, TimeUnit.SECONDS)
      val host=new BrowserHost(directory, process, socket, pending, events, alive, profile.isEmpty,profileLock,channel,networkProxy)
      try {
        val pid=host.send("SystemInfo.getProcessInfo").sync()("processInfo").asVector.find(_("type").asString=="browser").get("id").asLong
        val handle=ProcessHandle.of(pid)
        if(handle.isPresent&&handle.get().info().startInstant().isPresent){
          val record=obj("pid"->num(pid),"started"->str(handle.get().info().startInstant().get().toString),"generation"->str(host.generation))
          Files.writeString(directory.resolve(processRecord),JsonFormatter.Compact(record))
        }
      }catch{case _:Exception=>scribe.warn("Managed browser process identity unavailable; abrupt-owner recovery cannot be guaranteed")}
      host
    } catch {case t:Throwable=>
      networkProxy.foreach(_.close())
      process.destroy()
      Thread.ofVirtual().start(new Runnable{def run():Unit=try process.exitValue() finally{profileLock.release();channel.close();if(profile.isEmpty)removeProfile(directory)}})
      throw t
    }
  }
}

final case class BrowserFrame(id:String, parent:Option[String], sessionId:String, contextId:Long, rootId:String, contextGeneration:String) {
  def identity:String=s"$sessionId:$contextGeneration"
}

final class BrowserTab private[robobrowser](val host: BrowserHost, val id: String, val sessionId: String, val contextId:Option[String],val openerId:Option[String]) {
  @volatile private[robobrowser] var targetTitle:String=""
  /** Browser-owned title from the most recent host.tabs discovery; does not execute page code. */
  def title:String=targetTitle
  private val sessions = ConcurrentHashMap.newKeySet[String]()
  private val sessionParents = new ConcurrentHashMap[String,String]()
  private val contexts = new ConcurrentHashMap[String,(String,Long,String)]()
  private val attachmentFailures = new ConcurrentHashMap[String,String]()
  private val childExecutor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()
  private val attachmentSettings=new Object
  private var attachmentConfiguration = -1
  private val frameInitializers=new CopyOnWriteArrayList[String=>Unit]()
  private val workerInitializers=new CopyOnWriteArrayList[String=>Unit]()
  private val frameVersion = new AtomicLong()
  private var frameCache=List.empty[BrowserFrame]
  private var cachedFrameVersion = -1L
  private var frameCacheTime = 0L
  sessions.add(sessionId)
  private val removeEvents = host.onEvent { event =>
    val owner=event.get("sessionId").map(_.asString).getOrElse("")
    if(sessions.contains(owner)) {
      val params=event.get("params").getOrElse(obj())
      val method=event.get("method").map(_.asString).getOrElse("")
      if(method.startsWith("Runtime.executionContext")||Set("Page.frameAttached","Page.frameDetached","Page.frameNavigated","Target.attachedToTarget","Target.detachedFromTarget").contains(method))frameVersion.incrementAndGet()
      event.get("method").map(_.asString).getOrElse("") match {
        case "Runtime.executionContextCreated" =>
          val context=params("context");val aux=context.get("auxData").getOrElse(obj())
          if(aux.get("isDefault").exists(_.asBoolean))aux.get("frameId").foreach(frame=>contexts.put(frame.asString,(owner,context("id").asLong,java.util.UUID.randomUUID().toString)))
        case "Runtime.executionContextsCleared" => contexts.entrySet().removeIf(_.getValue._1==owner)
        case "Runtime.executionContextDestroyed" => contexts.entrySet().removeIf(e=>e.getValue._1==owner&&e.getValue._2==params("executionContextId").asLong)
        case "Target.attachedToTarget" if params("targetInfo")("type").asString=="iframe" =>
          val child=params("sessionId").asString
          sessions.add(child);sessionParents.put(child,owner)
          childExecutor.submit(new Runnable {def run():Unit = try {
            host.send("Page.enable",session=Some(child)).sync()
            host.send("Runtime.enable",session=Some(child)).sync()
            host.send("Network.enable",session=Some(child)).sync()
            host.send("DOM.enable",session=Some(child)).sync()
            host.send("CSS.enable",session=Some(child)).sync()
            frameInitializers.asScala.foreach(_(child))
            host.send("Target.setAutoAttach",BrowserTab.autoAttach(!frameInitializers.isEmpty || !workerInitializers.isEmpty,!workerInitializers.isEmpty),Some(child)).sync()
          } catch {case _:Exception=>attachmentFailures.put(child,"Child target unavailable")}finally{try host.send("Runtime.runIfWaitingForDebugger",session=Some(child)).sync()catch{case _:Exception=>()}}})
        case "Target.attachedToTarget" if params("targetInfo")("type").asString=="worker" =>
          val child=params("sessionId").asString
          sessions.add(child);sessionParents.put(child,owner)
          childExecutor.submit(new Runnable {def run():Unit = try {
            host.send("Runtime.enable",session=Some(child)).sync()
            workerInitializers.asScala.foreach(_(child))
            host.send("Target.setAutoAttach",BrowserTab.autoAttach(!workerInitializers.isEmpty,!workerInitializers.isEmpty),Some(child)).sync()
          } catch {case _:Exception=>attachmentFailures.put(child,"Worker target unavailable")}finally{try host.send("Runtime.runIfWaitingForDebugger",session=Some(child)).sync()catch{case _:Exception=>()}}})
        case "Target.detachedFromTarget" =>
          val gone=scala.collection.mutable.Set(params("sessionId").asString)
          var size = -1
          while(size!=gone.size){size=gone.size;sessionParents.asScala.foreach{(child,parent)=>if(gone.contains(parent))gone.add(child)}}
          gone.foreach { child=>sessions.remove(child);sessionParents.remove(child);attachmentFailures.remove(child) }
          contexts.entrySet().removeIf(e=>gone.contains(e.getValue._1))
        case _ => ()
      }
    }
  }
  private[robobrowser] def detachListeners():Unit={removeEvents();childExecutor.shutdownNow();contexts.clear();sessions.clear();sessionParents.clear()}
  def ownsSession(id:String):Boolean=sessions.contains(id)
  def activeInput[A](action: => A):A=host.activeInput(this)(action)
  /** Initialize a newly attached child session before its page scripts run. Register before enableFrames. */
  def initializeFrames(initializer:String=>Unit):()=>Unit={frameInitializers.add(initializer);()=>{frameInitializers.remove(initializer);()}}
  /** Instrument dedicated workers before their script executes. Register before enableFrames. */
  def initializeWorkers(initializer:String=>Unit):()=>Unit={workerInitializers.add(initializer);()=>{workerInitializers.remove(initializer);()}}
  def enableFrames():Task[Unit]=Task {attachmentSettings.synchronized {
    val configuration=(if(frameInitializers.isEmpty)0 else 1)|(if(workerInitializers.isEmpty)0 else 2)
    if(configuration!=attachmentConfiguration){
      val settings=BrowserTab.autoAttach(configuration!=0,!workerInitializers.isEmpty)
      send("Target.setAutoAttach",settings).sync()
      contexts.values().asScala.map(_._1).toSet.filter(_!=sessionId).foreach { child=>
        if(ownsSession(child))try host.send("Target.setAutoAttach",settings,Some(child)).sync()catch{case e:Exception=>if(ownsSession(child))throw e}
      }
      attachmentConfiguration=configuration
    }
  }}
  def frameContext(frame:String):(String,Long)=Option(contexts.get(frame)).map(c=>(c._1,c._2)).getOrElse(throw new IllegalStateException("Frame context unavailable"))
  def evaluateInFrame(frame:String,expression:String):Task[Json]=Task {
    val (owner,context)=frameContext(frame)
    BrowserTab.value(host.send("Runtime.evaluate",obj("expression"->str(expression),"contextId"->num(context),"returnByValue"->bool(true),"awaitPromise"->bool(true)),Some(owner)).sync())
  }
  def frames:Task[List[BrowserFrame]]=Task { contexts.synchronized {
    val version=frameVersion.get()
    if(cachedFrameVersion==version&&System.nanoTime()-frameCacheTime<500000000L)frameCache
    else {
    val found=scala.collection.mutable.Map.empty[String,BrowserFrame]
    for(owner<-sessions.asScala.toList) {
      try {
        val tree=host.send("Page.getFrameTree",session=Some(owner)).sync()("frameTree")
        val root=tree("frame")("id").asString
        def visit(n:Json,parent:Option[String]):Unit={
          val frame=n("frame");val id=frame("id").asString
          val parentId=frame.get("parentId").map(_.asString).orElse(parent)
          Option(contexts.get(id)).filter(_._1==owner).foreach { (_,context,generation)=>found(id)=BrowserFrame(id,parentId,owner,context,root,generation) }
          n.get("childFrames").foreach(_.asVector.foreach(child=>visit(child,Some(id))))
        }
        visit(tree,None)
      } catch {case _:Exception if owner!=sessionId=>()}
    }
    frameCache=found.values.toList;cachedFrameVersion=version;frameCacheTime=System.nanoTime();frameCache
    }
  }}
  def linkFrame(frame:BrowserFrame,parent:BrowserFrame):Task[Unit]=Task {
    val owner=host.send("DOM.getFrameOwner",obj("frameId"->str(frame.id)),Some(parent.sessionId)).sync()("backendNodeId")
    val objectId=host.send("DOM.resolveNode",obj("backendNodeId"->owner,"executionContextId"->num(parent.contextId)),Some(parent.sessionId)).sync()("object")("objectId")
    try {
      val result=host.send("Runtime.callFunctionOn",obj("objectId"->objectId,"functionDeclaration"->str("function(id){globalThis.__rbMirror.registerFrame(this,id)}"),"arguments"->arr(obj("value"->str(frame.id))),"returnByValue"->bool(true)),Some(parent.sessionId)).sync()
      if(result.get("exceptionDetails").nonEmpty)throw new IllegalStateException("Frame projection agent unavailable")
    } finally host.send("Runtime.releaseObject",obj("objectId"->objectId),Some(parent.sessionId)).sync()
  }
  /** Maps frame CSS pixels through frame-owner content quads into the top-level viewport. */
  def rootPoint(frameId:String,x:Double,y:Double):Task[(Double,Double)]=Task {
    val graph=frames.sync().map(f=>f.id->f).toMap
    var frame=graph(frameId);var px=x;var py=y;var depth=0
    while(frame.parent.nonEmpty) {
      require(depth<32,"Frame nesting limit exceeded");depth+=1
      val parent=graph(frame.parent.get)
      val backend=host.send("DOM.getFrameOwner",obj("frameId"->str(frame.id)),Some(parent.sessionId)).sync()("backendNodeId")
      val model=host.send("DOM.getBoxModel",obj("backendNodeId"->backend),Some(parent.sessionId)).sync()("model")("content").asVector.map(_.asDouble)
      val size=evaluateInFrame(frame.id,"({width:innerWidth,height:innerHeight})").sync()
      val u=px/size("width").asDouble;val v=py/size("height").asDouble
      px=model(0)+(model(2)-model(0))*u+(model(6)-model(0))*v
      py=model(1)+(model(3)-model(1))*u+(model(7)-model(1))*v
      frame=graph(parent.rootId)
    }
    (px,py)
  }

  def send(method: String, params: Json = obj()): Task[Json] = host.send(method, params, Some(sessionId))
  // An explicit destination supersedes an outstanding reload/resource load. In particular,
  // Chromium can leave Page.navigate pending when it races a media-heavy reload.
  def navigate(url: String): Task[Unit] = Task {
    val fragmentOnly=if(!url.contains('#'))false else {
      val current=host.send("Target.getTargetInfo",obj("targetId"->str(id))).sync()("targetInfo")("url").asString
      current.takeWhile(_!='#')==url.takeWhile(_!='#')
    }
    // Fragment navigation must not cancel resources still loading in the same document.
    if(!fragmentOnly){
      val deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5)
      var stoppedLoading=false
      while(!stoppedLoading){
        try{send("Page.stopLoading").sync();stoppedLoading=true}
        catch{case e:RuntimeException if Option(e.getMessage).contains("CDP Page.stopLoading: Not attached to an active page")&&System.nanoTime()<deadline=>Thread.sleep(25)}
      }
    }
    val r=send("Page.navigate",obj("url"->str(url))).sync()
    r.get("errorText").foreach(e => throw new RuntimeException(e.asString))
  }
  def evaluate(expression: String): Task[Json] = send("Runtime.evaluate", obj(
    "expression" -> str(expression), "returnByValue" -> bool(true), "awaitPromise" -> bool(true)
  )).map { r =>
    BrowserTab.value(r)
  }
  /** A renderer-enforced deadline for untrusted synchronous capture code, independent of the CDP transport deadline. */
  def evaluateBounded(expression:String,timeoutMs:Int,frame:Option[String]=None):Task[Json]=Task {
    require(timeoutMs>0&&timeoutMs<=20000,"Invalid evaluation deadline")
    val (owner,context)=frame.map(frameContext).map((s,c)=>(s,Some(c))).getOrElse((sessionId,None))
    val params=List("expression"->str(expression),"returnByValue"->bool(true),"awaitPromise"->bool(false),"timeout"->num(timeoutMs)):::
      context.toList.map(c=>"contextId"->num(c))
    BrowserTab.value(host.send("Runtime.evaluate",obj(params*),Some(owner)).sync())
  }
  def close(): Task[Unit] = host.send("Target.closeTarget", obj("targetId" -> str(id))).map(_ => ())
}

object BrowserTab {
  private def autoAttach(pause:Boolean,workers:Boolean):Json=obj("autoAttach"->bool(true),"waitForDebuggerOnStart"->bool(pause),"flatten"->bool(true),
    "filter"->arr(obj("type"->str("iframe"),"exclude"->bool(false)),obj("type"->str("worker"),"exclude"->bool(!workers)),obj("exclude"->bool(true))))
  private def value(r:Json):Json={
    r.get("exceptionDetails").foreach {details=>
      val message=details.get("exception").flatMap(_.get("description")).orElse(details.get("text")).map(_.asString).getOrElse("Unknown JavaScript exception")
      throw new RuntimeException("Page evaluation failed: "+message.take(1200))
    }
    r("result").get("value").getOrElse(Null)
  }
}
