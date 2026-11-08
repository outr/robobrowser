(() => {
  'use strict';
  const config=JSON.parse(document.currentScript.dataset.config);
  const originalFetch=window.fetch.bind(window),originalOpen=XMLHttpRequest.prototype.open;
  const ownOrigin=location.origin, upstream=new URL(config.upstream), mappings=new Map([[upstream.origin,ownOrigin]]);
  const reverseOrigins=new Map(Object.entries(config.origins||{})),aliases=new Map();
  for(const [proxy,real] of reverseOrigins)mappings.set(real,proxy);
  function realURL(value){try{if(aliases.has(value))return aliases.get(value);const u=new URL(value);if(u.pathname.startsWith('/__rb_execution/fetch/')){const target=u.searchParams.get('target');if(target?.startsWith('/'))return realURL(u.origin+target);}const origin=reverseOrigins.get(u.origin);return origin?origin+u.pathname+u.search+u.hash:value;}catch{return value;}}
  let port=null,replaying=false,allowPageFocus=true;
  const capturedShadowRoots=new WeakMap(),nativeAttachShadow=Element.prototype.attachShadow;
  Element.prototype.attachShadow=function(options){const root=nativeAttachShadow.call(this,options);capturedShadowRoots.set(this,root);return root;};
  const nativeFocus=HTMLElement.prototype.focus;
  HTMLElement.prototype.focus=function(options){if(allowPageFocus)return nativeFocus.call(this,options);};
  for(const event of ['pointerdown','keydown'])document.addEventListener(event,e=>{if(e.isTrusted)allowPageFocus=true;},true);
  const metrics={moves:0,maxInputDelayMs:0,errors:[],media:[]};
  const effectiveBase=()=>config.hasBase?config.base:realURL(location.href);
  installExecutionSockets({origin:ownOrigin,base:effectiveBase,fetch:originalFetch});
  function map(value){
    let u;try{u=new URL(String(value),effectiveBase());}catch{return String(value);}
    if(!['http:','https:'].includes(u.protocol)||u.hostname.endsWith('.'+config.session+'.localhost'))return String(value);
    let origin=mappings.get(u.origin);
    if(!origin){
      // Registration failures must not silently fall back to a direct frontend request.
      const r=new XMLHttpRequest();originalOpen.call(r,'POST',ownOrigin+'/__rb_execution/map',false);r.send(u.origin+'/');
      if(r.status!==200)throw Error('Unable to map upstream origin');origin=new URL(JSON.parse(r.responseText).url).origin;mappings.set(u.origin,origin);reverseOrigins.set(origin,u.origin);
    }
    return origin+u.pathname+u.search+u.hash;
  }
  // Keep auxiliary windows inside this session's mapped origins. They inherit the iframe sandbox.
  const nativeWindowOpen=window.open.bind(window),nativePostMessage=window.postMessage,nativeOpener=window.opener;
  const childWindows=new Map(),windowFacades=new WeakMap();let childRequestId=0;
  function releaseChild(child){
    child.port?.close();child.port=null;child.epoch=null;
    for(const request of child.pending.values()){clearTimeout(request.timer);request.reject(Object.assign(Error('Popup document changed; outcome unknown'),{unknown:true}));}
    child.pending.clear();
  }
  function liveChildren(){for(const [id,child] of childWindows)if(child.window.closed||(child.element&&!child.element.isConnected)){releaseChild(child);childWindows.delete(id);}return [...childWindows.values()];}
  window.open=function(url,target,features){
    if(liveChildren().filter(c=>c.kind==='window').length>=16)return null;
    const opened=nativeWindowOpen(url?map(url):url,target,features);
    if(!opened||opened===window||opened===parent||opened===top)return opened;
    if(opened&&opened!==window&&!liveChildren().some(child=>child.window===opened)){
      const id=crypto.randomUUID();childWindows.set(id,{id,kind:'window',window:opened,epoch:null,url:url?String(url):'about:blank',pending:new Map(),port:null});
    }
    return opened?windowFacade(opened):opened;
  };
  function childCommand(data){
    const child=liveChildren().find(c=>c.id===(data.op==='frameCommand'?data.frameId:data.windowId)&&c.kind===(data.op==='frameCommand'?'frame':'window'));
    if(!child?.port||!child.epoch||child.epoch!==(data.op==='frameCommand'?data.frameEpoch:data.windowEpoch))throw Error('Popup document unavailable or stale');
    if(!data.command||!['state','read','click','type','scroll','eval','files','frames','frameCommand','windows','windowCommand','storageFlush','storageStatus'].includes(data.command.op))throw Error('Unsupported popup operation');
    return new Promise((resolve,reject)=>{
      const id=++childRequestId,timer=setTimeout(()=>{child.pending.delete(id);reject(Object.assign(Error('Popup command timed out; outcome unknown'),{unknown:true}));},4000);
      child.pending.set(id,{resolve,reject,timer});child.port.postMessage({...data.command,id});
    });
  }
  function messageTarget(value){return typeof value==='string'&&value!=='*'&&value!=='/'?new URL(map(value)).origin:value;}
  function postTo(target,data,options,transfer){
    if(options&&typeof options==='object')return nativePostMessage.call(target,data,{...options,targetOrigin:messageTarget(options.targetOrigin)});
    return nativePostMessage.call(target,data,messageTarget(options),transfer);
  }
  const nativeMessageOrigin=Object.getOwnPropertyDescriptor(MessageEvent.prototype,'origin');
  Object.defineProperty(MessageEvent.prototype,'origin',{...nativeMessageOrigin,get(){const value=nativeMessageOrigin.get.call(this);try{return value&&value!=='null'?new URL(realURL(value)).origin:value;}catch{return value;}}});
  function windowFacade(target){
    if(windowFacades.has(target))return windowFacades.get(target);
    const facade=new Proxy(target,{
      get(actual,key){
        if(key==='postMessage')return (data,options,transfer)=>postTo(actual,data,options,transfer);
        if(key==='location'){
          const location=actual.location;
          return new Proxy(location,{
            get(loc,name){const value=Reflect.get(loc,name,loc);if(name==='href')return realURL(value);if(typeof value==='function'&&['assign','replace'].includes(name))return url=>value.call(loc,map(url));return typeof value==='function'?value.bind(loc):value;},
            set(loc,name,value){return Reflect.set(loc,name,name==='href'?map(value):value,loc);}
          });
        }
        const value=Reflect.get(actual,key,actual);
        if(value===actual)return facade;
        return typeof value==='function'&&['close','focus','blur'].includes(key)?value.bind(actual):value;
      },
      set(actual,key,value){return Reflect.set(actual,key,key==='location'?map(value):value,actual);}
    });
    windowFacades.set(target,facade);return facade;
  }
  const nativeMessageSource=Object.getOwnPropertyDescriptor(MessageEvent.prototype,'source');
  Object.defineProperty(MessageEvent.prototype,'source',{...nativeMessageSource,get(){const source=nativeMessageSource.get.call(this);return windowFacades.get(source)||source;}});
  if(nativeOpener){
    let opener=nativeOpener;const facade=windowFacade(opener);
    Object.defineProperty(window,'opener',{configurable:true,get(){return opener?facade:null;},set(value){if(value===null)opener=null;}});
  }
  // Location is unforgeable; intercept cancelable document navigation at the browser boundary.
  window.navigation?.addEventListener('navigate',event=>{
    if(!event.cancelable||event.formData||event.downloadRequest||event.navigationType==='traverse')return;
    const target=map(event.destination.url);
    if(target===event.destination.url)return;
    event.preventDefault();
    queueMicrotask(()=>event.navigationType==='replace'?location.replace(target):location.assign(target));
  });
  for(const name of ['pushState','replaceState']){
    const original=history[name];history[name]=function(state,title,url){return arguments.length<3?original.call(this,state,title):original.call(this,state,title,url==null?url:map(url));};
  }
  function cookieOperation(value){
    const r=new XMLHttpRequest();originalOpen.call(r,'POST',ownOrigin+'/__rb_execution/cookies',false);
    r.send(JSON.stringify({path:location.pathname+location.search,...(value===undefined?{}:{value:String(value)})}));
    if(r.status!==200)throw Error('Cookie synchronization failed');return JSON.parse(r.responseText).value;
  }
  Object.defineProperty(Document.prototype,'cookie',{configurable:true,get(){return cookieOperation();},set(value){cookieOperation(value);}});
  function requestURL(value,credentials){
    const mapped=map(value);const u=new URL(mapped,effectiveBase());
    if(!reverseOrigins.has(u.origin))return mapped;
    return u.origin+'/__rb_execution/fetch/'+credentials+'?target='+encodeURIComponent(u.pathname+u.search);
  }
  const responseURL=Object.getOwnPropertyDescriptor(Response.prototype,'url');
  Object.defineProperty(Response.prototype,'url',{...responseURL,get(){return realURL(responseURL.get.call(this));}});
  window.fetch=(input,init)=>{
    const credentials=init?.credentials??(input instanceof Request?input.credentials:'same-origin');
    if(!['omit','same-origin','include'].includes(credentials))return originalFetch(input,init);
    if(input instanceof Request)input=new Request(requestURL(input.url,credentials),input);
    else input=requestURL(input,credentials);
    return originalFetch(input,init);
  };
  const xhrRequests=new WeakMap(),nativeSend=XMLHttpRequest.prototype.send,nativeHeader=XMLHttpRequest.prototype.setRequestHeader,nativeMime=XMLHttpRequest.prototype.overrideMimeType;
  XMLHttpRequest.prototype.open=function(method,url,...rest){
    xhrRequests.set(this,{method,url,rest,headers:[]});return originalOpen.call(this,method,map(url),...rest);
  };
  XMLHttpRequest.prototype.setRequestHeader=function(name,value){const result=nativeHeader.call(this,name,value);xhrRequests.get(this)?.headers.push([name,value]);return result;};
  XMLHttpRequest.prototype.overrideMimeType=function(mime){const result=nativeMime.call(this,mime);const info=xhrRequests.get(this);if(info)info.mime=mime;return result;};
  XMLHttpRequest.prototype.send=function(body){
    const info=xhrRequests.get(this);
    if(info){
      const withCredentials=this.withCredentials,timeout=this.timeout,responseType=this.responseType;
      originalOpen.call(this,info.method,requestURL(info.url,withCredentials?'include':'same-origin'),...info.rest);
      this.withCredentials=withCredentials;this.timeout=timeout;this.responseType=responseType;
      for(const [name,value] of info.headers)nativeHeader.call(this,name,value);
      if(info.mime)nativeMime.call(this,info.mime);xhrRequests.delete(this);
    }
    return nativeSend.call(this,body);
  };
  const xhrURL=Object.getOwnPropertyDescriptor(XMLHttpRequest.prototype,'responseURL');
  Object.defineProperty(XMLHttpRequest.prototype,'responseURL',{...xhrURL,get(){return realURL(xhrURL.get.call(this));}});
  const originalAttribute=Element.prototype.setAttribute,originalGet=Element.prototype.getAttribute;
  const urlAttributes=new Set(['src','href','action','poster','formaction','data-src','srcset','imagesrcset']);
  const attributes=new WeakMap();
  function records(n){
    let values=attributes.get(n);if(values)return values;values=new Map();attributes.set(n,values);
    try{for(const [name,value] of Object.entries(JSON.parse(originalGet.call(n,'data-rb-original-urls')||'{}')))values.set(name,{value,mapped:originalGet.call(n,name)});}catch{}
    return values;
  }
  function setURL(n,name,value,mapped){records(n).set(name,{value:String(value),mapped});originalAttribute.call(n,name,mapped);}
  Element.prototype.getAttribute=function(name){const key=String(name).toLowerCase(),physical=originalGet.call(this,name);if(!urlAttributes.has(key))return physical;const saved=records(this).get(key);return saved&&saved.mapped===physical?saved.value:physical;};
  Element.prototype.setAttribute=function(name,value){const key=String(name).toLowerCase();if(urlAttributes.has(key)&&!key.endsWith('srcset'))setURL(this,key,value,map(value));else originalAttribute.call(this,name,value);};
  for(const [type,property] of [[HTMLImageElement,'src'],[HTMLScriptElement,'src'],[HTMLLinkElement,'href'],[HTMLAnchorElement,'href'],[HTMLIFrameElement,'src'],[HTMLMediaElement,'src'],[HTMLVideoElement,'poster'],[HTMLSourceElement,'src'],[HTMLFormElement,'action'],[HTMLButtonElement,'formAction'],[HTMLInputElement,'formAction'],[HTMLAreaElement,'href']]){
    const d=Object.getOwnPropertyDescriptor(type.prototype,property);if(d?.set)Object.defineProperty(type.prototype,property,{...d,get(){const raw=this.getAttribute(property);if(raw===null)return realURL(d.get.call(this));try{return new URL(raw,effectiveBase()).href;}catch{return raw;}},set(value){setURL(this,property.toLowerCase(),value,map(value));}});
  }
  const nativeCurrentSrc=Object.getOwnPropertyDescriptor(HTMLMediaElement.prototype,'currentSrc');
  Object.defineProperty(HTMLMediaElement.prototype,'currentSrc',{...nativeCurrentSrc,get(){return realURL(nativeCurrentSrc.get.call(this));}});
  // URL rewriting is intentionally bounded. Origin-sensitive APIs, workers and service workers
  // require additional design; this experiment does not claim a transparent browser environment.
  const selector=n=>{
    if(!(n instanceof Element))return null;
    if(n.id)return '#'+CSS.escape(n.id);
    const parts=[];while(n&&n!==document.documentElement){const p=n.parentElement;if(!p)return null;parts.unshift(n.localName+':nth-child('+([...p.children].indexOf(n)+1)+')');n=p;}
    return 'html>'+parts.join('>');
  };
  function selectorPath(n){
    const root=n.getRootNode();if(!(root instanceof ShadowRoot))return [selector(n)];
    const indices=[];let current=n;
    while(current!==root){const parent=current.parentNode;if(!parent?.children)return null;indices.unshift([...parent.children].indexOf(current));current=parent;}
    const outer=selectorPath(root.host);return outer?[...outer,indices]:null;
  }
  function target(data){
    if(data.selectorPath===undefined)return document.querySelector(data.selector);
    const path=data.selectorPath;if(!Array.isArray(path)||path.length<1||path.length>16)throw Error('Invalid shadow selector path');
    let root=document,n=null;
    for(let i=0;i<path.length;i++){
      const part=path[i];
      if(typeof part==='string'&&part.length<=4096)n=root.querySelector(part);
      else if(Array.isArray(part)&&part.length>0&&part.length<=64&&part.every(x=>Number.isInteger(x)&&x>=0&&x<100000)){
        n=root;for(const index of part){n=n?.children?.[index];if(!n)break;}
      }else throw Error('Invalid shadow selector segment');
      if(!n)return null;
      if(i<path.length-1){root=capturedShadowRoots.get(n)||n.shadowRoot;if(!root)return null;}
    }
    return n;
  }
  function controls(){
    const result=[],stack=[document.documentElement];let visited=0;
    while(stack.length&&result.length<200&&visited++<10000){
      const n=stack.pop();if(!n)continue;
      if(n.matches('a,button,input,textarea,select,[role=button]'))result.push(n);
      const shadow=capturedShadowRoots.get(n)||n.shadowRoot;
      const children=[...n.children,...(shadow?[...shadow.children]:[])];
      for(let i=children.length-1;i>=0;i--)stack.push(children[i]);
    }
    return result;
  }
  document.addEventListener('pointermove',e=>{metrics.moves++;metrics.maxInputDelayMs=Math.max(metrics.maxInputDelayMs,Math.max(0,performance.now()-e.timeStamp));},true);
  document.addEventListener('click',e=>{
    if(!replaying&&port)port.postMessage({event:'click',selector:selector(e.target),trusted:e.isTrusted});
  },true);
  const recovering=new WeakMap(),playIntent=new WeakMap();
  const nativePlay=HTMLMediaElement.prototype.play,nativePause=HTMLMediaElement.prototype.pause;
  HTMLMediaElement.prototype.play=function(){playIntent.set(this,true);return nativePlay.call(this);};
  HTMLMediaElement.prototype.pause=function(){playIntent.set(this,false);return nativePause.call(this);};
  document.addEventListener('pause',e=>{if(e.target instanceof HTMLMediaElement&&!e.target.error)playIntent.set(e.target,false);},true);
  const progressiveMedia=new Map();
  function retireMedia(){for(const [n,life] of progressiveMedia)if(!n.isConnected||originalGet.call(n,'src')!==life.url)life.close();}
  setInterval(retireMedia,1000);
  new MutationObserver(retireMedia).observe(document,{subtree:true,childList:true,attributes:true,attributeFilter:['src']});
  function mediaEvent(target,event,signal,action){return new Promise((resolve,reject)=>{
    const clear=()=>{target.removeEventListener(event,done);target.removeEventListener('error',failed);signal.removeEventListener('abort',aborted);};
    const done=()=>{clear();resolve();},failed=()=>{clear();reject(Error('Progressive media decoder failed'));},aborted=()=>{clear();reject(new DOMException('Media retired','AbortError'));};
    target.addEventListener(event,done,{once:true});target.addEventListener('error',failed,{once:true});signal.addEventListener('abort',aborted,{once:true});
    if(signal.aborted){aborted();return;}try{action?.();}catch(e){clear();reject(e);}
  });}
  async function playProgressive(n,result,state,originalSource,url,time,rate){
    const controller=new AbortController(),signal=controller.signal;
    const response=await originalFetch(result.streamURL,{credentials:'omit',signal});
    if(!response.ok||!response.body)throw Error('Progressive media response unavailable');
    if(!n.isConnected||nativeCurrentSrc.get.call(n)!==url||recovering.get(n)!==state){controller.abort();return false;}
    const media=new MediaSource(),objectURL=URL.createObjectURL(media);
    let reader=null,closed=false;
    const life={url:objectURL,close(){if(closed)return;closed=true;controller.abort();reader?.cancel().catch(()=>{});URL.revokeObjectURL(objectURL);aliases.delete(objectURL);if(progressiveMedia.get(n)===life)progressiveMedia.delete(n);}};
    progressiveMedia.get(n)?.close();progressiveMedia.set(n,life);
    try{
      const opened=mediaEvent(media,'sourceopen',signal);
      aliases.set(objectURL,realURL(url));state.outputURL=objectURL;state.path='progressive';state.status='streaming';
      setURL(n,'src',originalSource??realURL(url),objectURL);n.load();n.playbackRate=rate;
      if(time)n.addEventListener('loadedmetadata',()=>{if(!closed)n.currentTime=time;},{once:true});
      const playing=(playIntent.get(n)??n.autoplay)?nativePlay.call(n):Promise.resolve();playing.catch(()=>{});
      await opened;
      const buffer=media.addSourceBuffer(result.mime);media.duration=result.duration;
      reader=response.body.getReader();
      for(;;){const {value,done}=await reader.read();if(done)break;await mediaEvent(buffer,'updateend',signal,()=>buffer.appendBuffer(value));}
      if(closed)throw new DOMException('Media retired','AbortError');
      if(media.readyState==='open')media.endOfStream();await playing;return true;
    }catch(error){life.close();throw error;}
  }
  function recoverMedia(n,event,probe=false){
    if(!(n instanceof HTMLVideoElement)||(!probe&&n.error?.code!==4)||!nativeCurrentSrc.get.call(n).startsWith('http:'))return;
    const url=nativeCurrentSrc.get.call(n),originalSource=n.getAttribute('src');if(!new URL(url).hostname.endsWith('.'+config.session+'.localhost'))return;
    const prior=recovering.get(n);
    if((prior?.url===url&&((prior.status!=='failed'&&(probe||prior.status!=='inspection-unavailable'))||prior.notifying))||aliases.has(url))return;
    if(!probe)event.stopImmediatePropagation();
    const state={url,status:'converting'};recovering.set(n,state);metrics.media.push(state);if(metrics.media.length>8)metrics.media.shift();
    const time=n.currentTime,rate=n.playbackRate,started=performance.now();
    n.requestVideoFrameCallback?.(()=>{if(recovering.get(n)===state)state.firstFrameMs=performance.now()-started;});
    (async()=>{
      let r=await originalFetch('/__rb_execution/recover'+(probe?'?probe=1':''),{method:'POST',body:url});if(!r.ok)throw Error(await r.text());let result=await r.json();state.confirmed=!!result.mime;
      const deadline=performance.now()+90000;
      async function status(){await new Promise(resolve=>setTimeout(resolve,100));r=await originalFetch('/__rb_execution/media/'+result.id);if(!r.ok)throw Error(await r.text());result=await r.json();Object.assign(state,{phase:result.phase,fetchMs:result.fetchMs,conversionMs:result.conversionMs,confirmed:!!result.mime});}
      while(result.status==='converting'&&performance.now()<deadline)await status();
      if(result.status==='not-needed'){state.status='not-needed';const index=metrics.media.indexOf(state);if(index>=0)metrics.media.splice(index,1);if(!probe)n.dispatchEvent(new Event('error'));return;}
      if(!n.isConnected||nativeCurrentSrc.get.call(n)!==url||recovering.get(n)!==state){state.status='superseded';return;}
      let progressive=false;
      const corsAllowed=new URL(result.url).origin===ownOrigin||(!result.credentialed&&(result.cors==='*'||result.cors===upstream.origin));
      if(result.status==='streaming'&&corsAllowed&&typeof MediaSource!=='undefined'&&MediaSource.isTypeSupported(result.mime)){
        try{progressive=await playProgressive(n,result,state,originalSource,url,time,rate);}
        catch(error){if(error.name==='AbortError'){state.status='superseded';return;}state.progressiveError=String(error);}
      }
      while(['converting','streaming'].includes(result.status)&&performance.now()<deadline)await status();
      Object.assign(state,{phase:result.phase,fetchMs:result.fetchMs,conversionMs:result.conversionMs});
      if(result.status!=='ready')throw Error(result.error||'Media recovery timed out');
      const physical=nativeCurrentSrc.get.call(n)||originalGet.call(n,'src');
      if(!n.isConnected||(physical!==url&&physical!==state.outputURL)||recovering.get(n)!==state){state.status='superseded';return;}
      if(!progressive){
        state.path='completed-file';aliases.set(result.url,realURL(url));state.outputURL=result.url;
        setURL(n,'src',originalSource??realURL(url),result.url);n.load();n.playbackRate=rate;
        if(time)n.addEventListener('loadedmetadata',()=>{n.currentTime=time;},{once:true});
        if(playIntent.get(n)??n.autoplay)await nativePlay.call(n);
      }
      state.status=recovering.get(n)===state?'recovered':'superseded';
    })().catch(error=>{const current=nativeCurrentSrc.get.call(n)||originalGet.call(n,'src');if(!n.isConnected||(current!==url&&current!==state.outputURL)||recovering.get(n)!==state){state.status='superseded';return;}state.error=String(error);
      // Missing video dimensions can simply mean playable audio. Failed optional inspection
      // must not fabricate a native media error; retain the reason for diagnostics instead.
      // A later real media error can still retry, and confirmed conversion failures stay visible.
      if(probe&&!state.confirmed&&!state.outputURL&&!n.error){state.status='inspection-unavailable';return;}
      state.status='failed';metrics.errors.push(String(error));state.notifying=true;try{n.dispatchEvent(new Event('error'));}finally{state.notifying=false;}});
  }
  document.addEventListener('error',e=>recoverMedia(e.target,e),true);
  document.addEventListener('loadedmetadata',e=>{const n=e.target;if(n instanceof HTMLVideoElement&&n.videoWidth===0&&!n.error)recoverMedia(n,e,true);},true);
  addEventListener('error',e=>{metrics.errors.push(String(e.message).slice(0,300));if(metrics.errors.length>20)metrics.errors.shift();});
  addEventListener('message',e=>{
    const physicalSource=nativeMessageSource.get.call(e);
    let child=liveChildren().find(c=>c.window===physicalSource);
    if(!child&&e.data?.type==='rb-execution-ready'&&e.data.session===config.session){
      const element=[...document.querySelectorAll('iframe')].find(n=>n.contentWindow===physicalSource);
      if(element&&liveChildren().filter(c=>c.kind==='frame').length<64){const id=crypto.randomUUID();child={id,kind:'frame',element,window:physicalSource,epoch:null,url:'',pending:new Map(),port:null};childWindows.set(id,child);}
    }
    const physicalOrigin=nativeMessageOrigin.get.call(e);
    if(child&&e.data?.type==='rb-execution-ready'&&e.data.session===config.session){
      if(!(()=>{try{return new URL(e.data.url).origin===physicalOrigin&&new URL(physicalOrigin).hostname.endsWith('.'+config.session+'.localhost')&&new URL(physicalOrigin).port===new URL(ownOrigin).port;}catch{return false;}})())return;
      releaseChild(child);child.url=realURL(e.data.url);
      const channel=new MessageChannel(),epoch=crypto.randomUUID();child.port=channel.port1;
      channel.port1.onmessage=event=>{
        if(child.port!==channel.port1)return;
        const d=event.data;
        if(d.event==='connected'){child.epoch=epoch;child.port.postMessage({op:'focusPolicy',allow:allowPageFocus});return;}
        if(d.event==='leaving'){releaseChild(child);return;}
        const request=child.pending.get(d.id);if(request){clearTimeout(request.timer);child.pending.delete(d.id);d.ok?request.resolve(d.result):request.reject(Object.assign(Error(d.error),{unknown:!!d.unknown}));}
      };
      channel.port1.start();nativePostMessage.call(child.window,{type:'rb-execution-connect',session:config.session},physicalOrigin,[channel.port2]);return;
    }
    if(parent!==window&&physicalSource===parent&&e.origin===config.parent&&e.data?.type==='rb-execution-reconnect'&&e.data.session===config.session){ready();return;}
    if(e.data?.type!=='rb-execution-connect'||e.data.session!==config.session||!e.ports[0])return;
    const controller=parent!==window?parent:nativeOpener;
    const validOrigin=physicalOrigin===config.parent||(()=>{try{return new URL(physicalOrigin).hostname.endsWith('.'+config.session+'.localhost')&&new URL(physicalOrigin).port===new URL(ownOrigin).port;}catch{return false;}})();
    if(!controller||physicalSource!==controller||!validOrigin||e.data?.type!=='rb-execution-connect'||e.data.session!==config.session||!e.ports[0])return;
    port?.close();port=e.ports[0];
    const replyPort=port;
    port.onmessage=async event=>{
      const {id,op,code,selector:css}=event.data;
      try{
        let result;
        if(op==='focusPolicy'){allowPageFocus=!!event.data.allow;for(const child of liveChildren())child.port?.postMessage({op:'focusPolicy',allow:allowPageFocus});result=true;}
        else if(op==='storageFlush'){const depth=Number(event.data.depth)||0;if(depth>16)throw Error('Storage frame traversal limit exceeded');for(const child of liveChildren().filter(c=>c.port&&c.epoch)){await childCommand(child.kind==='frame'?{op:'frameCommand',frameId:child.id,frameEpoch:child.epoch,command:{op:'storageFlush',depth:depth+1}}:{op:'windowCommand',windowId:child.id,windowEpoch:child.epoch,command:{op:'storageFlush',depth:depth+1}});}await window.__rbStorageSync?.flush();result=window.__rbStorageSync?.status()||{pending:false};}
        else if(op==='storageStatus')result=window.__rbStorageSync?.status()||{pending:false};
        else if(op==='state')result={localURL:location.href,url:config.upstream+location.pathname+location.search+location.hash,title:document.title,metrics,storage:window.__rbStorageSync?.status(),fixture:window.executionFixture?.state()??null};
        else if(op==='read')result={url:config.upstream+location.pathname+location.search,title:document.title,text:(document.body?.innerText||'').slice(0,30000),elements:controls().map(n=>({selector:n.getRootNode()===document?selector(n):null,...(n.getRootNode()===document?{}:{selectorPath:selectorPath(n)}),tag:n.localName,text:(n.innerText||n.getAttribute('aria-label')||'').slice(0,200),value:n.type==='password'?null:n.value??null,...(n.type==='file'?{files:[...n.files].map(f=>({name:f.name,size:f.size,type:f.type}))}:{})}))};
        else if(op==='type'){const n=target(event.data);if(!n||!n.matches('input,textarea'))throw Error('Editable element not found');n.focus();const setter=Object.getOwnPropertyDescriptor(n instanceof HTMLTextAreaElement?HTMLTextAreaElement.prototype:HTMLInputElement.prototype,'value').set;setter.call(n,String(event.data.value??''));n.dispatchEvent(new InputEvent('input',{bubbles:true,composed:true,inputType:'insertText',data:String(event.data.value??'')}));n.dispatchEvent(new Event('change',{bubbles:true}));result=true;}
        else if(op==='frames')result=liveChildren().filter(c=>c.kind==='frame').map(c=>({id:c.id,epoch:c.epoch,url:c.url,selector:selector(c.element),connected:!!c.epoch}));
        else if(op==='windows')result=liveChildren().filter(c=>c.kind==='window').map(c=>({id:c.id,epoch:c.epoch,url:c.url,connected:!!c.epoch}));
        else if(op==='windowCommand'||op==='frameCommand')result=await childCommand(event.data);
        else if(op==='files'){
          const n=target(event.data),files=event.data.files;
          if(!n?.matches('input[type=file]')||n.disabled)throw Error('Enabled file input not found');
          if(!Array.isArray(files)||files.length>10||(!n.multiple&&files.length>1))throw Error('Invalid file count');
          let total=0;const transfer=new DataTransfer();
          for(const file of files){
            if(typeof file.name!=='string'||!file.name||file.name.length>255||/[\\/\x00]/.test(file.name)||typeof file.base64!=='string')throw Error('Invalid file metadata');
            const binary=atob(file.base64);total+=binary.length;if(total>524288)throw Error('Agent files exceed 512 KiB');
            transfer.items.add(new File([Uint8Array.from(binary,c=>c.charCodeAt(0))],file.name,{type:String(file.type||'application/octet-stream')}));
          }
          n.files=transfer.files;n.dispatchEvent(new Event('input',{bubbles:true,composed:true}));n.dispatchEvent(new Event('change',{bubbles:true}));
          result=[...n.files].map(f=>({name:f.name,size:f.size,type:f.type}));
        }
        else if(op==='scroll'){scrollTo({left:Number(event.data.x)||0,top:Number(event.data.y)||0,behavior:'instant'});result={x:scrollX,y:scrollY};}
        else if(op==='eval')result=await (0,eval)(code);
        else if(op==='click'){const n=target(event.data);if(!n)throw Error('Local element not found');replaying=true;try{n.click();}finally{replaying=false;}result=true;}
        else throw Error('Unknown local operation');
        replyPort.postMessage({id,ok:true,result:result??null});
      }catch(error){replyPort.postMessage({id,ok:false,error:String(error),unknown:!!error.unknown});}
    };port.start();port.postMessage({event:'connected'});
  });
  const ready=()=>{const controller=parent!==window?parent:nativeOpener;if(controller)nativePostMessage.call(controller,{type:'rb-execution-ready',session:config.session,url:location.href},'*');};
  addEventListener('pagehide',()=>{for(const life of progressiveMedia.values())life.close();for(const child of liveChildren()){releaseChild(child);if(child.kind==='window')child.window.close();}childWindows.clear();port?.postMessage({event:'leaving'});port?.close();port=null;});
  addEventListener('pageshow',e=>{if(e.persisted)ready();});
  ready();
})();
