package robobrowser.mirror

import fabric.*
import org.scalatest.funsuite.AnyFunSuite
import robobrowser.BrowserHost
import java.nio.file.Files
import java.util.concurrent.{Executors,TimeUnit}

class BrowserHostSpec extends AnyFunSuite {
  test("dedicated profiles are exclusive and can be reopened after host shutdown") {
    val profile=Files.createTempDirectory("robobrowser-profile-test-")
    val first=BrowserHost.start(profile=Some(profile)).sync()
    try intercept[IllegalStateException]{BrowserHost.start(profile=Some(profile)).sync()}
    finally first.stop().sync()
    val second=BrowserHost.start(profile=Some(profile)).sync()
    try assert(first.generation!=second.generation)finally second.stop().sync()
    val paths=Files.walk(profile);try paths.sorted(java.util.Comparator.reverseOrder()).forEach(p=>Files.deleteIfExists(p))finally paths.close()
  }
  test("a new explicit host cleans up only its verified orphan after an abrupt owner exit") {
    val profile=Files.createTempDirectory("robobrowser-orphan-test-")
    val child=new ProcessBuilder(java.nio.file.Path.of(System.getProperty("java.home"),"bin","java").toString,"-cp",System.getProperty("java.class.path"),"spec.MirrorOrphanHost",profile.toString).redirectErrorStream(true).redirectOutput(profile.resolve("owner.log").toFile).start()
    var next:Option[BrowserHost]=None
    var old:Option[ProcessHandle]=None
    try {
      val record=profile.resolve(".robobrowser-process.json");val deadline=System.nanoTime()+15000000000L
      while(!Files.exists(record)&&System.nanoTime()<deadline)Thread.sleep(25)
      assert(Files.exists(record),"Isolated child host did not record its process")
      val saved=fabric.io.JsonParser(Files.readString(record));old=Some(ProcessHandle.of(saved("pid").asLong).get())
      child.destroyForcibly();assert(child.waitFor(5,TimeUnit.SECONDS));assert(old.get.isAlive)
      next=Some(BrowserHost.start(profile=Some(profile)).sync())
      assert(!old.get.isAlive,"Verified orphan was not stopped")
      assert(next.get.open().sync().evaluate("1+1").sync().asInt==2)
    }finally {
      if(child.isAlive)child.destroyForcibly()
      next.foreach(_.stop().sync());old.filter(_.isAlive).foreach(_.destroyForcibly())
      val paths=Files.walk(profile);try paths.sorted(java.util.Comparator.reverseOrder()).forEach(p=>Files.deleteIfExists(p))finally paths.close()
    }
  }
  test("context disposal preserves sibling contexts and a host crash fails pending CDP work") {
    val host=BrowserHost.start().sync();val pool=Executors.newVirtualThreadPerTaskExecutor()
    try {
      val a=host.createContext().sync();val b=host.createContext().sync()
      val first=host.open(context=Some(a)).sync();val second=host.open(context=Some(b)).sync()
      assert(first.contextId.contains(a)&&second.contextId.contains(b))
      host.closeContext(a).sync();assert(host.tabs.sync().exists(_.id==second.id));assert(!host.tabs.sync().exists(_.id==first.id))
      val pending=pool.submit(new java.util.concurrent.Callable[Json]{def call():Json=second.evaluate("new Promise(()=>{})").sync()})
      Thread.sleep(100)
      try host.send("Browser.close").sync()catch{case _:Exception=>()}
      intercept[java.util.concurrent.ExecutionException]{pending.get(3,TimeUnit.SECONDS)}
      intercept[IllegalStateException]{second.evaluate("1").sync()}
    }finally{pool.shutdownNow();host.stop().sync()}
  }
}
