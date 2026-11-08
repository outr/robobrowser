package spec

import fabric.*
import fabric.io.{JsonFormatter,JsonParser}
import robobrowser.{BrowserHost,BrowserConfig,FrameCaptcha,AntiCaptchaCoordinateSolver}
import java.nio.file.{Files,Path}
import java.nio.file.attribute.PosixFilePermissions

/** Opt-in real-account probe. Never prints credentials, page text, URLs, or cookie values. */
object FacebookLoginAcceptance {
  def main(args:Array[String]):Unit={
    val home=Path.of(System.getProperty("user.home"))
    val credentials=home.resolve(".config/robobrowser/test-login.json")
    val root=home.resolve(".local/state/robobrowser/facebook-login-acceptance")
    Files.createDirectories(root);Files.setPosixFilePermissions(root,PosixFilePermissions.fromString("rwx------"))
    val permitted=PosixFilePermissions.fromString("rw-------")
    require(Files.getPosixFilePermissions(credentials).equals(permitted),"Credential file must have mode 600")
    require(Files.size(credentials)<=16384,"Credential file exceeds limit")
    val login=try JsonParser(Files.readString(credentials)) catch{case _:Exception=>throw new IllegalArgumentException("Invalid credential JSON")}
    require(login.get("origin").contains(str("https://www.facebook.com")),"Unexpected credential origin")
    require(List("username","password").forall(k=>login.get(k).exists(v=>v.isInstanceOf[Str]&&v.asString.nonEmpty)),"Credential fields missing")
    val host=BrowserHost.start(BrowserConfig(passwordManager=false,forceDeviceScaleFactor=Some(1),windowSize=Some(1200->1000)),profile=Some(root.resolve("profile"))).sync()
    def report(state:String):Unit={
      Files.writeString(root.resolve("status.json"),JsonFormatter.Default(obj("state"->str(state),"time"->num(System.currentTimeMillis()))))
      println("LOGIN_STATE "+state)
    }
    try {
      val tab=host.open("https://www.facebook.com/").sync()
      val deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(40)
      while(!tab.evaluate("location.origin==='https://www.facebook.com'&&document.readyState==='complete'").sync().asBoolean&&System.nanoTime()<deadline)Thread.sleep(250)
      def state():String={
        val page=tab.evaluate("""(()=>{const t=document.body?.innerText?.toLowerCase()||'';return {origin:location.origin,login:!!document.querySelector('input[type=password]'),challenge:/two.factor|authentication code|authenticator|check your notifications|approve.*log.?in|enter.*code/.test(t),captcha:!!document.querySelector('iframe[src*=captcha],iframe[src*=recaptcha]')||/confirm you.re human/.test(t),rejected:/incorrect password|password.*incorrect|wrong password|invalid.*password/.test(t),checkpoint:/checkpoint|two_step_verification/.test(location.pathname)}})()""").sync()
        val authenticated=tab.send("Network.getCookies",obj("urls"->arr(str("https://www.facebook.com/")))).sync()("cookies").asVector.exists(c=>c("name").asString=="c_user" && c("value").asString.nonEmpty)
        if(page("origin").asString!="https://www.facebook.com")"unexpected_origin"
        else if(page("captcha").asBoolean)"human_verification_required"
        else if(page("challenge").asBoolean||page("checkpoint").asBoolean)"verification_required"
        else if(page("rejected").asBoolean)"credentials_rejected"
        else if(authenticated && !page("login").asBoolean)"authenticated"
        else if(page("login").asBoolean)"login_form"
        else "loading_or_unclassified"
      }
      var initial=state();val formDeadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(20)
      while(initial=="loading_or_unclassified"&&System.nanoTime()<formDeadline){Thread.sleep(250);initial=state()}
      if(initial=="login_form") {
        val frame=tab.send("Page.getFrameTree").sync()("frameTree")("frame")("id")
        val context=tab.send("Page.createIsolatedWorld",obj("frameId"->frame,"worldName"->str("robobrowser-login-probe"))).sync()("executionContextId")
        val result=tab.send("Runtime.callFunctionOn",obj("executionContextId"->context,"returnByValue"->bool(true),"arguments"->arr(obj("value"->login("username")),obj("value"->login("password"))),"functionDeclaration"->str("""function(username,password){
          if(location.origin!=='https://www.facebook.com')return 'origin_refused';
          const u=document.querySelector('input[name=email]'),p=document.querySelector('input[name=pass][type=password]');
          const controls=p?.form?.querySelectorAll('button[type=submit],input[type=submit]');const b=controls?.length===1?controls[0]:p?.form?.querySelector('button[name=login]');
          if(!u)return 'username_field_unavailable';if(!p)return 'password_field_unavailable';if(!b)return 'submit_control_unavailable';if(!p.form||u.form!==p.form||b.form!==p.form)return 'form_relationship_unavailable';
          if(new URL(p.form.action,location.href).origin!==location.origin)return 'form_origin_refused';
          const set=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set;
          for(const [n,v] of [[u,username],[p,password]]){set.call(n,v);n.dispatchEvent(new Event('input',{bubbles:true}));n.dispatchEvent(new Event('change',{bubbles:true}));}
          b.click();return 'submitted';
        }"""))).sync()
        require(!result.get("exceptionDetails").isDefined,"Credential entry failed")
        val outcome=result("result").get("value").map(_.asString).getOrElse("entry_failed")
        report(outcome)
        if(outcome!="submitted"){
          val shape=tab.evaluate("[...document.querySelectorAll('input,button')].map(n=>({tag:n.localName,type:n.type,name:n.name,form:!!n.form}))").sync()
          Files.writeString(root.resolve("form-shape.json"),JsonFormatter.Default(shape))
          return
        }
      }
      var last="";val end=System.nanoTime()+java.util.concurrent.TimeUnit.MINUTES.toNanos(15)
      var verified=false;var captchaAttempted=false
      while(System.nanoTime()<end && !verified){
        Thread.sleep(2000)
        val current=try state() catch{case _:Exception=>"navigation_in_progress"}
        if(current!=last){report(current);last=current}
        if(current=="verification_required" && !captchaAttempted){
          sys.env.get("ROBOBROWSER_CAPTCHA_KEY").foreach{key=>
            captchaAttempted=true
            try{val solved=new FrameCaptcha((m,p,s)=>tab.host.send(m,p,s),Some(tab.sessionId),s=>println("CAPTCHA_PROGRESS "+s)).solve(new AntiCaptchaCoordinateSolver(key),4).sync();report(if(solved)"captcha_token_observed"else"captcha_not_confirmed")}
            catch{case _:Exception=>report("captcha_step_interrupted_or_failed")}
          }
        }
        if(current=="authenticated"){
          tab.navigate("https://www.facebook.com/").sync();Thread.sleep(5000)
          if(state()=="authenticated"){report("authenticated_after_reload");verified=true}
        }
        if(current=="credentials_rejected"||current=="unexpected_origin")return
      }
      if(!verified)report("verification_wait_expired")
    } catch {case _:Exception=>report("probe_failed_without_sensitive_details")}
    finally host.stop().sync()
  }
}
