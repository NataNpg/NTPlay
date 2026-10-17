/* Detecção de plataforma: TV (controle remoto), celular, iOS, modo leve + navegação por setas */
(function(){
  const H=document.documentElement,ua=navigator.userAgent,C=window.Capacitor,nat=!!(C&&C.isNativePlatform&&C.isNativePlatform());
  const q=new URLSearchParams(location.search);
  const ios=/iPhone|iPad|iPod/i.test(ua)||(navigator.platform=='MacIntel'&&navigator.maxTouchPoints>1);
  const tvUA=/Android TV|AFTB|AFTM|AFTT|BRAVIA|SmartTV|SMART-TV|Tizen|Web0S|GoogleTV|AndroidTV|MiBOX|Aquos|HbbTV/i.test(ua);
  const tv=q.has('tv')||tvUA||(nat&&/Android/i.test(ua)&&navigator.maxTouchPoints===0);
  const mob=!tv&&(ios||/Android|Mobile/i.test(ua)||(nat&&innerWidth<900));
  if(tv)H.classList.add('tv');if(mob)H.classList.add('mobile');if(ios)H.classList.add('ios');
  let lite;try{lite=localStorage.ntLite}catch(e){}
  if(lite==null)lite=(tv||mob||nat)?'1':'0';
  if(lite=='1'){H.classList.add('lite');try{localStorage.ntLite='1'}catch(e){}}
  if(tv){const m=document.querySelector('meta[name=viewport]');if(m)m.content='width=1280, initial-scale='+(Math.min(innerWidth,screen.width||innerWidth)/1280||1)+', user-scalable=no'}
  /* navegação espacial */
  const SEL='button:not([disabled]),a[href],select,input:not([type=hidden]),[tabindex="0"]';
  const vis=el=>{if(el.hidden||el.closest('[hidden]'))return false;const r=el.getBoundingClientRect();return r.width>4&&r.height>4&&r.bottom>0&&r.top<innerHeight+400};
  function scope(){const m=document.getElementById('modal');if(m&&!m.hidden)return m;const s=document.getElementById('sdr');if(s&&!s.hidden)return s;return document}
  function move(dir){
    const root=scope(),cur=document.activeElement&&document.activeElement!==document.body&&root.contains(document.activeElement)?document.activeElement:null;
    const list=[...root.querySelectorAll(SEL)].filter(vis);if(!list.length)return false;
    if(!cur){const f=list.find(e=>e.closest('#bmain,#pg-detail,#rside'))||list[0];f.focus();return true}
    const a=cur.getBoundingClientRect(),ax=a.left+a.width/2,ay=a.top+a.height/2;let best=null,bs=1e12;
    for(const el of list){if(el===cur)continue;const r=el.getBoundingClientRect(),x=r.left+r.width/2,y=r.top+r.height/2,dx=x-ax,dy=y-ay;
      let main,cross;if(dir=='ArrowRight'){if(dx<=2&&r.left<a.right-2)continue;main=dx;cross=Math.abs(dy)}
      else if(dir=='ArrowLeft'){if(dx>=-2&&r.right>a.left+2)continue;main=-dx;cross=Math.abs(dy)}
      else if(dir=='ArrowDown'){if(dy<=2&&r.top<a.bottom-2)continue;main=dy;cross=Math.abs(dx)}
      else{if(dy>=-2&&r.bottom>a.top+2)continue;main=-dy;cross=Math.abs(dx)}
      if(main<0)continue;const sc=main+cross*3;if(sc<bs){bs=sc;best=el}}
    if(best){best.focus({preventScroll:true});best.scrollIntoView({block:'nearest',inline:'nearest'});return true}return false}
  addEventListener('keydown',e=>{
    if(!/^Arrow(Up|Down|Left|Right)$/.test(e.key)||e.ctrlKey||e.altKey||e.metaKey)return;
    const t=e.target,tag=t&&t.tagName;
    if(tag=='INPUT'&&/text|search|url|^$/.test(t.type||'')&&(e.key=='ArrowLeft'||e.key=='ArrowRight'))return;
    if(tag=='SELECT'&&(e.key=='ArrowUp'||e.key=='ArrowDown'))return;
    const pl=document.getElementById('pg-player');
    if(pl&&!pl.hidden&&!document.fullscreenElement&&t&&t.id=='v'&&(e.key=='ArrowLeft'||e.key=='ArrowRight')){const v=t;v.currentTime+=e.key=='ArrowRight'?15:-15;e.preventDefault();return}
    if(move(e.key))e.preventDefault()},true);
  /* botão voltar do Android / controle */
  if(nat&&C.Plugins&&C.Plugins.App){const A=C.Plugins.App;
    A.addListener('backButton',()=>{
      const m=document.getElementById('modal'),s=document.getElementById('sdr'),br=document.getElementById('pg-browse');
      const atRoot=br&&!br.hidden&&m.hidden&&s.hidden&&!document.querySelector('#q').value;
      if(atRoot&&!window.ntCanBack){A.exitApp();return}
      document.dispatchEvent(new KeyboardEvent('keydown',{key:'Escape',bubbles:true}))})}
  window.ntPlatform={tv,mob,ios,nat};
})();
