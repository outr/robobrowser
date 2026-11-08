package spec
import fabric.*
import fabric.io.{JsonFormatter,JsonParser}
import robobrowser.{FrameCaptcha,AntiCaptchaCoordinateSolver,CoordinateCaptchaSolver}
import rapid.Task
import scala.concurrent.duration.*
import java.net.URI
import java.net.http.{HttpClient,HttpRequest,HttpResponse,WebSocket}
import java.nio.file.{Path,Files}
import java.util.concurrent.{CompletableFuture,ConcurrentHashMap,TimeUnit}
import java.util.concurrent.atomic.AtomicLong

/** Attaches only to this test's existing profile; disconnecting never closes its browser. */
object AttachedCaptchaProbe {
  def main(args:Array[String]):Unit={
    val root=Path.of(System.getProperty("user.home"),".local/state/robobrowser/facebook-login-acceptance")
    val pid=JsonParser(Files.readString(root.resolve("profile/.robobrowser-process.json")))("pid").asLong
    val command=Files.readString(Path.of("/proc",pid.toString,"cmdline"))
    require(command.contains("--user-data-dir="+root.resolve("profile")),"Unexpected browser owner")
    val port="--remote-debugging-port=(\\d+)".r.findFirstMatchIn(command).get.group(1)
    val client=HttpClient.newHttpClient()
    val endpoint=JsonParser(client.send(HttpRequest.newBuilder(URI.create(s"http://127.0.0.1:$port/json/version")).GET().build(),HttpResponse.BodyHandlers.ofString()).body())("webSocketDebuggerUrl").asString
    val pending=new ConcurrentHashMap[Long,CompletableFuture[Json]]();val ids=new AtomicLong()
    val listener=new WebSocket.Listener {
      private val buffer=new StringBuilder
      override def onOpen(ws:WebSocket):Unit=ws.request(1)
      override def onText(ws:WebSocket,text:CharSequence,last:Boolean):java.util.concurrent.CompletionStage[?]={
        buffer.append(text)
        if(last){val j=JsonParser(buffer.toString);buffer.clear();j.get("id").foreach(id=>Option(pending.get(id.asLong)).foreach(_.complete(j)))}
        ws.request(1);CompletableFuture.completedFuture(null)
      }
    }
    val socket=client.newWebSocketBuilder().buildAsync(URI.create(endpoint),listener).get(10,TimeUnit.SECONDS)
    def call(method:String,params:Json,session:Option[String]):Task[Json]=Task{
      val id=ids.incrementAndGet();val answer=new CompletableFuture[Json]();pending.put(id,answer)
      try {
        val fields=List("id"->num(id),"method"->str(method),"params"->params):::session.toList.map(s=>"sessionId"->str(s))
        socket.sendText(JsonFormatter.Compact(obj(fields*)),true).get(10,TimeUnit.SECONDS)
        val r=answer.get(20,TimeUnit.SECONDS);require(r.get("error").isEmpty,"CDP request failed");r("result")
      }finally pending.remove(id)
    }
    var attached:Option[String]=None
    try{
      val target=call("Target.getTargets",obj(),None).sync()("targetInfos").asVector.find{t=>
        val u=URI.create(t("url").asString);t("type").asString=="page"&&u.getHost=="www.facebook.com"&&List("/checkpoint","/two_step_verification").exists(p=>Option(u.getPath).exists(_.startsWith(p)))
      }.getOrElse(throw new IllegalStateException("Verification tab unavailable"))
      attached=Some(call("Target.attachToTarget",obj("targetId"->target("targetId"),"flatten"->bool(true)),None).sync()("sessionId").asString)
      val key=sys.env("ROBOBROWSER_CAPTCHA_KEY")
      val provider=new AntiCaptchaCoordinateSolver(key,timeout=45.seconds)
      val solver=new CoordinateCaptchaSolver {def classifyCoordinates(image:String,comment:String):Task[List[(Double,Double)]]=Task{
        Files.write(root.resolve("captcha-challenge-last.png"),java.util.Base64.getDecoder.decode(image))
        val points=provider.classifyCoordinates(image,comment).sync()
        Files.writeString(root.resolve("captcha-points-last.json"),JsonFormatter.Compact(arr(points.map((x,y)=>arr(num(x),num(y)))*)))
        points
      }}
      val result=new FrameCaptcha(call,attached,s=>println("CAPTCHA_PROGRESS "+s)).solve(solver,1).sync()
      println("CAPTCHA_RESULT "+result)
    }catch{
      case e:IllegalStateException if Option(e.getMessage).exists(_.startsWith("Anti-Captcha image classification failed"))=>println(e.getMessage)
      case _:Exception=>println("CAPTCHA_FAILED_WITHOUT_SENSITIVE_DETAILS")
    }finally{attached.foreach(s=>try call("Target.detachFromTarget",obj("sessionId"->str(s)),None).sync()catch{case _:Exception=>()});socket.abort()}
  }
}
