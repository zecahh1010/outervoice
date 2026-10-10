const $=s=>document.querySelector(s), clone=o=>JSON.parse(JSON.stringify(o));
const colors=['#a9d8ff','#b7e8cf','#ffe69a','#ffcbaa','#f49a97','#d6c4f4','#b8e8ea'], colorNames=['Sky','Mint','Yellow','Peach','Red','Lavender','Aqua'], glyphs=['\ue814','\ue87d','\ue7f4'], iconNames=['Angry','Thank You','Warmly Remind'];
colors.push('#d5efb1','#f6c1df','#bcc7f2');
colorNames.push('Lime','Pink','Indigo');
// Local vector icons inherit each button's configured foreground color and size.
const vectorIcon=paths=>`<svg viewBox="0 0 32 32" aria-hidden="true" focusable="false" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">${paths}</svg>`;
const face=details=>vectorIcon('<circle cx="16" cy="16" r="13"/>'+details);
glyphs.push(
 face('<path d="m7 13 4-4 3 4m4 0 3-4 4 4M8 18h16a8 8 0 0 1-16 0Zm2 3h12"/>'),
 face('<path d="m6 8 7 4m13-4-7 4m-7 2h-3m12 0h3"/><rect x="7" y="19" width="18" height="6" rx="2"/><path d="M13 19v6m6-6v6m-9-3h12"/>'),
 face('<circle cx="11" cy="13" r="1" fill="currentColor"/><circle cx="21" cy="13" r="1" fill="currentColor"/><path d="M9 19q7 9 14 0"/>'),
 face('<path d="M7 13q4-5 8 0M9 20q7 7 14-1"/><circle cx="22" cy="13" r="1" fill="currentColor"/>'),
 face('<path d="m8 10 5-2m6 0 5 2M10 24q6-7 12 0"/><circle cx="11" cy="14" r="1" fill="currentColor"/><circle cx="21" cy="14" r="1" fill="currentColor"/><path d="M7 16q-5 6 0 6t0-6Z"/>'),
 face('<circle cx="11" cy="12" r="2"/><circle cx="21" cy="12" r="2"/><ellipse cx="16" cy="22" rx="4" ry="5"/>'),
 face('<path d="M7 13q4 4 8 0m3 0q4 4 8 0M12 22q4 3 8 0"/>'),
 vectorIcon('<path d="m11 3-8 8v10l8 8h10l8-8V11l-8-8Z"/><path d="M16 9v10"/><circle cx="16" cy="24" r="1" fill="currentColor"/>'),
 vectorIcon('<path d="m4 28 5-17 12 12-17 5Zm2-7 6 5m-3-10 8 8M16 4l1 5m6-5-3 8m9 2-6 2M14 11l7 7"/><circle cx="27" cy="5" r="1" fill="currentColor"/><circle cx="27" cy="23" r="1" fill="currentColor"/>')
);
iconNames.push('Funny','Extreme Angry','Happy','Friendly','Sorry','Surprised','Calm','Urgent','Celebration');
const initial={sounds:['Welcome','Please wait','Thank you','Announcement','Meeting starting'].map((name,i)=>({id:'s'+i,name})),config:{enabled:false,size:88,gap:12,percent:50,bubbleSize:88,autoMinimize:false,autoSeconds:30,items:[{id:'live',selected:true,color:0,icon:0},...[0,1,2,3,4].map(i=>({id:'s'+i,selected:i<3,color:i,icon:i%3}))]},position:{x:120,y:120},bubble:{x:120,y:120},minimized:false};
let state;try{state=JSON.parse(localStorage.getItem('outervoice-simulator'))||clone(initial)}catch{state=clone(initial)}
state.config.bubbleSize ??= 88;
state.config.autoMinimize ??= false;
state.config.autoSeconds ??= 30;
state.stripScroll ??= 0;
let autoTimer=null,panelPointerActive=false;
let overlayRevision=0,transitioning=false;
let settingsTab='buttons';
let page='home',draft=null,editing='live',actual=false,other=false,pending=null,audio=null,holding=false,timer=null,toastTimer,blobs=new Map();
const esc=s=>String(s).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const icon=(g,action,label)=>`<button class="mi icon" data-action="${action}" aria-label="${label}" title="${label}">${g}</button>`;
function persist(){localStorage.setItem('outervoice-simulator',JSON.stringify(state))}
function toast(s){$('#toast').textContent=s;$('#toast').style.display='block';clearTimeout(toastTimer);toastTimer=setTimeout(()=>$('#toast').style.display='none',2600)}
function name(id){return id==='live'?'Live Speak':state.sounds.find(s=>s.id===id)?.name||'Sound'}
function buttonColor(item){return item.id==='live'?(item.customColor==null?'var(--teal)':colors[item.customColor]):colors[item.color]}
function buttonForeground(item){return item.id==='live'&&item.customColor==null?'white':'var(--bg)'}
function liveItem(config){return config.items.find(i=>i.id==='live')}
function liveOnly(config){return config.items.filter(i=>i.selected).length===1&&liveItem(config)?.selected}
function header(title,back=true){return `<header class="header">${back?icon('\ue5c4','back','Back'):''}<h1>${title}</h1>${back?'':icon('\ue913','settings','Floating Panel')+icon('\ue050','audio','Audio settings')+'<span class="route">Outer speaker</span>'+icon('\ue88e','info','About')+icon('\ue8ac','exit','Exit app')}</header>`}
function navigate(next){stopAudio();holding=false;clearTimeout(timer);other=false;page=next;draft=next==='settings'?clone(state.config):next==='edit'?clone(state.sounds):null;if(next==='add')pending=null;if(next==='settings')settingsTab='buttons';render()}
function render(){const screen=$('#screen');if(other){screen.innerHTML=header('Settings',false)+'<div class="otherapp"><h2>Android Settings</h2><article>Network & internet<br><small>Wi-Fi, mobile, data usage</small></article><article>Connected devices<br><small>Bluetooth, USB</small></article><article>Apps & notifications<br><small>Permissions, default apps</small></article><article>Sound<br><small>Volume, notification sounds</small></article></div>'}
else if(page==='home'){screen.innerHTML=header('Outer <span class="accent">Voice</span>',false)+`<div class="home"><section class="live"><h2>Live Speaking</h2><div class="live-controls"><button class="mi mic" data-hold aria-label="Hold to Speak">\ue029</button><h3>Hold to Speak</h3><span id="live-status" class="muted">Hold, speak, then release to play</span><div class="meter">${'<i></i>'.repeat(11)}</div></div></section><section class="saved"><div class="savedhead"><h2>Saved sounds</h2><button data-action="edit">Edit List</button><button class="primary" data-action="add">+ Add Sound</button></div><div class="scroll">${state.sounds.map(s=>`<div class="soundrow"><button class="mi play" data-play="${s.id}" aria-label="Play ${esc(s.name)}">\ue037</button><span>${esc(s.name)}</span></div>`).join('')||'<p class="muted">No saved sounds yet.</p>'}</div></section></div>`}
else if(page==='add'){screen.innerHTML=header('Add Sound')+`<div class="form"><label for="sound-name">Button name</label><input id="sound-name" class="nameinput" placeholder="Enter a name" maxlength="60"><label>Import or record</label><div class="importbuttons"><button data-action="import"><span class="mi">\ue2c6</span>Import Sound</button><button data-action="record"><span class="mi">\ue029</span>Record</button></div><div class="importstatus"><span id="import-name" class="muted">WAV, MP3 and other audio files</span><button id="test-play" class="test-play" data-action="preview" disabled title="Import or record a sound to enable playback"><span class="mi">\ue037</span><b>Play</b></button></div><div class="recordinfo muted">Record up to 180 seconds</div><input type="file" id="file" hidden><div class="actions"><button data-action="back">Cancel</button><button class="primary" data-action="save-sound">Add Sound</button></div></div>`;$('#file').onchange=importFile}
else if(page==='edit'){screen.innerHTML=header('Edit Saved Sounds')+`<div class="edit"><p class="muted">Rename, reorder or delete sounds. Changes apply when you Save Changes.</p><div class="scroll">${draft.map((s,i)=>`<div class="editrow"><span class="number">${i+1}</span><b>${esc(s.name)}</b><button class="mi icon" data-editmove="${i},-1" ${i===0?'disabled':''} aria-label="Move up ${esc(s.name)}">\ue5d8</button><button class="mi icon" data-editmove="${i},1" ${i===draft.length-1?'disabled':''} aria-label="Move down ${esc(s.name)}">\ue5db</button><button data-rename="${i}">Rename</button><button class="danger" data-delete="${i}">Delete</button></div>`).join('')}</div><div class="actions"><button data-action="back">Cancel</button><button class="primary" data-action="save-edit">Save Changes</button></div></div>`}
else if(page==='settings'){renderSettings()}
else screen.innerHTML='<div class="exited"><h2>Outer Voice closed</h2><button class="primary" data-action="home">Open Outer Voice</button></div>';
overlay();bindHolds()}
function renderSettings(){
 const tabs=[['buttons','Buttons & Order'],['size','Size & Minimize']];
 $('#screen').innerHTML=header('Floating Panel').replace('</header>',`<label class="settings-enable"><input id="enabled" type="checkbox" ${draft.enabled?'checked':''}>Enable floating panel</label></header>`)
 +`<nav class="settings-tabs" role="tablist" aria-label="Floating Panel settings">${tabs.map(([id,label])=>`<button id="tab-${id}" role="tab" aria-selected="${settingsTab===id}" aria-controls="settings-content" data-settings-tab="${id}">${label}</button>`).join('')}</nav><div id="settings-content" class="settings-content" role="tabpanel" aria-labelledby="tab-${settingsTab}"></div><div class="settings-actions"><button data-action="back">Cancel</button><button class="primary" data-action="save-config">Save Settings</button></div>`;
 $('#enabled').onchange=e=>draft.enabled=e.target.checked;
 const content=$('#settings-content');content.classList.toggle('compact-layout',settingsTab!=='buttons');
 if(settingsTab==='buttons'){
   content.innerHTML=`<section class="panelitems"><h2>Panel buttons</h2><p class="tab-hint">Select sounds. Use arrows to change order.</p><div class="scroll" id="config-list"></div></section><section class="customize" id="customize"></section>`;
   configRows();customize();
 }else{
   content.innerHTML=`<section class="settings-card compact-controls"><div class="compact-heading"><h2>Panel layout</h2><button data-action="reset-size">Reset size</button></div><div class="compact-slider"><label for="size">Button size</label><input type="range" id="size" min="64" max="200" step="4" value="${draft.size}"><output id="size-value">${draft.size}px</output></div><div class="compact-slider"><label for="gap">Button spacing</label><input type="range" id="gap" min="0" max="40" value="${draft.gap}"><output id="gap-value">${draft.gap}px</output></div><div class="compact-slider"><label for="percent">Enlarge Live Speak button</label><input type="range" id="percent" min="0" max="100" step="1" value="${draft.percent}" aria-label="Live Speak button enlargement percent"><output id="percent-value">${draft.percent}%</output></div><h3>Minimize</h3><div class="compact-slider"><label for="bubbleSize">Minimized button size</label><input type="range" id="bubbleSize" min="64" max="200" step="4" value="${draft.bubbleSize}"><output id="bubbleSize-value">${draft.bubbleSize}px</output></div><div class="compact-auto"><label><input id="autoMinimize" type="checkbox" ${draft.autoMinimize?'checked':''}>Auto minimize</label><input id="autoSeconds" type="number" min="1" max="3600" step="1" value="${draft.autoSeconds}" ${draft.autoMinimize?'':'disabled'} aria-label="Auto minimize time in seconds"><span>seconds</span></div><p class="muted">Panel use restarts the inactivity timer.</p></section><section class="settings-card compact-previews"><h2>Floating panel preview</h2><div class="preview" id="preview"></div><h3>Minimized button preview</h3><div id="minimized-size-preview"></div></section>`;
   $('#autoMinimize').onchange=e=>{draft.autoMinimize=e.target.checked;$('#autoSeconds').disabled=!draft.autoMinimize};
   $('#autoSeconds').oninput=e=>draft.autoSeconds=e.target.value;
 }
 for(const key of ['size','gap','bubbleSize','percent'])if($('#'+key))$('#'+key).oninput=e=>{draft[key]=+e.target.value;$('#'+key+'-value').textContent=draft[key]+(key==='percent'?'%':'px');preview()};
 preview();
}
function neighbor(index,dir){for(let i=index+dir;i>=0&&i<draft.items.length;i+=dir)if(draft.items[i].selected)return i;return -1}
function configRows(){ $('#config-list').innerHTML=draft.items.map((s,i)=>`<div class="configrow ${s.id===editing?'selected':''}"><input type="checkbox" data-select="${s.id}" ${s.selected?'checked':''} ${s.id==='live'?'disabled':''} aria-label="Show ${esc(name(s.id))}"><span class="mi badge" data-custom="${s.id}" style="background:${buttonColor(s)};color:${buttonForeground(s)}">${s.id==='live'?'\ue029':glyphs[s.icon]}</span><span class="itemname" data-custom="${s.id}">${esc(name(s.id))}</span>${[-1,1].map(d=>`<button class="mi icon" data-configmove="${i},${d}" ${!s.selected||neighbor(i,d)<0?'disabled':''} aria-label="Move ${d<0?'up':'down'} ${esc(name(s.id))}">${d<0?'\ue5d8':'\ue5db'}</button>`).join('')}</div>`).join('')}
function customize(){
 const item=draft.items.find(i=>i.id===editing)||draft.items[0];editing=item.id;
 $('#customize').classList.add('emotion-customization');
 const swatches=[4,8,3,2,7,1,6,0,9,5]; // Display warm to cool without changing stored color indices.
 const selected=item.id==='live'?(item.customColor??-1):item.color;
 const d=Math.min(120,Math.round(draft.size*(1+draft.percent/100)));
 $('#customize').innerHTML=`<h2>Customize ${esc(name(item.id))}</h2><h3>Button color</h3>`
 +(item.id==='live'?`<div class="default-color"><button data-color="-1" class="${selected===-1?'chosen':''}" aria-label="App Blue"><i></i><span>App Blue</span><small>Default color</small></button></div>`:'')
 +`<div class="swatches">${swatches.map(i=>`<button data-color="${i}" class="${selected===i?'chosen':''}" aria-label="${colorNames[i]}"><i style="background:${colors[i]}"></i>${colorNames[i]}</button>`).join('')}</div>`
 +(item.id==='live'?`<h3>Button preview</h3><div class="live-color-preview"><span class="mi" style="width:${d}px;height:${d}px;font-size:${d*.45}px;background:${buttonColor(item)};color:${buttonForeground(item)}">\ue029</span></div>`
 :`<h3 style="height:30px">Button icon</h3><div class="choices">${glyphs.map((g,i)=>`<button data-glyph="${i}" aria-label="${esc(iconNames[i])}" aria-pressed="${item.icon===i}" title="${esc(iconNames[i])}" class="${item.icon===i?'chosen':''}"><span class="mi" style="color:${item.icon===i?colors[item.color]:'white'}">${g}</span>${iconNames[i]}</button>`).join('')}</div>`);
}
function circles(c,f=1,interactive=false){let largest=Math.round(c.size*(1+c.percent/100)*f);return c.items.filter(i=>i.selected).map((s,i)=>{let d=Math.round(c.size*(s.id==='live'?1+c.percent/100:1)*f),m=(largest-d)/2;return `<div class="floatitem" style="width:${d}px;margin-left:${i?c.gap*f:0}px"><button class="mi floatcircle" style="width:${d}px;height:${d}px;margin-top:${m}px;font-size:${d*.45}px;background:${buttonColor(s)};color:${buttonForeground(s)}" ${interactive?(s.id==='live'?'data-hold':`data-play="${s.id}"`):'tabindex="-1"'} aria-label="${esc(name(s.id))}">${s.id==='live'?'\ue029':glyphs[s.icon]}</button><span class="floatlabel" style="margin-top:${m}px;font-size:${interactive?Math.max(12,Math.min(17,c.size/6)):12}px">${esc(name(s.id))}</span></div>`}).join('')}
function preview(){
 const panel=$('#preview');
 if(panel){
   const selected=draft.items.filter(i=>i.selected);
   const largest=draft.size*(1+draft.percent/100);
   const width=selected.reduce((sum,i)=>sum+(i.id==='live'?largest:draft.size),0)+Math.max(0,selected.length-1)*draft.gap;
   const scale=Math.min(.62,76/largest,Math.max(1,panel.clientWidth-28)/Math.max(1,width));
   panel.innerHTML='<div class="previewpanel"><div class="previewtools"><span class="mi">\ue25d</span><span>− Minimize</span><span class="mi">\ue5cd</span></div><div class="previewstrip">'+circles(draft,scale)+'</div></div>';
 }
 const minimized=$('#minimized-size-preview');
 if(minimized)minimized.innerHTML=`<span class="mi bubble-preview" style="background:${buttonColor(liveItem(draft))};color:${buttonForeground(liveItem(draft))};width:${Math.round(Math.min(80,draft.bubbleSize*.62))}px;height:${Math.round(Math.min(80,draft.bubbleSize*.62))}px;font-size:${Math.min(80,draft.bubbleSize*.62)*.45}px">\ue029</span><div><strong>${draft.bubbleSize}px</strong><span class="muted">${liveOnly(draft)?'Hold to speak':'Tap to reopen'} · Drag to move</span></div>`;
}
function overlay(){
 overlayRevision++;transitioning=false;clearTimeout(autoTimer);panelPointerActive=false;
 let o=$('#overlay');o.innerHTML='';
 if(!state.config.enabled||(!other&&page!=='home'))return;
 if(state.minimized){
   let d=state.config.bubbleSize;
   state.bubble.x=Math.max(0,Math.min(state.bubble.x,1024-d));state.bubble.y=Math.max(0,Math.min(state.bubble.y,600-d));
   const speak=liveOnly(state.config);
   o.innerHTML=`<button class="mi bubble" aria-label="${speak?'Hold to Speak directly':'Reopen floating panel'}" title="${speak?'Hold to speak; release to play. Drag to move.':'Tap to reopen. Drag to move.'}" style="background:${buttonColor(liveItem(state.config))};color:${buttonForeground(liveItem(state.config))};left:${state.bubble.x}px;top:${state.bubble.y}px;width:${d}px;height:${d}px;font-size:${d*.45}px">\ue029</button>`;
   drag($('.bubble'),$('.bubble'),state.bubble,speak?null:()=>transitionPanel(false),speak);
 }else{
   o.innerHTML=`<div class="floatpanel" style="left:${state.position.x}px;top:${state.position.y}px"><div class="paneltoolbar"><span class="mi grip" role="button" tabindex="0" aria-label="Drag floating panel" title="Drag to move">\ue25d</span><button class="minimize" data-action="minimize" aria-label="Minimize panel"><span class="mi">\ue15b</span><span class="minimize-label">Minimize</span></button><button class="mi close-panel" data-action="close-panel" aria-label="Close panel">\ue5cd</button></div><div class="floatstrip">${circles(state.config,1,true)}</div><div class="floatstatus">Hold Live Speak; release to play.</div></div>`;
   let panel=$('.floatpanel');
   const selected=state.config.items.filter(i=>i.selected);
   const soundWidth=selected.reduce((sum,i)=>sum+state.config.size*(i.id==='live'?1+state.config.percent/100:1),0)+Math.max(0,selected.length-1)*state.config.gap;
   panel.style.width=Math.min(1008,Math.max(252,Math.ceil(soundWidth)+30))+'px';
   panel.classList.toggle('narrow-panel',panel.offsetWidth<360);
   panel.classList.toggle('single-sound',selected.length===1);
   state.position.x=Math.max(0,Math.min(state.position.x,1024-panel.offsetWidth));state.position.y=Math.max(0,Math.min(state.position.y,600-panel.offsetHeight));
   panel.style.left=state.position.x+'px';panel.style.top=state.position.y+'px';
   panel.querySelector('.floatstrip').scrollLeft=state.stripScroll;
   drag(panel.querySelector('.grip'),panel,state.position);
   armAutoMinimize();
 }
}
async function transitionPanel(minimize){
 if(transitioning)return;clearTimeout(autoTimer);stopAudio();holding=false;clearTimeout(timer);transitioning=true;
 const revision=overlayRevision,el=minimize?$('.floatpanel'):$('.bubble');
 if(!el){transitioning=false;return}
 const reduced=matchMedia('(prefers-reduced-motion: reduce)').matches, duration=reduced?0:320;
 const d=state.config.bubbleSize;
 if(minimize){
   const target=state.bubble;
   const live=el.querySelector('[data-hold]').getBoundingClientRect(),device=$('#device').getBoundingClientRect();
   const cx=(live.left+live.width/2-device.left)/(device.width/1024),cy=(live.top+live.height/2-device.top)/(device.height/600);
   target.x=Math.max(0,Math.min(cx-d/2,1024-d));target.y=Math.max(0,Math.min(cy-d/2,600-d));
   state.stripScroll=el.querySelector('.floatstrip').scrollLeft;
   const dx=target.x+d/2-cx,dy=target.y+d/2-cy,scale=d/el.querySelector('[data-hold]').offsetWidth;
   el.style.pointerEvents='none';el.style.transformOrigin=`${cx-state.position.x}px ${cy-state.position.y}px`;
   try{await el.animate([{transform:'translate(0,0) scale(1)',opacity:1},{transform:`translate(${dx}px,${dy}px) scale(${scale})`,opacity:0}],{duration,easing:'cubic-bezier(.22,1,.36,1)',fill:'forwards'}).finished}catch{}
   if(revision!==overlayRevision)return;
   state.minimized=true;persist();overlay();
   $('.bubble')?.animate([{transform:'scale(.8)',opacity:0},{transform:'scale(1)',opacity:1}],{duration:reduced?0:140,easing:'ease-out'});
 }else{
   const origin={...state.bubble};state.minimized=false;overlay();bindHolds();
   const panel=$('.floatpanel');if(!panel)return;
   const live=panel.querySelector('[data-hold]').getBoundingClientRect(),device=$('#device').getBoundingClientRect();
   const px=(live.left+live.width/2-device.left)/(device.width/1024)-state.position.x,py=(live.top+live.height/2-device.top)/(device.height/600)-state.position.y;
   const cx=origin.x+d/2,cy=origin.y+d/2;
   state.position.x=Math.max(0,Math.min(cx-px,1024-panel.offsetWidth));state.position.y=Math.max(0,Math.min(cy-py,600-panel.offsetHeight));
   panel.style.left=state.position.x+'px';panel.style.top=state.position.y+'px';persist();
   panel.style.transformOrigin=`${px}px ${py}px`;panel.style.pointerEvents='none';
   const dx=cx-state.position.x-px,dy=cy-state.position.y-py,scale=d/panel.querySelector('[data-hold]').offsetWidth;
   try{await panel.animate([{transform:`translate(${dx}px,${dy}px) scale(${scale})`,opacity:0},{transform:'translate(0,0) scale(1)',opacity:1}],{duration,easing:'cubic-bezier(.22,1,.36,1)'}).finished}catch{}
   panel.style.pointerEvents='';transitioning=false;armAutoMinimize();
 }
}
function armAutoMinimize(){
 clearTimeout(autoTimer);
 if(!state.config.enabled||!state.config.autoMinimize||state.minimized||!$('.floatpanel'))return;
 const tick=()=>{
   if(!$('.floatpanel')||state.minimized||!state.config.autoMinimize)return;
   if(holding||audio||panelPointerActive||transitioning){autoTimer=setTimeout(tick,250);return}
   transitionPanel(true);
 };
 autoTimer=setTimeout(tick,state.config.autoSeconds*1000);
}
document.addEventListener('pointerdown',e=>{
 if(e.target.closest('.floatpanel')){panelPointerActive=true;clearTimeout(autoTimer)}
},true);
for(const event of ['pointerup','pointercancel'])document.addEventListener(event,()=>{
 if(panelPointerActive){panelPointerActive=false;armAutoMinimize()}
});
document.addEventListener('keydown',e=>{if(e.target.closest('.floatpanel'))armAutoMinimize()});
document.addEventListener('keyup',e=>{if(e.target.closest('.floatpanel'))armAutoMinimize()});
function drag(handle,element,pos,tap,speak=false){
 let start,moved=false;
 handle.onpointerdown=e=>{if(e.button!==0)return;e.preventDefault();handle.setPointerCapture(e.pointerId);start={x:e.clientX,y:e.clientY,px:pos.x,py:pos.y};moved=false;if(speak)startHold(handle)};
 handle.onpointermove=e=>{if(!start)return;let scale=$('#device').getBoundingClientRect().width/1024,dx=(e.clientX-start.x)/scale,dy=(e.clientY-start.y)/scale;if(!moved&&Math.hypot(dx,dy)>8){moved=true;if(speak)endHold(handle,true)}if(moved){pos.x=Math.max(0,Math.min(1024-element.offsetWidth,start.px+dx));pos.y=Math.max(0,Math.min(600-element.offsetHeight,start.py+dy));element.style.left=pos.x+'px';element.style.top=pos.y+'px'}};
 handle.onpointerup=()=>{if(!start)return;start=null;if(speak)endHold(handle,moved);persist()};
 handle.onclick=()=>{if(!moved&&tap)tap()};
 handle.onpointercancel=()=>{start=null;moved=true;if(speak)endHold(handle,true);persist()};
 if(speak){handle.onkeydown=e=>{if((e.key===' '||e.key==='Enter')&&!e.repeat){e.preventDefault();startHold(handle)}};handle.onkeyup=e=>{if(e.key===' '||e.key==='Enter'){e.preventDefault();endHold(handle)}}}
}
function stopAudio(){if(audio){audio.pause();audio=null}document.querySelectorAll('[data-play]').forEach(b=>b.style.filter='');if($('#test-play'))$('#test-play').innerHTML='<span class="mi">\ue037</span><b>Play</b>'}
function play(id){stopAudio();let url=blobs.get(id);if(url){audio=new Audio(url);audio.play().catch(()=>toast('This browser cannot decode that sound.'));audio.onended=stopAudio}else{toast('Demo playback: '+name(id));document.querySelectorAll(`[data-play="${id}"]`).forEach(b=>b.style.filter='brightness(1.6)');timer=setTimeout(stopAudio,1200)}}
function liveStatus(text){if($('#live-status'))$('#live-status').textContent=text;if($('.floatstatus'))$('.floatstatus').textContent=text}
function startHold(el){el.releaseMotion?.cancel();stopAudio();holding=true;el.classList.add('active');$('.live')?.classList.add('recording');liveStatus('Recording… release to save and play');toast('Simulated microphone recording')}
function endHold(el,cancel=false){if(!holding)return;holding=false;const scale=getComputedStyle(el).scale;el.classList.remove('active');if(!matchMedia('(prefers-reduced-motion: reduce)').matches)el.releaseMotion=el.animate([{scale},{scale:'1'}],{duration:220,easing:'cubic-bezier(.22,1,.36,1)'});$('.live')?.classList.remove('recording');liveStatus(cancel?'Recording cancelled':'Saved to WAV · playing… (simulation)');if(!cancel)timer=setTimeout(()=>liveStatus('Hold Live Speak; release to play.'),1600)}
function bindHolds(){document.querySelectorAll('[data-hold]').forEach(el=>{el.onpointerdown=e=>{if(e.button!==0)return;e.preventDefault();el.setPointerCapture(e.pointerId);startHold(el)};el.onpointerup=()=>endHold(el);el.onpointercancel=()=>endHold(el,true);el.onkeydown=e=>{if((e.key===' '||e.key==='Enter')&&!e.repeat){e.preventDefault();startHold(el)}};el.onkeyup=e=>{if(e.key===' '||e.key==='Enter'){e.preventDefault();endHold(el)}}})}
function importFile(e){const file=e.target.files[0];if(!file)return;stopAudio();if(pending?.url)URL.revokeObjectURL(pending.url);pending={name:file.name,url:URL.createObjectURL(file)};$('#import-name').textContent=file.name;$('#test-play').disabled=false;if(!$('#sound-name').value)$('#sound-name').value=file.name.replace(/\.[^.]+$/,'');toast('Imported. Press Play to test browser compatibility.')}
document.addEventListener('click',e=>{const b=e.target.closest('button,[data-custom]');if(!b)return;if(b.dataset.nav){navigate(b.dataset.nav);return}if(b.dataset.settingsTab){settingsTab=b.dataset.settingsTab;renderSettings();return}if(b.dataset.play){play(b.dataset.play);return}if(b.dataset.custom){editing=b.dataset.custom;configRows();customize();return}if(b.dataset.editmove){let[i,d]=b.dataset.editmove.split(',').map(Number);[draft[i],draft[i+d]]=[draft[i+d],draft[i]];render();return}if(b.dataset.configmove){let[i,d]=b.dataset.configmove.split(',').map(Number),n=neighbor(i,d);if(n>=0){[draft.items[i],draft.items[n]]=[draft.items[n],draft.items[i]];configRows();preview()}return}if(b.dataset.rename!==undefined){let s=draft[+b.dataset.rename],n=prompt('Rename sound',s.name);if(n?.trim()){s.name=n.trim().slice(0,60);render()}return}if(b.dataset.delete!==undefined){if(confirm('Delete saved sound? It is removed only after Save Changes.')){draft.splice(+b.dataset.delete,1);render()}return}if(b.dataset.color!==undefined||b.dataset.glyph!==undefined){let s=draft.items.find(i=>i.id===editing);if(b.dataset.color!==undefined){if(s.id==='live')s.customColor=+b.dataset.color<0?null:+b.dataset.color;else s.color=+b.dataset.color}else if(s.id!=='live')s.icon=+b.dataset.glyph;configRows();customize();preview();return}switch(b.dataset.action){case'home':case'back':navigate('home');break;case'add':case'edit':case'settings':navigate(b.dataset.action);break;case'info':alert('Outer Voice v1.4.2\nUI browser simulation\nDeveloper: Zeca\nAudio routing and Android permissions require the native app.');break;case'audio':toast('Outer speaker · BUS12 routing is simulated in this browser.');break;case'exit':state.config.enabled=false;persist();page='exit';render();break;case'import':$('#file').click();break;case'record':pending={name:'Recorded demo.wav',url:null};$('#import-name').textContent='Recorded demo.wav (simulation)';$('#test-play').disabled=false;toast('Demo recording ready');break;case'preview':if(audio){stopAudio();break}if(pending?.url){audio=new Audio(pending.url);audio.play().then(()=>$('#test-play').innerHTML='<span class="mi">\ue047</span><b>Stop</b>').catch(()=>{stopAudio();toast('This browser cannot decode that sound.')});audio.onended=stopAudio}else toast('Demo recording playback (simulation)');break;case'save-sound':{let n=$('#sound-name').value.trim();if(!n||!pending){toast('Enter a name and import or record a sound.');break}let id='s'+Date.now();state.sounds.push({id,name:n});state.config.items.push({id,selected:false,color:0,icon:2});if(pending.url)blobs.set(id,pending.url);persist();navigate('home');break}case'save-edit':state.sounds=draft;state.config.items=state.config.items.filter(i=>i.id==='live'||state.sounds.some(s=>s.id===i.id));persist();navigate('home');break;case'reset-size':draft.size=88;$('#size').value=88;$('#size-value').textContent='88px';preview();break;case'save-config':{let seconds=Number(draft.autoSeconds);if(draft.autoMinimize&&(!Number.isInteger(seconds)||seconds<1||seconds>3600)){toast('Enter an auto-minimize time from 1 to 3600 seconds.');settingsTab='size';renderSettings();$('#autoSeconds').focus();break}draft.autoSeconds=Number.isInteger(seconds)&&seconds>=1&&seconds<=3600?seconds:30;let p=$('#percent');if(p&&(p.value===''||+p.value<0||+p.value>100)){toast('Enter a Live Speak button enlargement from 0 to 100%.');break}state.config=draft;state.minimized=false;state.stripScroll=0;persist();navigate('home');break}case'minimize':transitionPanel(true);break;case'close-panel':if(confirm('Close the floating panel? You can enable it again in Floating Panel settings.')){state.config.enabled=false;persist();overlay()}break}});
document.addEventListener('change',e=>{if(e.target.dataset.select){let s=draft.items.find(i=>i.id===e.target.dataset.select);s.selected=e.target.checked;editing=s.id;configRows();customize();preview()}});
$('#other').onclick=()=>{stopAudio();other=!other;page='home';render();if(!state.config.enabled)toast('Enable the floating panel in Floating Panel to see it here.')};$('#zoom').onclick=()=>{actual=!actual;$('#zoom').textContent=actual?'Fit screen':'Actual size';resize()};$('#reset').onclick=()=>{if(confirm('Reset demo sounds and settings?')){state=clone(initial);persist();navigate('home')}};
function resize(){const scale=actual?1:Math.min(1,(innerWidth-32)/1024,Math.max(240,innerHeight-140)/600);$('#device').style.transform=`scale(${scale})`;$('#viewport').style.width=1024*scale+'px';$('#viewport').style.height=600*scale+'px';$('#viewport').style.overflow=actual?'auto':'clip'}
window.onresize=resize;window.addEventListener('keydown',e=>{if(e.key==='Escape')navigate('home')});window.addEventListener('blur',()=>{if(holding){holding=false;$('.live')?.classList.remove('recording');document.querySelectorAll('.active').forEach(e=>e.classList.remove('active'));liveStatus('Recording cancelled')}});render();resize();
