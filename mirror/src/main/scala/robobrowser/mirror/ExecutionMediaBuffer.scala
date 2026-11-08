package robobrowser.mirror

import java.io.{IOException,InputStream}
import scala.collection.mutable.ArrayBuffer

private[mirror] object ExecutionMediaBuffer {
  /** A partial MP4 fragment must not wait for the HTTP server's buffer to fill. */
  def transfer(in:InputStream,out:java.io.OutputStream):Unit={
    val bytes=new Array[Byte](32768);var count=in.read(bytes)
    while(count>=0){if(count>0){out.write(bytes,0,count);out.flush()};count=in.read(bytes)}
  }
}

/** Append-only bounded conversion output. Readers use byte offsets, so completion can compact storage. */
private[mirror] final class ExecutionMediaBuffer(reserve:Int=>Unit,release:Int=>Unit) {
  private val chunks=ArrayBuffer.empty[Array[Byte]]
  private var length=0
  private var finished=false
  private var failure:Throwable=null
  private var disposed=false
  private var readers=0
  def size:Int=synchronized(length)
  def append(bytes:Array[Byte]):Unit=synchronized {
    require(!finished&&failure==null&& !disposed,"Media output closed")
    require(length.toLong+bytes.length<=16L*1024*1024,"Media exceeds 16 MiB")
    reserve(bytes.length);chunks+=bytes;length+=bytes.length;notifyAll()
  }
  def complete():Unit=synchronized {
    require(failure==null&& !disposed,"Media output closed")
    val all=new Array[Byte](length);var offset=0
    chunks.foreach(b=>{System.arraycopy(b,0,all,offset,b.length);offset+=b.length})
    chunks.clear();chunks+=all;finished=true;notifyAll()
  }
  def fail(error:Throwable):Unit=synchronized {failure=error;notifyAll()}
  def bytes:Array[Byte]=synchronized {
    if(failure!=null)throw new IOException("Media conversion failed",failure)
    require(finished&& !disposed,"Media conversion not ready");chunks.head
  }
  def open():InputStream=synchronized {
    require(!disposed&&readers<8,"Media reader budget exceeded or output closed");readers+=1
    new InputStream {
      private var offset=0
      private var closed=false
      override def read():Int={val b=new Array[Byte](1);if(read(b,0,1)<0)-1 else b(0)&255}
      override def read(target:Array[Byte],start:Int,count:Int):Int=ExecutionMediaBuffer.this.synchronized {
        java.util.Objects.checkFromIndexSize(start,count,target.length)
        if(count==0)return 0
        val deadline=System.nanoTime()+75000000000L
        while(offset==length&& !finished&&failure==null&& !disposed&& !closed){
          if(System.nanoTime()>=deadline)throw new IOException("Media output timed out")
          ExecutionMediaBuffer.this.wait(1000)
        }
        if(closed||disposed)throw new IOException("Media reader closed")
        if(failure!=null)throw new IOException("Media conversion failed",failure)
        if(offset==length)return -1
        var skip=offset;var index=0
        while(skip>=chunks(index).length){skip-=chunks(index).length;index+=1}
        val source=chunks(index);val n=math.min(count,source.length-skip)
        System.arraycopy(source,skip,target,start,n);offset+=n;n
      }
      override def close():Unit=ExecutionMediaBuffer.this.synchronized {
        if(!closed){closed=true;readers-=1;ExecutionMediaBuffer.this.notifyAll()}
      }
    }
  }
  def dispose():Unit=synchronized {
    if(!disposed){disposed=true;release(length);chunks.clear();length=0;notifyAll()}
  }
}
