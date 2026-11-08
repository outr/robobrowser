package spec
import robobrowser.*
import robobrowser.VisualCaptcha.*
import rapid.Task
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

/** Exercises the existing RoboBrowser extension against an opaque cross-site frame. */
object LegacyFrameCaptchaAcceptance {
  def main(args:Array[String]):Unit={
    val server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);val port=server.getAddress.getPort
    server.createContext("/",e=>{
      val page=e.getRequestURI.getPath match {
        case "/wrapper"=>"""<style>body{margin:0}iframe{border:0}#challenge{display:none}</style><iframe id=anchor src='/anchor' width=300 height=78></iframe><iframe id=challenge src='/bframe' width=400 height=400></iframe><textarea name=g-recaptcha-response hidden></textarea><script>onmessage=e=>{if(e.data==='open'){anchor.style.display='none';challenge.style.display='block'}if(e.data==='solved')document.querySelector('textarea').value='test-token'}</script>"""
        case "/anchor"=>"""<body style='margin:0'><button style='width:300px;height:78px' onclick="parent.postMessage('open','*')">Checkbox</button>"""
        case "/bframe"=>"""<body style='margin:0;background:#abc'><button style='position:absolute;left:40px;top:50px;width:80px;height:80px' onclick='window.selected=true'>Tile</button><button style='position:absolute;right:10px;bottom:10px;width:90px;height:40px' onclick="if(window.selected)parent.postMessage('solved','*')">Verify</button>"""
        case _=>s"""<iframe sandbox='allow-scripts' src='http://127.0.0.1:$port/wrapper' style='position:absolute;left:120px;top:90px;width:440px;height:460px;border:8px solid black'></iframe>"""
      }
      val bytes=page.getBytes("UTF-8");e.getResponseHeaders.set("Content-Type","text/html");e.sendResponseHeaders(200,bytes.length);e.getResponseBody.write(bytes);e.close()
    });server.start()
    val browser=RoboBrowser(RoboBrowserConfig(freshProfile=true,browserConfig=BrowserConfig(windowSize=Some(1200->900),forceDeviceScaleFactor=Some(1),extraArgs=List("--site-per-process")))).sync()
    try{
      browser.navigate(s"http://localhost:$port/").sync();Thread.sleep(2000)
      val solver=new CoordinateCaptchaSolver{def classifyCoordinates(image:String,comment:String):Task[List[(Double,Double)]]=Task{
        val png=ImageIO.read(new ByteArrayInputStream(java.util.Base64.getDecoder.decode(image)))
        require(png.getWidth==400&&png.getHeight==400,"Legacy solver sent more than the challenge")
        List((60.0,80.0))
      }}
      require(browser.solveRecaptchaVisually(solver,3).sync(),"Legacy nested-frame CAPTCHA did not solve")
      println("PASS Existing RoboBrowser visual CAPTCHA API solves an opaque cross-site nested challenge")
    }finally{browser.dispose().sync();server.stop(0)}
  }
}
