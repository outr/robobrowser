(()=>{
  const field=document.createElement('input');
  field.id='input-baseline';
  field.style.cssText='position:fixed;left:20px;top:70px;width:400px;z-index:9999';
  document.body.append(field);
  for(let i=0;i<8;i++){
    const bit=document.createElement('div');bit.id='input-bit'+i;
    bit.style.cssText='position:fixed;left:'+(i*12)+'px;top:32px;width:12px;height:12px;background:black;z-index:9999';
    document.body.append(bit);
  }
  field.oninput=()=>{
    for(let i=0;i<8;i++)document.querySelector('#input-bit'+i).style.background=(field.value.length&(1<<i))?'white':'black';
  };
  field.focus();return true;
})()
