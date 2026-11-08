package spec
import robobrowser.*
import rapid.Task
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

/** Real CDP frame/geometry test with a fake classifier; never calls a paid solver. */
object FrameCaptchaAcceptance {
  def main(args:Array[String]):Unit={
    val server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0)
    val port=server.getAddress.getPort
    val wrapper="""<style>body{margin:0}iframe{border:0;display:block}#challenge{display:none}</style><iframe id=anchor src='/recaptcha/api2/anchor' width=300 height=78></iframe><iframe id=challenge src='/recaptcha/api2/bframe' width=400 height=400></iframe><textarea name=g-recaptcha-response hidden></textarea><script>onmessage=e=>{if(e.data==='open'){anchor.style.display='none';challenge.style.display='block'}if(e.data==='solved')document.querySelector('textarea').value='fixture-token'}</script>"""
    val root=s"""<style>body{margin:0}</style><iframe style='display:none' src='/recaptcha/api2/anchor'></iframe><iframe sandbox='allow-scripts' src='http://127.0.0.1:$port/wrapper' style='position:absolute;left:120px;top:90px;width:440px;height:460px;border:8px solid black;transform:scale(.8);transform-origin:top left'></iframe>"""
    server.createContext("/",e=>{
      val html=e.getRequestURI.getPath match {
        case "/wrapper"=>wrapper
        case "/recaptcha/api2/anchor"=>"""<body style='margin:0'><button style='width:300px;height:78px' onclick="parent.postMessage('open','*')">Checkbox</button>"""
        case "/recaptcha/api2/bframe"=>"""<body style='margin:0;background:#abc'><button id=tile style='position:absolute;left:40px;top:50px;width:80px;height:80px' onclick='window.selected=true'>Tile</button><button style='position:absolute;right:10px;bottom:10px;width:90px;height:40px' onclick="if(window.selected)parent.postMessage('solved','*')">Verify</button>"""
        case _=>root
      }
      val bytes=html.getBytes("UTF-8");e.getResponseHeaders.set("Content-Type","text/html");e.sendResponseHeaders(200,bytes.length);e.getResponseBody.write(bytes);e.close()
    });server.start()
    val host=BrowserHost.start(BrowserConfig(windowSize=Some(1200->900),forceDeviceScaleFactor=Some(1),extraArgs=List("--site-per-process"))).sync()
    try{
      val tab=host.open(s"http://localhost:$port/").sync();Thread.sleep(2000)
      tab.evaluate("document.querySelector('iframe[sandbox]').style.top='790px';document.body.style.height='2000px';scrollTo(0,700);true").sync()
      var classified=0
      val solver=new CoordinateCaptchaSolver {def classifyCoordinates(image:String,comment:String):Task[List[(Double,Double)]]=Task{
        val png=ImageIO.read(new ByteArrayInputStream(java.util.Base64.getDecoder.decode(image)))
        require(png.getWidth>=318&&png.getWidth<=322&&png.getHeight>=318&&png.getHeight<=322,"Only the scaled challenge frame must be sent to the classifier")
        require((png.getRGB(5,5)&0xffffff)==0xaabbcc,"Challenge crop must account for page scrolling")
        classified+=1;List((60*.8,80*.8))
      }}
      require(new FrameCaptcha((m,p,s)=>tab.host.send(m,p,s),Some(tab.sessionId),s=>println("TRACE "+s)).solve(solver,3).sync(),"Nested opaque cross-site CAPTCHA did not solve")
      require(classified==1,"Unexpected repeated provider calls")
      println("PASS Nested opaque cross-site frame, scaled coordinates, cropped provider image and nested response token")
      val direct=host.open(s"http://localhost:$port/wrapper").sync();Thread.sleep(1000)
      val bad=new CoordinateCaptchaSolver {def classifyCoordinates(image:String,comment:String):Task[List[(Double,Double)]]=Task.pure(List((-1.0,5.0)))}
      require(scala.util.Try(FrameCaptcha.forTab(direct).solve(bad,1).sync()).isFailure,"Invalid provider coordinates must be rejected")
      require(direct.evaluate("!document.querySelector('textarea').value").sync().asBoolean,"Invalid coordinates submitted an answer")
      println("PASS Invalid provider coordinates fail without solving or submitting")
      direct.evaluate("document.querySelector('#challenge').width=600;document.querySelector('#challenge').height=600;true").sync()
      val large=new CoordinateCaptchaSolver {def classifyCoordinates(image:String,comment:String):Task[List[(Double,Double)]]=Task{
        val png=ImageIO.read(new ByteArrayInputStream(java.util.Base64.getDecoder.decode(image)))
        require(png.getWidth==500&&png.getHeight==500,"Provider image must be explicitly bounded to 500 pixels")
        List((60.0*500/600,80.0*500/600))
      }}
      require(FrameCaptcha.forTab(direct).solve(large,2).sync(),"Downscaled image coordinates did not map back to the challenge")
      println("PASS Oversized challenge is bounded and provider coordinates map back correctly")
      val stale=host.open(s"http://localhost:$port/wrapper").sync();Thread.sleep(1000)
      val moved=new CoordinateCaptchaSolver {def classifyCoordinates(image:String,comment:String):Task[List[(Double,Double)]]=Task{
        stale.navigate("about:blank").sync();Thread.sleep(300);List((60.0,80.0))
      }}
      require(!FrameCaptcha.forTab(stale).solve(moved,1).sync(),"Navigation during classification must not be reported as solved")
      println("PASS Navigation during provider work discards the stale answer")
    }finally{host.stop().sync();server.stop(0)}
  }
}
