package robobrowser.mirror

import java.net.URI
import org.scalatest.funsuite.AnyFunSuite

class ExecutionCookiesSpec extends AnyFunSuite {
  def response(jar:ExecutionCookies,url:String,lines:String*):Unit=
    jar.put(URI.create(url),java.util.Map.of("Set-Cookie",java.util.List.of(lines*)))
  test("host-only, domain, path and expiry rules select the original destination") {
    val jar=new ExecutionCookies(()=>"https://a.example.com/")
    response(jar,"https://a.example.com/account/login","host=1; Path=/","shared=2; Domain=example.com; Path=/","scoped=3; Path=/account")
    assert(jar.header("https://a.example.com/account/me")=="scoped=3; host=1; shared=2")
    assert(jar.header("https://b.example.com/")=="shared=2")
    assert(jar.header("https://unrelated.example/").isEmpty)
    assert(!jar.header("https://a.example.com/accounting").contains("scoped"))
    response(jar,"https://a.example.com/","host=; Path=/; Max-Age=0")
    assert(!jar.header("https://a.example.com/").contains("host="))
  }
  test("scripts see server cookies but cannot read, replace or delete HttpOnly cookies") {
    val jar=new ExecutionCookies(()=>"https://example.com/")
    response(jar,"https://example.com/","secret=original; Path=/; HttpOnly; Secure","csrf=visible; Path=/")
    assert(jar.scriptRead("https://example.com/")=="csrf=visible")
    jar.scriptWrite("https://example.com/","secret=changed; Path=/")
    jar.scriptWrite("https://example.com/","secret=; Path=/; Max-Age=0")
    jar.scriptWrite("https://example.com/","fake=1; HttpOnly")
    jar.scriptWrite("https://example.com/","client=sent; Path=/")
    assert(jar.header("https://example.com/")=="secret=original; csrf=visible; client=sent")
  }
  test("domain injection, insecure SameSite None and cookie prefix violations are rejected") {
    val jar=new ExecutionCookies(()=>"https://example.com/")
    response(jar,"https://example.com/","bad=1; Domain=com","other=2; Domain=other.com","none=3; SameSite=None","__Host-bad=4; Secure; Domain=example.com; Path=/","__Secure-bad=5")
    assert(jar.header("https://example.com/").isEmpty)
    response(jar,"https://example.com/","__Host-good=6; Secure; Path=/","strict=7; Secure; SameSite=Strict")
    assert(jar.header("https://example.com/").contains("__Host-good=6"))
    assert(jar.header("http://example.com/").isEmpty)
  }
  test("cross-site subresources and POST do not receive Lax or Strict cookies") {
    val jar=new ExecutionCookies(()=>"https://other.example/")
    response(jar,"https://example.com/","lax=1; SameSite=Lax","strict=2; SameSite=Strict","none=3; SameSite=None; Secure")
    assert(jar.header("https://example.com/")=="none=3")
    assert(jar.header("https://example.com/","POST",true)=="none=3")
    assert(jar.header("https://example.com/","GET",true)=="lax=1; none=3")
  }
  test("two jars never share authenticated state") {
    val one=new ExecutionCookies(()=>"https://example.com/")
    val two=new ExecutionCookies(()=>"https://example.com/")
    response(one,"https://example.com/","login=one; HttpOnly; Secure")
    assert(one.header("https://example.com/").nonEmpty)
    assert(two.header("https://example.com/").isEmpty)
  }
}
