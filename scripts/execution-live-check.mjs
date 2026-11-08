#!/usr/bin/env node
// Isolated public-page check against the managed port-8898 demo. Requires Node 22+ and Chrome.
import {fileURLToPath} from 'node:url';import {spawn} from 'node:child_process';import fs from 'node:fs/promises';import os from 'node:os';import path from 'node:path';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..'),token=(await fs.readFile(root+'/.robobrowser-demo/token','utf8')).trim(),base='http://127.0.0.1:8898';
async function api(c){const d=await(await fetch(base+'/execution/api',{method:'POST',headers:{Authorization:'Bearer '+token,'Content-Type':'application/json'},body:JSON.stringify(c)})).json();if(!d.ok)throw Error(JSON.stringify(d));return d.result}
let source=null,profile=null,diagnostic=null;
const evidence=root+'/.robobrowser-demo/readiness-live';await fs.mkdir(evidence,{recursive:true});
await fs.writeFile(evidence+'/result.json',JSON.stringify({status:'running',startedAt:new Date().toISOString(),port:8898},null,2));
const dir=await fs.mkdtemp(path.join(os.tmpdir(),'rb-single-live-'));const child=spawn(process.env.CHROME_BIN||'/usr/bin/google-chrome',['--headless=new','--no-first-run','--no-default-browser-check','--remote-debugging-port=0','--user-data-dir='+dir,'about:blank'],{stdio:'ignore'});let socket;
try{
 let port;for(let i=0;i<100;i++){try{port=Number((await fs.readFile(path.join(dir,'DevToolsActivePort'),'utf8')).split('\n')[0]);break}catch{}await new Promise(r=>setTimeout(r,100))}
 const pages=await(await fetch(`http://127.0.0.1:${port}/json/list`)).json();socket=new WebSocket(pages.find(p=>p.type==='page').webSocketDebuggerUrl);await new Promise((r,j)=>{socket.onopen=r;socket.onerror=j});let id=0;const pending=new Map();socket.onmessage=e=>{const d=JSON.parse(e.data);if(d.id){pending.get(d.id)?.(d);pending.delete(d.id)}};
 async function call(method,params={}){const d=await Promise.race([new Promise(r=>{pending.set(++id,r);socket.send(JSON.stringify({id,method,params}))}),new Promise((_,j)=>setTimeout(()=>j(Error('CDP timeout')),15000).unref())]);if(d.error)throw Error(JSON.stringify(d.error));return d.result}
 async function js(expression){const d=await call('Runtime.evaluate',{expression,returnByValue:true,awaitPromise:true});if(d.exceptionDetails)throw Error(JSON.stringify(d.exceptionDetails));return d.result.value}
 async function until(expression){const end=Date.now()+120000;while(Date.now()<end){if(await js(expression).catch(()=>false))return;await new Promise(r=>setTimeout(r,100))}throw Error('Timed out: '+expression)}


 diagnostic=async error=>{
   const out=root+'/.robobrowser-demo/readiness-live';await fs.mkdir(out,{recursive:true});
   const state=await js("(async()=>{const d={url:$('url').value,error:$('error').textContent,epoch:agentEpoch};try{d.state=await local('state');}catch(e){d.stateError=String(e);}try{d.videos=await local('eval',{code:\"[...document.querySelectorAll('video')].map(v=>({src:v.currentSrc,ready:v.readyState,network:v.networkState,error:v.error?.message,paused:v.paused}))\"});}catch(e){d.videoError=String(e);}return d;})()").catch(e=>({error:String(e)}));
   await fs.writeFile(out+'/result.json',JSON.stringify({status:'failed',testedAt:new Date().toISOString(),error:String(error),state},null,2));
   await fs.writeFile(out+'/failure.png',Buffer.from((await call('Page.captureScreenshot',{format:'png'})).data,'base64'));
 };
 await call('Emulation.setDeviceMetricsOverride',{width:1500,height:1100,deviceScaleFactor:1,mobile:false});
 await call('Page.navigate',{url:base+'/execution#'+token});await until("typeof api==='function'&&typeof session!=='undefined'");
 async function local(code){return js("local('eval',{code:"+JSON.stringify(code)+"})");}
 profile='readiness-live-'+Date.now();
 await js("$('profile').value="+JSON.stringify(profile)+";open('')");source=await js('session.id');
 await until("agentEpoch!==null");await until("local('eval',{code:'!!window.executionFixture'})");
 const first=await local('location.origin');
 await local("document.cookie='liveCheck=retained; Path=/; Max-Age=3600';localStorage.setItem('liveCheck','retained');true");
 await js('close()');source=null;
 await js("open('')");source=await js('session.id');await until("agentEpoch!==null");
 const retained=await local("document.cookie.includes('liveCheck=retained')&&localStorage.getItem('liveCheck')==='retained'");
 if(!retained||await local('location.origin')!==first)throw Error('Profile retention failed');
 await local("const input=document.createElement('input');input.id='live-file-check';input.type='file';document.body.append(input);true");
 const fileCheck=await js("agentCommand({op:'files',selector:'#live-file-check',files:[{name:'live.txt',base64:'bGl2ZQ=='}]}).then(f=>f[0].name==='live.txt'&&f[0].size===4)");
 if(!fileCheck)throw Error('Live file API failed');
 await local("const button=document.createElement('button');button.id='live-popup-check';button.textContent='Open verification popup';button.onclick=()=>window.livePopup=window.open(location.href,'live-check');document.body.append(button);button.scrollIntoView({block:'center'});true");
 const frameRect=await js('frame.getBoundingClientRect().toJSON()'),buttonRect=await local("document.querySelector('#live-popup-check').getBoundingClientRect().toJSON()");
 for(const type of ['mousePressed','mouseReleased'])await call('Input.dispatchMouseEvent',{type,button:'left',clickCount:1,x:frameRect.x+buttonRect.x+10,y:frameRect.y+buttonRect.y+10});
 await until("agentCommand({op:'windows'}).then(w=>w.length===1&&w[0].connected)");
 const popupCheck=await js("agentCommand({op:'windows'}).then(w=>agentCommand({op:'windowCommand',windowId:w[0].id,windowEpoch:w[0].epoch,command:{op:'read'}})).then(r=>r.elements.some(e=>e.selector==='#increment'))");
 if(!popupCheck)throw Error('Live popup agent API failed');
 await local('livePopup.close();true');await until("agentCommand({op:'windows'}).then(w=>w.length===0)");
 await local("const host=document.createElement('section');host.id='live-shadow-check';document.body.append(host);const root=host.attachShadow({mode:'closed'});root.innerHTML='<input aria-label=\"Live shadow\">';root.querySelector('input').oninput=e=>window.liveShadowValue=e.target.value;true");
 await js("agentCommand({op:'read'}).then(r=>agentCommand({op:'type',selectorPath:r.elements.find(e=>e.text==='Live shadow').selectorPath,value:'verified'}))");
 const shadowCheck=await local("liveShadowValue==='verified'&&document.querySelector('#live-shadow-check').shadowRoot===null");
 if(!shadowCheck)throw Error('Live shadow agent API failed');
 const agentTimes=[];for(let i=0;i<20;i++){const started=Date.now();await js("agentCommand({op:'read'})");agentTimes.push(Date.now()-started);}agentTimes.sort((a,b)=>a-b);
 const agentTiming={samples:agentTimes.length,medianMs:agentTimes[10],p95Ms:agentTimes[18]};
 const sourceRoot=root+'/mirror/src/main/resources/robobrowser/mirror/';
 for(const [route,file] of [['/execution','execution.html'],['/execution.js','execution.js']]){
   if(await(await fetch(base+route)).text()!==await fs.readFile(sourceRoot+file,'utf8'))throw Error('Stale served '+file);
 }
 const bridgeURL=new URL('/__rb_execution/bridge.js',await js('session.local'));
 const expected='(()=>{'+await fs.readFile(sourceRoot+'execution-sockets.js','utf8')+await fs.readFile(sourceRoot+'execution-bridge.js','utf8')+'})();';
 if(await(await fetch(bridgeURL)).text()!==expected)throw Error('Stale served bridge');
 const githubStarted=Date.now();
 await js("open('https://github.com/')");await until("agentEpoch!==null");
 await until("local('eval',{code:"+JSON.stringify("document.querySelector('video')?.readyState>=2")+"})");
 const githubVideoReadyMs=Date.now()-githubStarted;
 await local("document.querySelector('video').scrollIntoView({block:'center'});true");
 await until("local('eval',{code:"+JSON.stringify("!document.querySelector('video').paused")+"})");
 const before=await local("document.querySelector('video').getVideoPlaybackQuality().totalVideoFrames");
 await new Promise(r=>setTimeout(r,3000));
 const after=await local("document.querySelector('video').getVideoPlaybackQuality().totalVideoFrames");
 if(after-before<30)throw Error('Live GitHub video stalled');
 await until("local('state').then(s=>s.metrics.media.every(m=>['recovered','superseded'].includes(m.status)))");
 const githubCompletedMs=Date.now()-githubStarted;
 const github=await js("local('state')");
 if(github.metrics.errors.length||await js("$('error').textContent"))throw Error('Live GitHub page has errors');
 await fs.mkdir(root+'/.robobrowser-demo/readiness-live',{recursive:true});
 await fs.writeFile(root+'/.robobrowser-demo/readiness-live/github.png',Buffer.from((await call('Page.captureScreenshot',{format:'png'})).data,'base64'));
 const facebookStarted=Date.now();
 await js("open('facebook.com')");await until("agentEpoch!==null");
 await until("local('eval',{code:"+JSON.stringify("document.title.includes('Facebook')&&!!document.querySelector('input[type=password]')")+"})");
 const facebookControlsReadyMs=Date.now()-facebookStarted;
 await new Promise(r=>setTimeout(r,4000));
 const state=await js("local('state')");
 const viewerError=await js("$('error').textContent");
 if(state.metrics.errors.length||viewerError)throw Error(JSON.stringify({state,viewerError}));
 const out=root+'/.robobrowser-demo/readiness-live';await fs.mkdir(out,{recursive:true});
 await fs.writeFile(out+'/facebook.png',Buffer.from((await call('Page.captureScreenshot',{format:'png'})).data,'base64'));
 const wikipediaStarted=Date.now();
 await js("open('https://www.wikipedia.org/')");await until("agentEpoch!==null");
 await until("local('eval',{code:"+JSON.stringify("!!document.querySelector('input[type=search],input[name=search]')")+"})");
 await new Promise(r=>setTimeout(r,3000));
 const wikipediaReadyWithSettleMs=Date.now()-wikipediaStarted;
 const wikipedia=await js("local('state')");
 if(wikipedia.metrics.errors.length||await js("$('error').textContent"))throw Error('Live Wikipedia page has errors');
 await fs.writeFile(out+'/wikipedia.png',Buffer.from((await call('Page.captureScreenshot',{format:'png'})).data,'base64'));
 await fs.writeFile(out+'/result.json',JSON.stringify({status:'passed',testedAt:new Date().toISOString(),port:8898,profileRetained:retained,agentTiming,githubVideoReadyMs,githubCompletedMs,githubMedia:github.metrics.media,facebookControlsReadyMs,wikipediaReadyWithSettleMs,fileApi:fileCheck,popupApi:popupCheck,shadowApi:shadowCheck,sourceMatches:true,githubFramesOverThreeSeconds:after-before,facebook:state,wikipedia},null,2));
 await js('close()');source=null;
 for(const suffix of ['.json','.lock'])await fs.rm(root+'/.robobrowser-demo/profiles/'+profile+suffix,{force:true});
 console.log(JSON.stringify({status:'passed',testedAt:new Date().toISOString(),port:8898,profileRetained:retained,agentTiming,githubVideoReadyMs,githubCompletedMs,githubMedia:github.metrics.media,facebookControlsReadyMs,wikipediaReadyWithSettleMs,fileApi:fileCheck,popupApi:popupCheck,shadowApi:shadowCheck,githubFramesOverThreeSeconds:after-before,facebookErrors:state.metrics.errors,wikipediaErrors:wikipedia.metrics.errors}));

}catch(error){await fs.writeFile(evidence+'/result.json',JSON.stringify({status:'failed',testedAt:new Date().toISOString(),error:String(error)},null,2));await diagnostic?.(error).catch(()=>{});throw error;}finally{socket?.close();child.kill('SIGTERM');await new Promise(r=>setTimeout(r,500));await fs.rm(dir,{recursive:true,force:true,maxRetries:5,retryDelay:200});if(source)await api({op:'close',id:source});if(profile)for(const suffix of ['.json','.lock'])await fs.rm(root+'/.robobrowser-demo/profiles/'+profile+suffix,{force:true})}
