package robobrowser.mirror

import fabric.*
import fabric.io.{JsonFormatter,JsonParser}
import java.nio.channels.FileChannel
import java.nio.file.{Files,Path,LinkOption,StandardCopyOption,StandardOpenOption}
import java.nio.file.attribute.PosixFilePermissions
import java.nio.charset.StandardCharsets.UTF_8

/** Application-selected storage. Each active profile has one process-wide filesystem lease. */
private[mirror] final class ExecutionProfiles(root:Path) {
  private val directory=root.toAbsolutePath.normalize()
  Files.createDirectories(directory)
  require(!Files.isSymbolicLink(directory),"Profile directory cannot be a symlink")
  Files.setPosixFilePermissions(directory,PosixFilePermissions.fromString("rwx------"))
  def acquire(name:String):Lease={
    require(name.matches("[a-zA-Z0-9_-]{1,64}"),"Profile name must contain 1-64 letters, digits, underscores or hyphens")
    val lockPath=directory.resolve(name+".lock")
    val channel=FileChannel.open(lockPath,StandardOpenOption.CREATE,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS)
    Files.setPosixFilePermissions(lockPath,PosixFilePermissions.fromString("rw-------"))
    try {
      val lock=try channel.tryLock()catch{case _:java.nio.channels.OverlappingFileLockException=>null}
      require(lock!=null,"Profile is already open")
      new Lease(name,channel)
    }catch{case e:Throwable=>channel.close();throw e}
  }
  final class Lease private[ExecutionProfiles](val name:String,channel:FileChannel) extends AutoCloseable {
    private val path=directory.resolve(name+".json")
    private var closed=false
    val data:Json=if(Files.exists(path,LinkOption.NOFOLLOW_LINKS)){
      require(!Files.isSymbolicLink(path)&&Files.size(path)<=16*1024*1024,"Invalid profile file")
      JsonParser(Files.readString(path))
    }else obj("id"->str(java.util.UUID.randomUUID().toString.replace("-","")),"origins"->obj(),"cookies"->arr())
    def save(value:Json):Unit=synchronized {
      require(!closed,"Profile lease closed")
      val bytes=JsonFormatter.Compact(value).getBytes(UTF_8)
      require(bytes.length<=16*1024*1024,"Profile storage limit exceeded")
      val temp=Files.createTempFile(directory,name+"-",".tmp",PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))
      try {
        val output=FileChannel.open(temp,StandardOpenOption.WRITE)
        try {val buffer=java.nio.ByteBuffer.wrap(bytes);while(buffer.hasRemaining)output.write(buffer);output.force(true)}finally output.close()
        Files.move(temp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING)
      }finally Files.deleteIfExists(temp)
    }
    override def close():Unit=synchronized {if(!closed){closed=true;channel.close()}}
  }
}
