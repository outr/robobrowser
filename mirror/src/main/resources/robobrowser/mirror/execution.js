'use strict';
const token=location.hash.slice(1)||sessionStorage.getItem('rbMirrorToken')||'';
if(token)sessionStorage.setItem('rbMirrorToken',token);history.replaceState(null,'',location.pathname);
const $=id=>document.getElementById(id),frame=$('page');
let session=null,port=null,requestId=0,generation=0,lifecycle=0,busy=false,polling=false;const pending=new Map();
let agentEpoch=null,relayVersion=0;
function remember(){
  if(!session)return;sessionStorage.setItem('rbExecutionSession',session.id);
  if(session.profile)sessionStorage.setItem('rbExecutionProfile',session.profile);else sessionStorage.removeItem('rbExecutionProfile');
  sessionStorage.setItem('rbExecutionURL',session.url);
}
function focusPolicy(){port?.postMessage({op:'focusPolicy',allow:!document.activeElement?.matches('input,textarea,select')});}
document.addEventListener('focusin',focusPolicy);
const fail=e=>{$('error').textContent=e.message||String(e);};
async function api(command){const r=await fetch('/execution/api',{method:'POST',headers:{Authorization:'Bearer '+token,'Content-Type':'application/json'},body:JSON.stringify(command)});const d=await r.json();if(!d.ok)throw Error(d.error);return d.result;}
function detach(){if(session&&agentEpoch!==null)api({op:'agentDetach',id:session.id,epoch:agentEpoch}).catch(()=>{});relayVersion++;agentEpoch=null;if(port)port.onmessage=null;port?.close();port=null;for(const p of pending.values()){clearTimeout(p.timer);p.reject(Error('Browser session changed'));}pending.clear();}
function local(op,args={}){if(!port)return Promise.reject(Error('Local page not connected'));return new Promise((resolve,reject)=>{const id=++requestId,timer=setTimeout(()=>{pending.delete(id);const error=Error('Local command timed out; outcome unknown');error.code='timeout';reject(error);},5000);pending.set(id,{resolve,reject,timer});port.postMessage({id,op,...args});});}
async function agentCommand(command,requestId=crypto.randomUUID()){if(!session||agentEpoch===null)throw Error('Executing page not connected');return api({op:'agent',id:session.id,epoch:agentEpoch,requestId,command});}
async function agentClick(selector){return agentCommand({op:'click',selector});}
addEventListener('message',async event=>{
  if(!session||event.source!==frame.contentWindow||event.data?.type!=='rb-execution-ready'||event.data.session!==session.id)return;
  try{if(new URL(event.data.url).origin!==event.origin)return;}catch{return;}
  const current=session,stamp=++generation;
  try{
    const saved=await api({op:'resolveLocation',id:current.id,url:event.data.url});
    if(current!==session||stamp!==generation)return;
    Object.assign(session,saved);remember();if(document.activeElement!==$('url'))$('url').value=saved.url;
  }catch{return;}
  detach();const channel=new MessageChannel();port=channel.port1;
  port.onmessage=event=>{if(port!==channel.port1||session!==current)return;const d=event.data;
    // An old page can finish leaving while its replacement's location check is pending.
    // Retire the old relay without invalidating that newer handshake.
    if(d.event==='leaving'){if(generation===stamp)generation++;detach();$('status').textContent='Navigating';return;}
    if(d.event==='connected'){$('status').textContent='Local scripts running';focusPolicy();startRelay().catch(fail);poll();return;}
    const p=pending.get(d.id);if(p){pending.delete(d.id);clearTimeout(p.timer);d.ok?p.resolve(d.result):p.reject(Object.assign(Error(d.error),d.unknown?{code:'timeout'}:{}));}
  };port.start();frame.contentWindow.postMessage({type:'rb-execution-connect',session:session.id},event.origin,[channel.port2]);
});
async function startRelay(){
  const current=session,version=++relayVersion,destination=current.local;
  let attached;try{attached=await api({op:'agentAttach',id:current.id});}catch(e){if(version===relayVersion&&current===session)fail(e);return;}
  if(version!==relayVersion||current!==session){api({op:'agentDetach',id:current.id,epoch:attached.epoch}).catch(()=>{});return;}
  let ack=null;
  try{
    await api({op:'location',id:current.id,epoch:attached.epoch,url:destination});
    if(version!==relayVersion||current!==session)return;
    agentEpoch=attached.epoch;
    while(version===relayVersion&&current===session&&port){
      const job=await api({op:'agentExchange',id:current.id,epoch:attached.epoch,...(ack?{ack}:{})});ack=null;
      if(version!==relayVersion||current!==session)break;
      if(job.command){
        try{const {op,...args}=job.command;ack={requestId:job.requestId,ok:true,result:await local(op,args)};}
        catch(error){ack={requestId:job.requestId,ok:false,error:String(error),unknown:error.code==='timeout'};}
      }else await new Promise(resolve=>setTimeout(resolve,50));
    }
  }catch(e){if(version===relayVersion&&current===session)fail(e);}
  finally{if(current===session)api({op:'agentDetach',id:current.id,epoch:attached.epoch}).catch(()=>{});}
}
async function close(invalidate=true){
  if(session&&port)await local('storageFlush');
  if(invalidate){lifecycle++;busy=false;}const operation=lifecycle;
  for(const key of ['rbExecutionSession','rbExecutionProfile','rbExecutionURL'])sessionStorage.removeItem(key);
  const old=session;session=null;generation++;detach();frame.src='about:blank';
  $('status').textContent='Closed';$('local').textContent='Local execution: closed';$('remote').textContent='Agent: disconnected';
  // Complete local teardown immediately; an old server response must not change a replacement page.
  if(old)try{await api({op:'close',id:old.id});}catch(error){if(operation===lifecycle)throw error;}
}
function normalize(url){if(!url)return '';if(!/^[a-z][a-z0-9+.-]*:\/\//i.test(url))url=(/^(localhost|127\.0\.0\.1)(:|\/|$)/i.test(url)?'http://':'https://')+url;const u=new URL(url);if(!['http:','https:'].includes(u.protocol))throw Error('Use an HTTP or HTTPS URL');return u.href;}
async function open(url){if(busy)return;const operation=++lifecycle;busy=true;$('error').textContent='';$('status').textContent='Starting browser';try{url=normalize(url);
  if(url&&session?.mode==='local'&&($('profile').value.trim()||null)===session.profile){
    const current=session;await local('storageFlush');if(current!==session)return;generation++;detach();
    try{
      const saved=await api({op:'navigate',id:current.id,url});
      if(current!==session)return;Object.assign(session,saved);remember();$('url').value=saved.url;frame.src=saved.local;$('status').textContent='Loading original scripts';return;
    }catch(error){
      if(current===session)frame.contentWindow.postMessage({type:'rb-execution-reconnect',session:current.id},new URL(current.local).origin);
      throw error;
    }
  }
  await close(false);if(operation!==lifecycle)return;const opened=await api({op:'open',url,mode:'local',profile:$('profile').value.trim()});
  if(operation!==lifecycle){await api({op:'close',id:opened.id});return;}
  session=opened;remember();generation++;$('url').value=session.url;frame.src=session.local;$('status').textContent='Loading original scripts';}catch(e){if(operation===lifecycle)fail(e);}finally{if(operation===lifecycle)busy=false;}}
$('open').onclick=()=>open($('url').value.trim());$('url').onkeydown=e=>{if(e.key==='Enter')open($('url').value.trim());};
$('github').onclick=()=>open('https://github.com/');$('fixture').onclick=()=>open('');$('close').onclick=()=>close().catch(fail);
$('agent-click').onclick=()=>agentClick('#increment').catch(fail);
$('eval').onclick=async()=>{try{$('result').textContent=JSON.stringify(await agentCommand({op:'eval',code:$('code').value}),null,2);}catch(e){fail(e);}};
async function poll(){
  if(!session||!port||polling)return;polling=true;const current=session,epoch=generation,connection=port;
  try{
    const [l,r,c]=await Promise.all([local('state'),api({op:'agentState',id:current.id}),api({op:'counts',id:current.id})]);
    if(epoch!==generation||connection!==port)return;
    if(l.storage?.error)$('error').textContent=l.storage.error;
    if(l.localURL&&l.localURL!==session.local){
      const saved=await api({op:'location',id:current.id,epoch:agentEpoch,url:l.localURL});
      if(epoch!==generation||current!==session||connection!==port)return;
      Object.assign(session,saved);remember();if(document.activeElement!==$('url'))$('url').value=saved.url;
    }
    $('local').textContent='Local execution\n'+JSON.stringify(l,null,2);$('remote').textContent='Agent connection\n'+JSON.stringify(r,null,2);$('counts').textContent='Shared fixture writes: '+c.writes;
  }catch(e){if(epoch===generation&&connection===port)fail(e);}finally{polling=false;}
}
setInterval(poll,1000);

(async()=>{
  const id=sessionStorage.getItem('rbExecutionSession'),profile=sessionStorage.getItem('rbExecutionProfile'),url=sessionStorage.getItem('rbExecutionURL')||'';if(!id)return;
  $('profile').value=profile||'';
  try{
    const saved=await api({op:'resume',id});if(session||busy)return;
    session=saved;remember();$('profile').value=saved.profile||'';generation++;$('url').value=saved.url;
    frame.src=saved.local;$('status').textContent='Reconnecting executing page';
  }catch(e){sessionStorage.removeItem('rbExecutionSession');if(profile&&!session&&!busy){await open(url);}else $('status').textContent='Previous browser session expired';}
})();
