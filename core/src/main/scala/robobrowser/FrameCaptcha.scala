package robobrowser

import fabric.*
import rapid.Task
import scala.collection.mutable

/** Provider receives only the cropped challenge, never the surrounding page or credentials. */
trait CoordinateCaptchaSolver {
  def classifyCoordinates(imageBase64:String,comment:String):Task[List[(Double,Double)]]
}

/** Frame-aware CAPTCHA transport shared by the managed host and legacy CDP client. */
final class FrameCaptcha(call:(String,Json,Option[String])=>Task[Json],rootSession:Option[String],progress:String=>Unit=_=>()) {
  private case class Frame(id:String,parent:Option[String],session:Option[String],root:String,context:Json)
  private case class Region(frame:Frame,x:Double,y:Double,width:Double,height:Double)
  private val attached=mutable.Map.empty[String,String]
  private def send(method:String,params:Json=obj(),session:Option[String]=rootSession):Json=call(method,params,session).sync()
  private def evaluate(frame:Frame,code:String):Json={
    val r=send("Runtime.evaluate",obj("expression"->str(code),"contextId"->frame.context,"returnByValue"->bool(true)),frame.session)
    require(r.get("exceptionDetails").isEmpty,"CAPTCHA frame evaluation failed")
    r("result").get("value").getOrElse(Null)
  }
  private def frames():Map[String,Frame]={
    val found=mutable.Map.empty[String,Frame]
    def visit(tree:Json,session:Option[String],root:String,parent:Option[String],depth:Int):Unit={
      require(depth<32 && found.size<128,"CAPTCHA frame budget exceeded")
      val id=tree("frame")("id").asString
      if(!found.contains(id)){
        val context=try Some(send("Page.createIsolatedWorld",obj("frameId"->str(id),"worldName"->str("robobrowser-captcha")),session)("executionContextId"))catch{case _:Exception=>None}
        context match {
          case Some(c)=>
            found(id)=Frame(id,parent,session,root,c)
            tree.get("childFrames").foreach(_.asVector.foreach(t=>visit(t,session,root,Some(id),depth+1)))
          case None=>
            // OOPIF contexts belong to another target; attach only to this tree's child.
            val child=attached.getOrElseUpdate(id,send("Target.attachToTarget",obj("targetId"->str(id),"flatten"->bool(true)))("sessionId").asString)
            val nested=send("Page.getFrameTree",session=Some(child))("frameTree")
            visit(nested,Some(child),id,parent,depth+1)
        }
      }
    }
    val tree=send("Page.getFrameTree")("frameTree")
    visit(tree,rootSession,tree("frame")("id").asString,None,0)
    // Page.getFrameTree omits OOPIFs in some Chrome versions. Discover only targets
    // whose owning iframe belongs to an already verified document in this tree.
    val candidates=send("Target.getTargets")("targetInfos").asVector.filter(_("type").asString=="iframe").map(_("targetId").asString)
    require(candidates.size<=512,"CAPTCHA target budget exceeded")
    var changed=true
    while(changed){
      changed=false
      for(id<-candidates if !found.contains(id)){
        val parent=found.values.toList.find{f=>
          try {
            val backend=send("DOM.getFrameOwner",obj("frameId"->str(id)),f.session)("backendNodeId")
            val remote=send("DOM.resolveNode",obj("backendNodeId"->backend,"executionContextId"->f.context),f.session)("object")("objectId")
            try send("Runtime.callFunctionOn",obj("objectId"->remote,"functionDeclaration"->str("function(){return this.ownerDocument===document}"),"returnByValue"->bool(true)),f.session)("result").get("value").contains(bool(true))
            finally send("Runtime.releaseObject",obj("objectId"->remote),f.session)
          }catch{case _:Exception=>false}
        }
        parent.foreach{p=>
          val child=attached.getOrElseUpdate(id,send("Target.attachToTarget",obj("targetId"->str(id),"flatten"->bool(true)))("sessionId").asString)
          visit(send("Page.getFrameTree",session=Some(child))("frameTree"),Some(child),id,Some(p.id),0)
          changed=true
        }
      }
    }
    found.toMap
  }
  private def region(graph:Map[String,Frame],needle:String):Option[Region]={
    graph.values.toList.sortBy(_.id).iterator.flatMap{f=>
      val result=evaluate(f,s"""(()=>{for(const e of document.querySelectorAll('iframe')){if(!(e.src||'').includes('$needle'))continue;const r=e.getBoundingClientRect(),s=getComputedStyle(e);if(r.width>10&&r.height>10&&s.visibility!=='hidden'&&s.display!=='none')return {x:r.x,y:r.y,width:r.width,height:r.height};}return null})()""")
      if(result==Null)None else Some(Region(f,result("x").asDouble,result("y").asDouble,result("width").asDouble,result("height").asDouble))
    }.take(1).toList.headOption
  }
  private def rootPoint(graph:Map[String,Frame],source:Frame,x:Double,y:Double):(Double,Double)={
    var f=source;var px=x;var py=y;var depth=0
    while(f.parent.nonEmpty){
      require(depth<32,"CAPTCHA frame nesting exceeded");depth+=1
      val parent=graph(f.parent.get)
      val owner=send("DOM.getFrameOwner",obj("frameId"->str(f.id)),parent.session)("backendNodeId")
      val quad=send("DOM.getBoxModel",obj("backendNodeId"->owner),parent.session)("model")("content").asVector.map(_.asDouble)
      val size=evaluate(f,"({width:innerWidth,height:innerHeight})")
      val u=px/size("width").asDouble;val v=py/size("height").asDouble
      px=quad(0)+(quad(2)-quad(0))*u+(quad(6)-quad(0))*v
      py=quad(1)+(quad(3)-quad(1))*u+(quad(7)-quad(1))*v
      f=graph(parent.root)
    }
    (px,py)
  }
  private def rootRegion(graph:Map[String,Frame],r:Region):Region={
    val (x,y)=rootPoint(graph,r.frame,r.x,r.y)
    val (right,top)=rootPoint(graph,r.frame,r.x+r.width,r.y)
    val (left,bottom)=rootPoint(graph,r.frame,r.x,r.y+r.height)
    require(math.abs(top-y)<1&&math.abs(left-x)<1&&right>x&&bottom>y,"Rotated CAPTCHA frames require human interaction")
    Region(r.frame,x,y,right-x,bottom-y)
  }
  private def token(graph:Map[String,Frame]):Boolean=graph.values.exists(f=>evaluate(f,"[...document.querySelectorAll('[name=\"g-recaptcha-response\"]')].some(e=>!!e.value)")==bool(true))
  private def click(x:Double,y:Double):Unit={
    require(x.isFinite&&y.isFinite&&x>=0&&y>=0,"CAPTCHA click outside viewport")
    send("Input.dispatchMouseEvent",obj("type"->str("mouseMoved"),"x"->num(x),"y"->num(y)))
    val release=obj("type"->str("mouseReleased"),"x"->num(x),"y"->num(y),"button"->str("left"),"clickCount"->num(1))
    try send("Input.dispatchMouseEvent",obj("type"->str("mousePressed"),"x"->num(x),"y"->num(y),"button"->str("left"),"clickCount"->num(1)))
    finally send("Input.dispatchMouseEvent",release)
    Thread.sleep(250)
  }
  /** Bounded provider attempts. A stale/moved challenge is discarded before applying coordinates. */
  def solve(solver:CoordinateCaptchaSolver,maxRounds:Int=8):Task[Boolean]=Task {
    require(maxRounds>0&&maxRounds<=20,"Invalid CAPTCHA round limit")
    try {
      send("Page.bringToFront")
      var graph=frames();progress("frames_discovered="+graph.size)
      val initialRoot=graph.values.find(_.parent.isEmpty).get
      require(evaluate(initialRoot,"devicePixelRatio").asDouble==1,"CAPTCHA solver requires device scale factor 1")
      if(token(graph))true
      else {
        region(graph,"anchor").foreach{r=>progress("checkbox_found");val point=rootPoint(graph,r.frame,r.x+28,r.y+r.height/2);click(point._1,point._2)}
        Thread.sleep(3000)
        var rounds=0;var solved=false
        while(rounds<maxRounds && !solved){
          graph=frames();solved=token(graph)
          if(!solved){
            rounds+=1
            region(graph,"bframe") match {
              case None=>progress("challenge_not_visible");Thread.sleep(1000)
              case Some(local)=>
                val r=rootRegion(graph,local);progress("challenge_found")
                val viewport=evaluate(graph.values.find(_.parent.isEmpty).get,"({x:scrollX,y:scrollY,width:innerWidth,height:innerHeight})")
                require(r.x>=0&&r.y>=0&&r.x+r.width<=viewport("width").asDouble+1&&r.y+r.height<=viewport("height").asDouble+1,"CAPTCHA challenge must be visible in the viewport")
                val imageScale=math.min(1.0,500.0/math.max(r.width,r.height))
                val image=send("Page.captureScreenshot",obj("format"->str("png"),"clip"->obj("x"->num(r.x+viewport("x").asDouble),"y"->num(r.y+viewport("y").asDouble),"width"->num(r.width),"height"->num(r.height),"scale"->num(imageScale))))("data").asString
                progress("challenge_captured")
                val points=solver.classifyCoordinates(image,"This is a reCAPTCHA. Follow the instruction shown at the top of the image and click the matching squares.").sync()
                progress("provider_answer_received")
                require(points.size<=64&&points.forall((x,y)=>x.isFinite&&y.isFinite&&x>=0&&y>=0&&x<r.width*imageScale&&y<r.height*imageScale),"Invalid CAPTCHA coordinates")
                val fresh=frames()
                val current=region(fresh,"bframe").map(rootRegion(fresh,_))
                val unchanged=current.exists(n=>n.frame.id==r.frame.id&&n.frame.context==r.frame.context&&math.abs(n.x-r.x)<1&&math.abs(n.y-r.y)<1&&math.abs(n.width-r.width)<1&&math.abs(n.height-r.height)<1)
                progress(if(unchanged)"challenge_current"else"challenge_changed");if(unchanged){points.foreach((x,y)=>click(r.x+x/imageScale,r.y+y/imageScale));Thread.sleep(600);click(r.x+r.width-60*r.width/local.width,r.y+r.height-30*r.height/local.height);Thread.sleep(2500)}
            }
          }
        }
        solved||token(frames())
      }
    } finally {
      attached.values.foreach(s=>try send("Target.detachFromTarget",obj("sessionId"->str(s)))catch{case _:Exception=>()});attached.clear()
    }
  }
}
object FrameCaptcha {
  def forTab(tab:BrowserTab):FrameCaptcha=new FrameCaptcha((m,p,s)=>tab.host.send(m,p,s),Some(tab.sessionId))
}
