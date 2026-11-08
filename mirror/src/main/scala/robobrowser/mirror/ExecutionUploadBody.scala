package robobrowser.mirror

import java.io.{IOException,InputStream}
import okhttp3.{MediaType,RequestBody}
import okio.BufferedSink

/** Single-use bounded upload; never buffers an entire multipart request or retries its body. */
private[mirror] final class ExecutionUploadBody(input:InputStream,length:Long,limit:Long) extends RequestBody {
  require(limit>0&&length>= -1,"Invalid upload bounds")
  require(length<=limit,"Upload exceeds configured request limit")
  override def contentType():MediaType=null
  override def contentLength():Long=length
  override def isOneShot():Boolean=true
  override def writeTo(sink:BufferedSink):Unit={
    val buffer=new Array[Byte](65536);var total=0L
    try {
      var count=input.read(buffer,0,math.min(buffer.length.toLong,limit+1).toInt)
      while(count>=0){
        total+=count
        if(total>limit)throw new IOException("Upload exceeds configured request limit")
        if(length>=0&&total>length)throw new IOException("Upload exceeds declared length")
        if(count>0)sink.write(buffer,0,count)
        count=input.read(buffer,0,math.min(buffer.length.toLong,limit-total+1).toInt)
      }
      if(length>=0&&total!=length)throw new IOException("Incomplete upload body")
    }finally input.close()
  }
}
