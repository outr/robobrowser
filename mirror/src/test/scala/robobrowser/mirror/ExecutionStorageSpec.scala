package robobrowser.mirror

import fabric.*
import org.scalatest.funsuite.AnyFunSuite

class ExecutionStorageSpec extends AnyFunSuite {
  private def write(revision:Long,value:String)=obj("revision"->num(revision),"namespace"->str("main"),"local"->obj("key"->str(value)),"session"->obj(),"databases"->arr())
  test("checkpoints isolate origins and reject stale writers without replacing data") {
    var disk:Json=obj();val s=new ExecutionStorage(obj(),v=>disk=v)
    assert(s.write("https://a",write(0,"one"))("revision").asLong==1)
    assert(s.read("https://b")("local")==obj())
    assert(s.write("https://a",write(0,"one"))("revision").asLong==1)
    intercept[IllegalArgumentException]{s.write("https://a",write(0,"stale"))}
    assert(new ExecutionStorage(disk,_=>()) .read("https://a")("local")("key")==str("one"))
  }
  test("failed persistence never acknowledges or replaces the previous checkpoint") {
    val s=new ExecutionStorage(obj(),_=>throw new java.io.IOException("fixture"))
    intercept[java.io.IOException]{s.write("https://a",write(0,"one"))}
    assert(s.read("https://a")("revision").asLong==0)
  }
  test("storage requests enforce string values and namespaces") {
    val s=new ExecutionStorage(obj(),_=>())
    intercept[IllegalArgumentException]{s.write("https://a",obj("revision"->num(0),"namespace"->str("../invalid")))}
  }
}
