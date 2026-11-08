package robobrowser.mirror

import fabric.*
import java.nio.file.Files
import org.scalatest.funsuite.AnyFunSuite

class ExecutionProfilesSpec extends AnyFunSuite {
  test("filesystem lease prevents concurrent owners and reopens atomically saved data") {
    val root=Files.createTempDirectory("rb-profile-spec-")
    try {
      val one=new ExecutionProfiles(root);val two=new ExecutionProfiles(root)
      val lease=one.acquire("workspace")
      intercept[IllegalArgumentException](two.acquire("workspace"))
      val data=obj("id"->str("123"),"cookies"->arr())
      lease.save(data);lease.close()
      val reopened=two.acquire("workspace")
      try assert(reopened.data==data)finally reopened.close()
      intercept[IllegalArgumentException](one.acquire("../escape"))
      intercept[IllegalArgumentException](one.acquire("a/b"))
    }finally {val paths=Files.walk(root);try paths.sorted(java.util.Comparator.reverseOrder()).forEach(Files.deleteIfExists(_))finally paths.close()}
  }
  test("persistent snapshots preserve expiry, HttpOnly and host-only scope without session cookies") {
    val first=new ExecutionCookies(()=>"https://a.example.com/")
    first.put(java.net.URI.create("https://a.example.com/"),java.util.Map.of("Set-Cookie",java.util.List.of("login=secret; Secure; HttpOnly; Path=/; Max-Age=3600","session=temporary; Path=/")))
    val restored=new ExecutionCookies(()=>"https://a.example.com/");restored.restore(first.snapshot)
    assert(restored.header("https://a.example.com/")=="login=secret")
    assert(restored.header("https://b.example.com/").isEmpty)
    assert(restored.scriptRead("https://a.example.com/").isEmpty)
  }
}
