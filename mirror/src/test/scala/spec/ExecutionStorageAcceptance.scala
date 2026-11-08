package spec

import fabric.*
import fabric.io.JsonFormatter
import robobrowser.{BrowserHost,BrowserNetworkPolicy}
import robobrowser.mirror.ExecutionServer
import com.sun.net.httpserver.HttpServer
import java.net.{InetSocketAddress,URI}
import java.nio.file.{Files,Path}

/** A new frontend and restarted gateway must restore real native storage before site scripts. */
object ExecutionStorageAcceptance {
  def main(args:Array[String]):Unit={
    val root=Files.createTempDirectory("rb-storage-profile-")
    val site=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0)
    val html="""<!doctype html><title>Storage proof</title><script>window.startup={local:localStorage.getItem('name'),session:sessionStorage.getItem('draft')};window.startupDatabases=indexedDB.databases();window.ready=true;</script><body>Storage proof</body>"""
    site.createContext("/",e=>{val bytes=html.getBytes(java.nio.charset.StandardCharsets.UTF_8);e.getResponseHeaders.set("Content-Type","text/html");e.sendResponseHeaders(200,bytes.length);e.getResponseBody.write(bytes);e.close()});site.start()
    val url=s"http://127.0.0.1:${site.getAddress.getPort}/"
    val policy=BrowserNetworkPolicy(allowPrivateNetworks=true,allowedPorts=List(site.getAddress.getPort))
    val token=java.util.UUID.randomUUID().toString
    var gateway=new ExecutionServer(0,token,Some(root),policy);gateway.start()
    val port=URI.create(gateway.address).getPort
    var front=BrowserHost.start().sync()
    var page=front.open(gateway.address+"/#"+token).sync()
    val checks=scala.collection.mutable.ListBuffer.empty[String]
    val flushTimings=scala.collection.mutable.ListBuffer.empty[Double]
    val out=Path.of("target/execution-storage");Files.createDirectories(out)
    def q(value:String)=JsonFormatter.Compact(str(value))
    def js(code:String)=page.evaluate(code).sync()
    def local(code:String)=js("local('eval',{code:"+q(code)+"})")
    def check(name:String)(f: => Boolean):Unit={require(f,name);checks+=name;println("PASS "+name)}
    def until(name:String)(f: => Boolean):Unit={val end=System.nanoTime()+30000000000L;var ok=false;while(!ok&&System.nanoTime()<end){ok=scala.util.Try(f).getOrElse(false);if(!ok)Thread.sleep(100)};check(name)(ok)}
    def open(profile:String,target:String=url):Unit={until("shell ready")(js("typeof open==='function'&&typeof session!=='undefined'").asBoolean);js("$('profile').value="+q(profile)+";open("+q(target)+")");until("page connected")(js("agentEpoch!==null").asBoolean&&local("window.ready===true").asBoolean)}
    def flush():Unit={flushTimings+=local("(()=>{const start=performance.now();return __rbStorageSync.flush().then(()=>performance.now()-start)})()").asDouble;check("storage acknowledged")(!local("__rbStorageSync.status().pending").asBoolean)}
    try {
      open("alpha")
      local("localStorage.setItem('name','alpha');localStorage.property='property-value';localStorage.setItem('removed','x');delete localStorage.removed;sessionStorage.setItem('draft','draft-value');true")
      local("""new Promise((resolve,reject)=>{const r=indexedDB.open('records',3);r.onupgradeneeded=()=>{const s=r.result.createObjectStore('items',{keyPath:'id',autoIncrement:true});s.createIndex('label','label',{unique:true});r.result.createObjectStore('keys');};r.onerror=()=>reject(r.error);r.onsuccess=()=>{const db=r.result,tx=db.transaction(['items','keys'],'readwrite');const cycle={label:'saved',date:new Date('2020-01-02'),blob:new Blob(['blob-content'],{type:'text/plain'}),bytes:new Uint16Array([7,65000]),map:new Map([['a',new Set([1,2])]]),big:12345678901234567890n};cycle.self=cycle;tx.objectStore('items').put(cycle);tx.objectStore('items').put({id:50,label:'deleted'});tx.objectStore('items').delete(50);tx.objectStore('keys').put({value:'compound'},['key',new Date('2020-01-01')]);tx.oncomplete=()=>{db.close();resolve(true)};tx.onerror=()=>reject(tx.error);};})""")
      flush()
      check("Storage enumeration hides checkpoint metadata")(local("localStorage.length===2&&Object.keys(localStorage).sort().join(',')==='name,property'&&localStorage.key(2)===null").asBoolean)
      check("profile stores databases and Web Storage")(Files.readString(root.resolve("alpha.json")).contains("blob-content")==false&&Files.readString(root.resolve("alpha.json")).contains("databases"))
      // Both native frontend storage and gateway memory disappear; only the profile remains.
      front.stop().sync();gateway.stop();gateway=new ExecutionServer(port,token,Some(root),policy);gateway.start();front=BrowserHost.start().sync();page=front.open(gateway.address+"/#"+token).sync()
      open("alpha")
      check("fresh browser restores Web Storage before first website script")(local("startup.local==='alpha'&&startup.session==='draft-value'&&localStorage.property==='property-value'&&localStorage.getItem('removed')===null").asBoolean)
      check("IndexedDB exists before website startup")(local("startupDatabases.then(dbs=>dbs.some(d=>d.name==='records'&&d.version===3))").asBoolean)
      val restored=local("""new Promise((resolve,reject)=>{const r=indexedDB.open('records');r.onerror=()=>reject(r.error);r.onsuccess=()=>{const db=r.result;const tx=db.transaction(['items','keys'],'readonly');const s=tx.objectStore('items'),unique=s.index('label').unique;const read=s.get(1);read.onsuccess=async()=>{try{const x=read.result;const text=await x.blob.text();resolve(db.version===3&&unique&&x.self===x&&x.date instanceof Date&&x.bytes[1]===65000&&x.map.get('a').has(2)&&x.big===12345678901234567890n&&text==='blob-content');db.close();}catch(e){reject(e)}};};})""")
      check("IndexedDB schema and structured clone values restored")(restored.asBoolean)
      check("deleted auto increment high key is retained")(local("""new Promise((resolve,reject)=>{const r=indexedDB.open('records');r.onsuccess=()=>{const db=r.result,tx=db.transaction('items','readwrite'),a=tx.objectStore('items').add({label:'next'});a.onsuccess=()=>{const key=a.result;tx.oncomplete=()=>{db.close();resolve(key)}};a.onerror=()=>reject(a.error);};})""").asInt==51)
      local("localStorage.setItem('immediate','reload');true")
      page.send("Page.reload").sync()
      until("reload recovers pending Web Storage")(js("agentEpoch!==null").asBoolean&&local("localStorage.getItem('immediate')==='reload'").asBoolean)
      flush()
      js("close()");open("beta")
      check("another profile has no storage")(local("startup.local===null&&startup.session===null").asBoolean&&local("indexedDB.databases().then(x=>x.length)").asInt==0)
      js("close()");open("alpha",url.replace("127.0.0.1","localhost"))
      check("another upstream origin has no storage")(local("startup.local===null").asBoolean&&local("indexedDB.databases().then(x=>x.length)").asInt==0)
      js("close()");open("alpha")
      local("localStorage.clear();sessionStorage.clear();new Promise((resolve,reject)=>{const r=indexedDB.deleteDatabase('records');r.onsuccess=()=>resolve(true);r.onerror=()=>reject(r.error)})");flush()
      front.stop().sync();gateway.stop();gateway=new ExecutionServer(port,token,Some(root),policy);gateway.start();front=BrowserHost.start().sync();page=front.open(gateway.address+"/#"+token).sync();open("alpha")
      check("clear and database deletion survive a fresh browser")(local("startup.local===null&&startup.session===null").asBoolean&&local("indexedDB.databases().then(x=>x.length)").asInt==0)
      js("close()")
      Files.writeString(out.resolve("result.json"),JsonFormatter.Default(obj("status"->str("passed"),"flushMs"->arr(flushTimings.toList.map(num)*),"checks"->arr(checks.toList.map(str)*))))
    }catch{case e:Throwable=>Files.writeString(out.resolve("result.json"),JsonFormatter.Default(obj("status"->str("failed"),"checks"->arr(checks.toList.map(str)*),"error"->str(e.toString))));throw e}
    finally {front.stop().sync();gateway.stop();site.stop(0);val paths=Files.walk(root);try paths.sorted(java.util.Comparator.reverseOrder()).forEach(Files.deleteIfExists(_))finally paths.close()}
  }
}
