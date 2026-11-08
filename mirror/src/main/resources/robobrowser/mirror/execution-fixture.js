(() => {
  const run=new URLSearchParams(location.search).get('run'),identity=crypto.randomUUID();
  const state={identity,text:'',count:0,moves:0,last:null,writes:0,frame:0,maxInputDelayMs:0};
  const pad=document.querySelector('#pad'),canvas=document.querySelector('canvas'),ctx=canvas.getContext('2d'),cursor=document.querySelector('#cursor');
  new ResizeObserver(()=>{canvas.width=pad.clientWidth;canvas.height=pad.clientHeight;}).observe(pad);
  pad.addEventListener('pointermove',e=>{const r=pad.getBoundingClientRect(),x=e.clientX-r.x,y=e.clientY-r.y;state.moves++;state.last={x,y};state.maxInputDelayMs=Math.max(state.maxInputDelayMs,performance.now()-e.timeStamp);cursor.style.transform=`translate(${x-12}px,${y-12}px)`;ctx.fillStyle='#50efb7';ctx.fillRect(x-3,y-3,6,6);});
  document.querySelector('#entry').oninput=e=>state.text=e.target.value;
  document.querySelector('#increment').onclick=async()=>{state.count++;document.querySelector('#count').textContent=state.count;const r=await fetch('/fixture-execution-action?run='+run,{method:'POST',body:JSON.stringify({identity,count:state.count})});state.writes=(await r.json()).writes;};
  function frame(){state.frame++;document.querySelector('#details').textContent=JSON.stringify(state,null,2);requestAnimationFrame(frame);}requestAnimationFrame(frame);
  window.executionFixture={state:()=>({...state})};
})();
