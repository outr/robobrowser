function installExecutionSockets({origin,base,fetch:transport}) {
  const active=new Set(),encoder=new TextEncoder();
  const request=async command=>{
    const response=await transport(origin+'/__rb_execution/socket',{method:'POST',body:JSON.stringify(command)});
    if(!response.ok)throw Error('WebSocket relay unavailable');return response.json();
  };
  const encode=bytes=>{let text='';for(let i=0;i<bytes.length;i+=8192)text+=String.fromCharCode(...bytes.subarray(i,i+8192));return btoa(text);};
  class RelayedWebSocket extends EventTarget {
    static CONNECTING=0;static OPEN=1;static CLOSING=2;static CLOSED=3;
    #url;#protocol='';#extensions='';#state=0;#buffered=0;#binaryType='blob';#id=null;#sequence=0;#chain=Promise.resolve();#closing=null;
    onopen=null;onmessage=null;onerror=null;onclose=null;
    get url(){return this.#url;}get protocol(){return this.#protocol;}get extensions(){return this.#extensions;}
    get readyState(){return this.#state;}get bufferedAmount(){return this.#buffered;}
    get binaryType(){return this.#binaryType;}set binaryType(value){if(value==='blob'||value==='arraybuffer')this.#binaryType=value;}
    constructor(url,protocols=[]) {
      super();let u;try{u=new URL(url,base());}catch{throw new DOMException('Invalid WebSocket URL','SyntaxError');}
      if(u.protocol==='http:')u.protocol='ws:';if(u.protocol==='https:')u.protocol='wss:';
      if(!['ws:','wss:'].includes(u.protocol)||u.hash||u.username||u.password)throw new DOMException('Invalid WebSocket URL','SyntaxError');
      const list=typeof protocols==='string'?[protocols]:Array.from(protocols,String);
      if(list.length>32||new Set(list).size!==list.length||list.some(p=>! /^[!#$%&'*+.^_`|~0-9A-Za-z-]{1,128}$/.test(p)))throw new DOMException('Invalid WebSocket protocols','SyntaxError');
      this.#url=u.href;active.add(this);this.#connect(list).catch(()=>this.#failed());
    }
    #emit(event){this.dispatchEvent(event);const handler=this['on'+event.type];if(typeof handler==='function'){try{handler.call(this,event);}catch(error){setTimeout(()=>{throw error;});}}}
    #finish(code,reason,wasClean){if(this.#state===3)return;this.#state=3;active.delete(this);this.#emit(new CloseEvent('close',{code,reason,wasClean}));}
    #failed(){if(this.#state===3)return;this.#emit(new Event('error'));this.#finish(1006,'',false);if(this.#id)request({op:'dispose',id:this.#id}).catch(()=>{});}
    async #connect(protocols){
      const result=await request({op:'open',url:this.#url,protocols});this.#id=result.id;
      // Navigation may retire the document while channel allocation is in flight.
      if(this.#state===3){await request({op:'dispose',id:this.#id});return;}
      if(this.#closing)await request({op:'close',id:this.#id,...this.#closing});
      while(this.#state!==3){
        const {events}=await request({op:'poll',id:this.#id});
        if(this.#state===3)return;
        for(const event of events){
          if(this.#state===3)break;
          if(event.event==='open'&&this.#state===0){this.#protocol=event.protocol;this.#extensions=event.extensions;this.#state=1;this.#emit(new Event('open'));}
          else if(event.event==='message'&&this.#state===1){
            let data=event.text;
            if(event.binary!==undefined){const bytes=Uint8Array.from(atob(event.binary),c=>c.charCodeAt(0));data=this.#binaryType==='arraybuffer'?bytes.buffer:new Blob([bytes]);}
            this.#emit(new MessageEvent('message',{data,origin:new URL(this.#url).origin}));
          }else if(event.event==='error')this.#emit(new Event('error'));
          else if(event.event==='close')this.#finish(event.code,event.reason,event.clean);
        }
      }
    }
    send(data){
      if(this.#state===0)throw new DOMException('WebSocket is connecting','InvalidStateError');
      let payload,size;
      if(data instanceof Blob){payload=data;size=data.size;}
      else if(data instanceof ArrayBuffer||ArrayBuffer.isView(data)){payload=data instanceof ArrayBuffer?new Uint8Array(data).slice():new Uint8Array(data.buffer,data.byteOffset,data.byteLength).slice();size=payload.byteLength;}
      else{payload=String(data).toWellFormed();size=encoder.encode(payload).length;}
      this.#buffered+=size;
      if(this.#state!==1)return;
      if(size>1024*1024||this.#buffered>4*1024*1024){this.#failed();return;}
      const sequence=++this.#sequence;
      this.#chain=this.#chain.then(async()=>{
        if(this.#state===3)return;
        const value=payload instanceof Blob?new Uint8Array(await payload.arrayBuffer()):payload;
        await request({op:'send',id:this.#id,sequence,...(typeof value==='string'?{text:value}:{binary:encode(value)})});
        this.#buffered-=size;
      }).catch(()=>this.#failed());
    }
    close(code=1000,reason=''){
      code=Number(code);reason=String(reason).toWellFormed();
      if(code!==1000&&(!Number.isInteger(code)||code<3000||code>4999))throw new DOMException('Invalid close code','InvalidAccessError');
      if(encoder.encode(reason).length>123)throw new DOMException('Close reason exceeds 123 bytes','SyntaxError');
      if(this.#state>=2)return;this.#state=2;this.#closing={code,reason};
      if(this.#id)this.#chain=this.#chain.then(()=>request({op:'close',id:this.#id,code,reason})).catch(()=>this.#failed());
    }
    dispose(){if(this.#state===3)return;if(this.#id)transport(origin+'/__rb_execution/socket',{method:'POST',body:JSON.stringify({op:'dispose',id:this.#id}),keepalive:true}).catch(()=>{});this.#finish(1001,'Document closed',false);}
  }
  for(const [name,value] of Object.entries({CONNECTING:0,OPEN:1,CLOSING:2,CLOSED:3}))Object.defineProperty(RelayedWebSocket.prototype,name,{value});
  Object.defineProperty(RelayedWebSocket.prototype,Symbol.toStringTag,{value:'WebSocket'});
  window.WebSocket=RelayedWebSocket;
  addEventListener('pagehide',()=>{for(const socket of active)socket.dispose();});
}
