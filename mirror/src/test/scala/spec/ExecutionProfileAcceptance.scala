package spec

import fabric.*
import fabric.io.JsonFormatter
import robobrowser.BrowserHost
import robobrowser.mirror.{ExecutionServer}
import java.nio.file.{Files,Path}
import java.net.URI

/** Real frontend storage, resource cookies, close/reopen, and service restart on the same address. */
object ExecutionProfileAcceptance {
  def main(args:Array[String]):Unit={
    val root=Files.createTempDirectory("rb-execution-profiles-")
    val host=BrowserHost.start().sync();val front=BrowserHost.start().sync()
    val token=java.util.UUID.randomUUID().toString
    var server=new ExecutionServer(0,token,Some(root))
    val checks=scala.collection.mutable.ListBuffer.empty[String]
    val out=Path.of("target/execution-profiles");Files.createDirectories(out)
    def check(name:String)(test: => Boolean):Unit={require(test,name);checks+=name;println("PASS "+name)}
    def until(name:String)(test: => Boolean):Unit={val end=System.nanoTime()+30000000000L;var ok=false;while(!ok&&System.nanoTime()<end){ok=scala.util.Try(test).getOrElse(false);if(!ok)Thread.sleep(100)};check(name)(ok)}
    try {
      server.start();val port=URI.create(server.address).getPort
      val page=front.open(server.address+"/execution#"+token).sync()
      def js(code:String)=page.evaluate(code).sync()
      def local(code:String)=js("local('eval',{code:"+JsonFormatter.Compact(str(code))+"})")
      def open(name:String):Unit={js("$('profile').value="+JsonFormatter.Compact(str(name))+";open('')");until("Profile "+name+" connected")(js("agentEpoch!==null").asBoolean&&local("!!window.executionFixture").asBoolean)}
      until("Profile shell ready")(js("typeof api==='function'").asBoolean)
      open("alpha");val alpha=js("session.id");val origin=local("location.origin");val oldEpoch=js("agentEpoch")
      local("localStorage.setItem('profile-marker','alpha');document.cookie='persistent=alpha; Path=/; Max-Age=3600';document.cookie='temporary=gone; Path=/';true")
      check("An active profile cannot be opened twice")(js("api({op:'open',profile:'alpha',mode:'local'}).then(()=>false,()=>true)").asBoolean)
      js("close()");open("beta")
      check("A different profile has separate origin and storage")(local("location.origin")!=origin&&local("localStorage.getItem('profile-marker')")==Null&& !local("document.cookie").asString.contains("persistent="))
      local("localStorage.setItem('profile-marker','beta');document.cookie='persistent=beta; Path=/; Max-Age=3600';true")
      js("close()");open("alpha")
      check("Reopening keeps the profile origin")(js("session.id")==alpha&&local("location.origin")==origin)
      check("Reopening restores persistent cookie and local storage")(local("document.cookie").asString.contains("persistent=alpha")&&local("localStorage.getItem('profile-marker')").asString=="alpha")
      check("Closing expires nonpersistent session cookies")(!local("document.cookie").asString.contains("temporary="))
      check("Reopened profile has a fresh command epoch")(js("agentEpoch")!=oldEpoch)
      val beforeRestart=js("agentEpoch")
      server.stop();server=new ExecutionServer(port,token,Some(root));server.start()
      page.send("Page.reload").sync()
      until("Shell reconnects saved profile after service restart")(js("session?.id")==alpha&&js("agentEpoch!==null").asBoolean&&local("!!window.executionFixture").asBoolean)
      check("Service restart preserves persistent cookies and frontend storage")(local("document.cookie").asString.contains("persistent=alpha")&&local("localStorage.getItem('profile-marker')").asString=="alpha")
      check("Service restart rejects an old document epoch")(js("api({op:'agent',id:session.id,epoch:"+JsonFormatter.Compact(beforeRestart)+",requestId:'before-restart',command:{op:'eval',code:'true'}}).then(()=>false,()=>true)").asBoolean)
      val permissions=Files.getPosixFilePermissions(root.resolve("alpha.json"))
      check("Profile cookie file is private to its owner")(permissions==java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"))
      js("close()");open("beta")
      check("Other profile remains independent after restart")(local("document.cookie").asString.contains("persistent=beta")&&local("localStorage.getItem('profile-marker')").asString=="beta")
      js("close()")
      Files.writeString(out.resolve("result.json"),JsonFormatter.Default(obj("status"->str("passed"),"checks"->arr(checks.toList.map(str)*))))
    }catch{case e:Throwable=>Files.writeString(out.resolve("result.json"),JsonFormatter.Default(obj("status"->str("failed"),"checks"->arr(checks.toList.map(str)*),"error"->str(e.toString))));throw e}
    finally {
      server.stop();front.stop().sync();host.stop().sync()
      val paths=Files.walk(root);try paths.sorted(java.util.Comparator.reverseOrder()).forEach(Files.deleteIfExists(_))finally paths.close()
    }
  }
}
