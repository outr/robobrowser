package spec

import robobrowser.mirror.ExecutionServer
import java.nio.file.{Files,Path}

object ExecutionDemo {
  def main(args:Array[String]):Unit={
    val token=args.lift(1).map(p=>Files.readString(Path.of(p)).trim).getOrElse(java.util.UUID.randomUUID().toString)
    require(token.matches("[a-zA-Z0-9-]{32,128}"),"Invalid demo capability")
    val server=new ExecutionServer(args.headOption.map(_.toInt).getOrElse(8898),token,args.lift(1).map(p=>Path.of(p).toAbsolutePath.getParent.resolve("profiles")))
    Runtime.getRuntime.addShutdownHook(new Thread(()=>server.stop()))
    server.start()
    args.lift(2).foreach(p=>Files.writeString(Path.of(p),ProcessHandle.current().pid().toString))
    println(s"RoboBrowser: ${server.address}/#${server.token}")
    new java.util.concurrent.CountDownLatch(1).await()
  }
}
