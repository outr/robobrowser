package robobrowser.mirror
import java.io.{ByteArrayInputStream,IOException}
import org.scalatest.funsuite.AnyFunSuite
class ExecutionUploadBodySpec extends AnyFunSuite {
  test("upload streams exact bytes in bounded reads and cannot be retried") {
    val data=Array.tabulate[Byte](1024*1024)(i=>i.toByte);var largest=0;var closed=false
    val input=new ByteArrayInputStream(data){override def read(b:Array[Byte],o:Int,n:Int):Int={largest=largest.max(n);super.read(b,o,n)};override def close():Unit={closed=true;super.close()}}
    val body=new ExecutionUploadBody(input,data.length,data.length);val sink=new okio.Buffer()
    body.writeTo(sink);assert(sink.readByteArray().sameElements(data));assert(largest<=65536&&closed&&body.isOneShot())
  }
  test("declared oversize is rejected before forwarding") {
    intercept[IllegalArgumentException](new ExecutionUploadBody(new ByteArrayInputStream(Array.emptyByteArray),100,99))
  }
  test("unknown-length oversize and truncated declared bodies fail and close their inputs") {
    for(length<-List(-1L,4L)){
      var closed=false
      val input=new ByteArrayInputStream(Array[Byte](1,2,3)){override def close():Unit={closed=true}}
      val body=new ExecutionUploadBody(input,length,if(length<0)2 else 4)
      intercept[IOException](body.writeTo(new okio.Buffer()));assert(closed)
    }
  }
}
