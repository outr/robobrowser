/* Restore before any website script, then checkpoint native storage after committed changes. */
(async () => {
  'use strict';
  const source=JSON.parse(document.getElementById('rb-storage-document').textContent);
  const rawLocal=window.localStorage,rawSession=window.sessionStorage;
  const get=Storage.prototype.getItem,set=Storage.prototype.setItem,remove=Storage.prototype.removeItem,clear=Storage.prototype.clear,key=Storage.prototype.key;
  const marker='__rb_storage_checkpoint_v1',namespaceKey='__rb_storage_namespace_v1';
  const factory=window.indexedDB,open=factory.open.bind(factory),drop=factory.deleteDatabase.bind(factory),list=factory.databases.bind(factory);
  const transaction=IDBDatabase.prototype.transaction,nativeFetch=window.fetch.bind(window);
  let revision=0,dirty=false,serial=0,timer=null,running=null,lastError=null,mutation=null;
  const inheritedNamespace=get.call(rawSession,namespaceKey);
  const namespace=window.opener&&(!inheritedNamespace||inheritedNamespace==='main')?crypto.randomUUID():(inheritedNamespace||'main');set.call(rawSession,namespaceKey,namespace);
  function meta(){try{return JSON.parse(get.call(rawLocal,marker)||'null');}catch{return null;}}
  function mark(){set.call(rawLocal,marker,JSON.stringify({revision,dirty,mutation}));}
  async function request(body,keepalive=false){
    const text=JSON.stringify(body);if(new TextEncoder().encode(text).length>12*1024*1024)throw Error('Persistent browser storage exceeds 12 MiB');
    const response=await nativeFetch('/__rb_execution/storage',{method:'POST',headers:{'Content-Type':'application/json'},body:text,keepalive});
    if(!response.ok)throw Error('Storage synchronization refused ('+response.status+'); local changes retained');
    return response.json();
  }
  function values(storage){const data=Object.create(null);for(let i=0;i<storage.length;i++){const k=key.call(storage,i);if(k!==marker&&k!==namespaceKey)data[k]=get.call(storage,k);}return data;}
  function restoreValues(storage,data){clear.call(storage);for(const [k,v] of Object.entries(data))set.call(storage,k,v);}
  function done(tx){return new Promise((resolve,reject)=>{tx.addEventListener('complete',resolve,{once:true});tx.addEventListener('abort',()=>reject(tx.error||Error('Database transaction aborted')),{once:true});});}
  function result(req){return new Promise((resolve,reject)=>{req.addEventListener('success',()=>resolve(req.result),{once:true});req.addEventListener('error',()=>reject(req.error),{once:true});req.addEventListener('blocked',()=>reject(Error('Database restoration blocked by another page')),{once:true});});}
  // A reference graph preserves cycles, shared objects, binary values and structured-clone types.
  async function encode(value){
    const nodes=[],seen=new Map();
    async function visit(v){
      if(v===null||typeof v==='string'||typeof v==='boolean')return {v};
      if(typeof v==='number')return Object.is(v,-0)?{t:'number',v:'-0'}:Number.isFinite(v)?{v}:{t:'number',v:String(v)};
      if(typeof v==='undefined')return {t:'undefined'};
      if(typeof v==='bigint')return {t:'bigint',v:String(v)};
      if(typeof v!=='object')throw Error('Unsupported database value');
      if(seen.has(v))return {r:seen.get(v)};
      const id=nodes.length;seen.set(v,id);const n={};nodes.push(n);
      if(v instanceof Date){n.t='date';n.v=String(v.getTime());}
      else if(v instanceof RegExp){n.t='regexp';n.v=[v.source,v.flags];}
      else if(v instanceof ArrayBuffer){n.t='buffer';let b='';for(const x of new Uint8Array(v))b+=String.fromCharCode(x);n.v=btoa(b);}
      else if(ArrayBuffer.isView(v)){n.t='view';n.name=v.constructor.name;n.buffer=await visit(v.buffer);n.offset=v.byteOffset;n.length=v instanceof DataView?v.byteLength:v.length;}
      else if(v instanceof Blob){n.t=v instanceof File?'file':'blob';n.type=v.type;n.buffer=await visit(await v.arrayBuffer());if(v instanceof File){n.name=v.name;n.modified=v.lastModified;}}
      else if(v instanceof Map){n.t='map';n.v=[];for(const [k,x] of v)n.v.push([await visit(k),await visit(x)]);}
      else if(v instanceof Set){n.t='set';n.v=[];for(const x of v)n.v.push(await visit(x));}
      else if(Array.isArray(v)||Object.getPrototypeOf(v)===Object.prototype||Object.getPrototypeOf(v)===null){n.t=Array.isArray(v)?'array':'object';n.length=v.length;n.v=[];for(const k of Object.keys(v))n.v.push([k,await visit(v[k])]);}
      else throw Error('Unsupported IndexedDB value: '+Object.prototype.toString.call(v));
      return {r:id};
    }
    return {root:await visit(value),nodes};
  }
  function decode(graph){
    const values=new Map();
    function read(x){
      if('v' in x&&!x.t)return x.v;
      if(x.t==='undefined')return undefined;
      if(x.t==='bigint')return BigInt(x.v);
      if(x.t==='number')return Number(x.v);
      if(values.has(x.r))return values.get(x.r);
      const n=graph.nodes[x.r];let v;
      if(n.t==='date')v=new Date(Number(n.v));
      else if(n.t==='regexp')v=new RegExp(...n.v);
      else if(n.t==='buffer')v=Uint8Array.from(atob(n.v),x=>x.charCodeAt(0)).buffer;
      else if(n.t==='view'){
        const types={Int8Array,Uint8Array,Uint8ClampedArray,Int16Array,Uint16Array,Int32Array,Uint32Array,Float32Array,Float64Array,BigInt64Array,BigUint64Array,DataView};
        if(!types[n.name])throw Error('Unsupported database view');v=new types[n.name](read(n.buffer),n.offset,n.length);
      }else if(n.t==='blob'||n.t==='file'){const parts=[read(n.buffer)];v=n.t==='file'?new File(parts,n.name,{type:n.type,lastModified:n.modified}):new Blob(parts,{type:n.type});}
      else if(n.t==='map')v=new Map();else if(n.t==='set')v=new Set();else if(n.t==='array')v=new Array(n.length);else if(n.t==='object')v={};else throw Error('Invalid database checkpoint');
      values.set(x.r,v);
      if(n.t==='map')for(const [k,x] of n.v)v.set(read(k),read(x));
      else if(n.t==='set')for(const x of n.v)v.add(read(x));
      else if(n.t==='array'||n.t==='object')for(const [k,x] of n.v)Object.defineProperty(v,k,{value:read(x),writable:true,enumerable:true,configurable:true});
      return v;
    }
    return read(graph.root);
  }
  async function databases(){
    const output=[];
    for(const info of (await list()).sort((a,b)=>a.name.localeCompare(b.name))){
      const db=await result(open(info.name));
      try{
        const stores=[];
        for(const name of [...db.objectStoreNames]){
          const tx=transaction.call(db,name,'readonly'),completion=done(tx),store=tx.objectStore(name),records=[];
          const definition={name,keyPath:store.keyPath,autoIncrement:store.autoIncrement,indexes:[...store.indexNames].map(name=>{const i=store.index(name);return {name,keyPath:i.keyPath,unique:i.unique,multiEntry:i.multiEntry};}),records};
          await new Promise((resolve,reject)=>{const cursor=store.openCursor();cursor.onerror=()=>reject(cursor.error);cursor.onsuccess=()=>{if(!cursor.result)return resolve();records.push([cursor.result.key,cursor.result.value]);cursor.result.continue();};});await completion;
          definition.records=await Promise.all(records.map(encode));
          if(definition.autoIncrement){
            definition.nextKey=await new Promise((resolve,reject)=>{const tx=transaction.call(db,name,'readwrite'),r=tx.objectStore(name).add({});r.onsuccess=()=>{const key=r.result;tx.onabort=()=>resolve(key);tx.abort();};r.onerror=()=>reject(r.error);});
          }
          stores.push(definition);
        }
        output.push({name:db.name,version:db.version,stores});
      }finally{db.close();}
    }
    return output;
  }
  async function restoreDatabases(saved){
    for(const info of await list())await result(drop(info.name));
    for(const entry of saved){
      const req=open(entry.name,entry.version);
      req.onupgradeneeded=()=>{for(const s of entry.stores){const store=req.result.createObjectStore(s.name,{keyPath:s.keyPath,autoIncrement:s.autoIncrement});for(const i of s.indexes)store.createIndex(i.name,i.keyPath,{unique:i.unique,multiEntry:i.multiEntry});}};
      const db=await result(req);
      try{
        if(!entry.stores.length)continue;
        const tx=transaction.call(db,entry.stores.map(s=>s.name),'readwrite'),completion=done(tx);
        for(const s of entry.stores){
          const store=tx.objectStore(s.name);
          for(const record of s.records){const [k,v]=decode(record);if(s.keyPath===null)store.put(v,k);else store.put(v);}
          if(s.autoIncrement&&s.nextKey>1){
            // Advancing a generator and deleting the temporary record retains deleted high keys.
            const k=s.nextKey-1,existing=s.records.some(r=>decode(r)[0]===k);
            if(!existing){let value={};if(s.keyPath!==null){let o=value;const path=s.keyPath.split('.');for(const part of path.slice(0,-1))o=o[part]={};o[path.at(-1)]=k;store.put(value);}else store.put(value,k);store.delete(k);}
          }
        }
        await completion;
      }finally{db.close();}
    }
  }
  function report(error){lastError=String(error);console.error('RoboBrowser storage sync:',lastError);window.dispatchEvent(new CustomEvent('robobrowser-storage-error',{detail:{message:lastError}}));}
  function changed(){revision=Math.max(revision,meta()?.revision||0);serial++;mutation=crypto.randomUUID();dirty=true;mark();clearTimeout(timer);timer=setTimeout(()=>flush().catch(report),150);}
  async function flush(){
    if(running){await running;if(dirty)return flush();return;}
    const checkpoint=async()=>{while(dirty){
      const shared=meta();revision=Math.max(revision,shared?.revision||0);
      const stamp=shared?.mutation,payload={op:'write',revision,namespace,local:values(rawLocal),session:values(rawSession),databases:await databases()};
      if(stamp!==meta()?.mutation)continue;
      const saved=await request(payload);revision=saved.revision;mutation=meta()?.mutation;dirty=stamp!==mutation;mark();lastError=null;
    }};
    running=navigator.locks?navigator.locks.request('robobrowser-storage-checkpoint',checkpoint):checkpoint();
    try{await running;}finally{running=null;}
  }
  try{
    const saved=await request({op:'read'}),previous=meta();revision=previous?.revision??saved.revision;mutation=previous?.mutation;
    if(previous?.dirty){dirty=true;await flush();}
    else if(!previous||previous.revision!==saved.revision){
      revision=saved.revision;
      // Import existing browser-only storage once when upgrading an old profile.
      if(saved.revision===0&&(Object.keys(values(rawLocal)).length||Object.keys(values(rawSession)).length||(await list()).length)){dirty=true;await flush();}
      else {await restoreDatabases(saved.databases);restoreValues(rawLocal,saved.local);}
    }
    if(!get.call(rawSession,marker))restoreValues(rawSession,saved.sessions[namespace]||{});
    set.call(rawSession,namespaceKey,namespace);set.call(rawSession,marker,'restored');mark();
    document.open();
    const proxies=new WeakMap();
    for(const name of ['localStorage','sessionStorage']){
      const raw=name==='localStorage'?rawLocal:rawSession;
      const proxy=new Proxy(raw,{ownKeys:target=>Reflect.ownKeys(target).filter(k=>k!==marker&&k!==namespaceKey),get(target,k){if(k===marker||k===namespaceKey)return undefined;if(k==='length')return Object.keys(values(target)).length;const v=Reflect.get(target,k,target);return typeof v==='function'?v.bind(target):v;},set(target,k,v){set.call(target,String(k),String(v));changed();return true;},deleteProperty(target,k){remove.call(target,String(k));changed();return true;},defineProperty(target,k,d){if(!('value' in d))throw TypeError('Storage requires a value');set.call(target,String(k),String(d.value));changed();return true;}});
      proxies.set(proxy,raw);Object.defineProperty(window,name,{get:()=>proxy,configurable:true});
    }
    Storage.prototype.getItem=function(k){return String(k)===marker||String(k)===namespaceKey?null:get.call(proxies.get(this)||this,k);};
    Storage.prototype.key=function(index){return Object.keys(values(proxies.get(this)||this))[Number(index)>>>0]??null;};
    for(const [name,native] of [['setItem',set],['removeItem',remove],['clear',clear]])Storage.prototype[name]=function(...args){const raw=proxies.get(this)||this;revision=Math.max(revision,meta()?.revision||0);const value=native.apply(raw,args);if(raw===rawLocal||raw===rawSession){if(raw===rawSession){set.call(rawSession,namespaceKey,namespace);set.call(rawSession,marker,'restored');}changed();}return value;};
    IDBDatabase.prototype.transaction=function(...args){const tx=transaction.apply(this,args);if(tx.mode!=='readonly')tx.addEventListener('complete',changed,{once:true});return tx;};
    factory.open=function(...args){const req=open(...args);req.addEventListener('upgradeneeded',()=>req.transaction.addEventListener('complete',changed,{once:true}));return req;};
    factory.deleteDatabase=function(...args){const req=drop(...args);req.addEventListener('success',changed,{once:true});return req;};
    addEventListener('storage',e=>{if((e.storageArea===rawLocal||e.storageArea===rawSession)&&e.key!==marker&&e.key!==namespaceKey)changed();});
    addEventListener('pagehide',()=>{if(dirty)flush().catch(report);});
    Object.defineProperty(window,'__rbStorageSync',{value:{flush,status:()=>({pending:dirty,error:lastError,revision})},configurable:false});
    document.write(source);document.close();
  }catch(error){report(error);document.body||document.documentElement.appendChild(document.createElement('body'));document.body.textContent='Browser storage could not be restored. Local data was retained. '+lastError;}
})();
