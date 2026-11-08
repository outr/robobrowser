package robobrowser.mirror

import java.net.{CookieManager,URI}
import fabric.*
import okhttp3.{Cookie,HttpUrl}
import scala.collection.mutable
import scala.jdk.CollectionConverters.*

/** Cookies belong to original origins, never to the proxy's localhost names. */
private[mirror] final class ExecutionCookies(topURL:()=>String,onChange:()=>Unit=()=>()) extends CookieManager {
  private val values=mutable.LinkedHashMap.empty[(String,String,String),Cookie]
  private def key(c:Cookie)=(c.domain(),c.path(),c.name())
  private def clean():Unit=values.filterInPlace((_,c)=>c.expiresAt()>System.currentTimeMillis())
  private def site(u:HttpUrl)=(u.scheme(),Option(u.topPrivateDomain()).getOrElse(u.host()))
  private def matching(url:HttpUrl):List[Cookie]={clean();values.values.filter(_.matches(url)).toList.sortBy(c=> -c.path().length)}
  private def putCookie(url:HttpUrl,line:String,script:Boolean):Unit={
    if(line.length>4096)return
    val fields=line.split(';').drop(1).map(_.trim.toLowerCase(java.util.Locale.ROOT))
    if(fields.exists(_=="partitioned"))return // No false unpartitioned substitute for CHIPS.
    Option(Cookie.parse(url,line)).foreach {c=>
      val name=c.name()
      val valid=(!c.secure()||url.isHttps()) &&
        (!Option(c.sameSite()).exists(_.equalsIgnoreCase("None"))||c.secure()) &&
        (!name.startsWith("__Secure-")||c.secure()) &&
        (!name.startsWith("__Host-")||(c.secure()&&c.hostOnly()&&c.path()=="/")) &&
        (!name.startsWith("__Http-")||(c.secure()&&c.httpOnly())) &&
        (!name.startsWith("__Host-Http-")||(c.secure()&&c.httpOnly()&&c.hostOnly()&&c.path()=="/"))
      clean()
      val protectedCookie=script && (c.httpOnly()||values.get(key(c)).exists(_.httpOnly()))
      val secureOverlay= !url.isHttps() && values.values.exists(old=>old.secure()&&old.name()==c.name()&&
        (old.domain()==c.domain()||old.domain().endsWith("."+c.domain())||c.domain().endsWith("."+old.domain()))&&
        (old.path()==c.path()||c.path().startsWith(old.path().stripSuffix("/")+"/")))
      if(valid&& !protectedCookie&& !secureOverlay){
        if(c.expiresAt()<=System.currentTimeMillis())values.remove(key(c))
        else if(values.contains(key(c))||values.size<3000)values.put(key(c),c)
      }
    }
  }
  def scriptRead(url:String):String=synchronized {
    matching(HttpUrl.get(url)).filterNot(_.httpOnly()).map(c=>s"${c.name()}=${c.value()}").mkString("; ")
  }
  def scriptWrite(url:String,line:String):Unit={synchronized {putCookie(HttpUrl.get(url),line,true)};onChange()}
  def header(url:String,method:String="GET",navigation:Boolean=false):String=synchronized {
    val target=HttpUrl.get(url)
    val sameSite=scala.util.Try(site(HttpUrl.get(topURL()))==site(target)).getOrElse(false)
    matching(target).filter {c=>
      Option(c.sameSite()).map(_.toLowerCase(java.util.Locale.ROOT)).getOrElse("lax") match {
        case "none"=>true
        case "strict"=>sameSite
        case _=>sameSite||(navigation&&Set("GET","HEAD","OPTIONS","TRACE")(method))
      }
    }.map(c=>s"${c.name()}=${c.value()}").mkString("; ")
  }
  def snapshot:Json=synchronized {
    clean();arr(values.values.filter(_.persistent()).map(c=>obj("url"->str((if(c.secure())"https://"else"http://")+c.domain()+"/"),"cookie"->str(c.toString))).toList*)
  }
  def restore(data:Json):Unit=synchronized {
    require(data.asVector.size<=3000,"Profile cookie budget exceeded")
    data.asVector.foreach(c=>putCookie(HttpUrl.get(c("url").asString),c("cookie").asString,false))
  }
  override def get(uri:URI,headers:java.util.Map[String,java.util.List[String]]):java.util.Map[String,java.util.List[String]]={
    val value=header(uri.toString)
    if(value.isEmpty)java.util.Map.of()else java.util.Map.of("Cookie",java.util.List.of(value))
  }
  override def put(uri:URI,headers:java.util.Map[String,java.util.List[String]]):Unit={
    val url=HttpUrl.get(uri.toString)
    val lines=headers.asScala.filter((k,_)=>k!=null&&k.equalsIgnoreCase("set-cookie")).values.toList.flatMap(_.asScala)
    synchronized {lines.foreach(putCookie(url,_,false))}
    if(lines.nonEmpty)onChange()
  }
}
