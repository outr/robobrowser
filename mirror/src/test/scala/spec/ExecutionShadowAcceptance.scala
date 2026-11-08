package spec

import fabric.*
import fabric.io.JsonFormatter
import robobrowser.BrowserHost
import robobrowser.mirror.{ExecutionServer}
import java.nio.file.{Files,Path}

/** Agent targets must cross shadow boundaries without changing the website's closed-root API. */
object ExecutionShadowAcceptance {
  def main(args:Array[String]):Unit={
    val host=BrowserHost.start().sync();val front=BrowserHost.start().sync()
    val server=new ExecutionServer(0)
    val out=Path.of("target/execution-shadow");Files.createDirectories(out);val checks=scala.collection.mutable.ListBuffer.empty[String]
    def q(s:String)=JsonFormatter.Compact(str(s))
    def check(name:String)(test: => Boolean):Unit={require(test,name);checks+=name;println("PASS "+name)}
    def until(name:String)(test: => Boolean):Unit={val end=System.nanoTime()+20000000000L;var ok=false;while(!ok&&System.nanoTime()<end){ok=scala.util.Try(test).getOrElse(false);if(!ok)Thread.sleep(100)};check(name)(ok)}
    def report(status:String,error:Option[String]=None):Unit=Files.writeString(out.resolve("result.json"),JsonFormatter.Default(obj("status"->str(status),"checks"->arr(checks.toList.map(str)*),"error"->error.map(str).getOrElse(Null))))
    try {
      server.start();val page=front.open(server.address+"/execution#"+server.token).sync()
      def js(code:String)=page.evaluate(code).sync()
      def local(code:String)=js("local('eval',{code:"+q(code)+"})")
      until("Shadow shell ready")(js("typeof api==='function'").asBoolean)
      js("open('')");until("Shadow fixture connected")(js("agentEpoch!==null").asBoolean&&local("!!window.executionFixture").asBoolean)
      local("""(()=>{
        window.shadowResult={clicks:0,text:'',file:null,outerInputs:0};
        const host=document.createElement('section');host.id='closed-host';document.body.append(host);
        const closed=host.attachShadow({mode:'closed'});
        const nested=document.createElement('div');closed.append(nested);const root=nested.attachShadow({mode:'closed'});
        root.innerHTML='<button>Closed action</button><input aria-label="Closed text"><input type="file" aria-label="Closed file">';
        root.querySelector('button').onclick=()=>shadowResult.clicks++;
        root.querySelector('input').oninput=e=>shadowResult.text=e.target.value;
        root.querySelector('input[type=file]').onchange=e=>shadowResult.file=e.target.files[0]?.name??null;
        document.addEventListener('input',e=>{if(e.target===host)shadowResult.outerInputs++;});
        const open=document.createElement('section');open.id='open-host';document.body.append(open);open.attachShadow({mode:'open'}).innerHTML='<button>Open action</button>';
        const declaration=document.createElement('section');declaration.id='declarative-host';document.body.append(declaration);declaration.setHTMLUnsafe('<div id="declarative-inner"><template shadowrootmode="open"><button>Declarative action</button></template></div>');
        return true;
      })()""")
      check("Closed roots remain closed to ordinary website APIs")(local("document.querySelector('#closed-host').shadowRoot===null").asBoolean)
      check("Declarative fixture contains a native open shadow root")(local("!!document.querySelector('#declarative-inner').shadowRoot").asBoolean)
      js("agentCommand({op:'read'}).then(r=>window.shadowControls=r.elements)")
      Files.writeString(out.resolve("controls.json"),JsonFormatter.Default(js("shadowControls")))
      check("Agent discovers controls inside nested closed and open roots")(js("['Closed action','Open action','Declarative action'].every(text=>shadowControls.some(e=>e.text===text&&e.selectorPath))").asBoolean)
      js("window.closedButton=shadowControls.find(e=>e.text==='Closed action');window.closedText=shadowControls.find(e=>e.text==='Closed text');window.closedFile=shadowControls.find(e=>e.text==='Closed file');true")
      val click="agentCommand({op:'click',selectorPath:closedButton.selectorPath},'closed-click-once')"
      js(click);js(click)
      check("Closed-root click executes once across retries")(local("shadowResult.clicks===1").asBoolean)
      js("agentCommand({op:'type',selectorPath:closedText.selectorPath,value:'inside shadow'})")
      check("Closed-root text edit reaches component and outer input listeners")(local("shadowResult.text==='inside shadow'&&shadowResult.outerInputs===1").asBoolean)
      js("agentCommand({op:'files',selectorPath:closedFile.selectorPath,files:[{name:'shadow.txt',base64:'eA=='}]})")
      check("Closed-root file selection reaches the native component")(local("shadowResult.file==='shadow.txt'").asBoolean)
      check("Agent read returns updated shadow input and file metadata")(js("agentCommand({op:'read'}).then(r=>r.elements.some(e=>e.value==='inside shadow')&&r.elements.some(e=>e.files?.[0]?.name==='shadow.txt'))").asBoolean)
      local("document.querySelector('#closed-host').remove();true")
      check("Removed shadow path is rejected without side effects")(js("agentCommand({op:'click',selectorPath:closedButton.selectorPath}).then(()=>false,()=>true)").asBoolean&&local("shadowResult.clicks===1").asBoolean)
      check("Invalid shadow paths cannot select arbitrary objects")(js("agentCommand({op:'click',selectorPath:['#open-host',[-1]]}).then(()=>false,()=>true)").asBoolean)
      check("Shadow workflow leaves no page or viewer errors")(js("local('state')")("metrics")("errors").asVector.isEmpty&&js("$('error').textContent").asString.isEmpty)
      js("close()");report("passed")
    }catch{case e:Throwable=>report("failed",Some(e.toString));throw e}
    finally{server.stop();front.stop().sync();host.stop().sync()}
  }
}
