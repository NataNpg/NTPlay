const $=s=>document.querySelector(s),LS=localStorage;const LITE=()=>document.documentElement.classList.contains('lite'),LIM0=()=>{const n=LITE()?24:60;return{l:n,m:n,s:n}};
const esc=s=>String(s==null?'':s).replace(/[&<>"]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c]));
const norm=s=>String(s||'').toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g,'').replace(/\(.*?\)|\[.*?\]/g,' ').replace(/\b(dublado|legendado|dual|4k|fhd|uhd|hd)\b/g,' ').replace(/[^a-z0-9]+/g,' ').trim();
const IC={search:'<circle cx="11" cy="11" r="7"/><path d="M21 21l-4.3-4.3"/>',clock:'<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/>',star:'<path d="M12 3l2.7 5.6 6.1.9-4.4 4.3 1 6.1L12 17l-5.4 2.9 1-6.1L3.2 9.5l6.1-.9z"/>',x:'<path d="M6 6l12 12M18 6L6 18"/>',back:'<path d="M15 5l-7 7 7 7"/>',tv:'<rect x="3" y="5" width="18" height="12" rx="2"/><path d="M8 21h8M12 17v4"/>',play:'<path d="M7 4l13 8-13 8z"/>'};
const ic=(n,f)=>`<svg class="i${f?' f':''}" viewBox="0 0 24 24">${IC[n]}</svg>`;
const TABS=['RECOMENDAÇÕES','Vivo','FUTEBOL','FILMES','SÉRIES','INFANTIL','ANIMES','FAVORITOS'];
const S={bq:'',bg:'',bsKey:'',view:'browse',stack:[],det:null,hero:{items:[],i:0},heroT:null,pq:'',pg2:'',pkey:'',all:[],live:[],mov:[],ser:[],every:[],map:new Map(),byUrl:new Map(),idx:{m:new Map(),s:new Map()},tab:LS.ntTab&&TABS.includes(LS.ntTab)?LS.ntTab:'RECOMENDAÇÕES',q:'',type:'all',group:'',fav1:false,sort:'',lim:LIM0(),
  fav:new Set(JSON.parse(LS.ntFav||'[]')),cur:null,hist:[],lastList:[],season:1,pl:'',autoNext:LS.ntAuto!='0'};
let P={};try{P=JSON.parse(LS.ntProg||'{}')}catch(e){}
let HL={movies:[],series:[],tips:[]};

/* ---------- M3U ---------- */
/* nome da série sem marcas de temporada: "Colony S03 Colony" -> "Colony" */
function serT(t){let x=String(t).replace(/\b[ST]\d{1,2}\b(?!\s?[EeXx]\d)/g,' ').replace(/\b(?:temporada|season|temp\.?)\s*\d{1,2}\b|\b\d{1,2}\s*[ªa°º]?\s*temporada\b/ig,' ').replace(/\s+/g,' ').replace(/[\s\-–|:._]+$/,'').replace(/^[\s\-–|:._]+/,'').trim();
  const w=x.split(' ');if(w.length>1&&w.length%2==0){const h=w.length/2;if(w.slice(0,h).join(' ').toLowerCase()==w.slice(h).join(' ').toLowerCase())x=w.slice(0,h).join(' ')}return x||String(t).trim()}
const epName=c=>{const m=c.name.match(/\b[sStT]\s?\d{1,2}\s?[eExX]\s?\d{1,3}\b|\b\d{1,2}x\d{1,3}\b/);let r=m?c.name.slice(m.index+m[0].length):'';r=r.replace(/^[\s\-–|:._]+/,'').trim();return r&&!/^\d+$/.test(r)?r:'Episódio '+c.e};
function epInfo(n){const m=n.match(/^(.*?)[\s._\-–|:]*[\[(]?\b[sStT]\s?(\d{1,2})\s?[eExX]\s?(\d{1,3})\b/)||n.match(/^(.*?)[\s._\-–|:]*\b(\d{1,2})x(\d{1,3})\b/);if(m)return{t:m[1].trim()||n,s:+m[2],e:+m[3]};
  // doramas/novelas: "Nome EP01", "Nome - Episódio 12", "Nome Capítulo 3", "Nome E05" (temporada pelo "Temporada 2"/"T2", senão 1)
  const k=n.match(/^(.*?)[\s._\-–|:\[(]*\b(?:ep\.?|eps\.?|epis[oó]dio|cap[ií]tulo|cap\.|chapter|E)\s?(\d{1,4})\b/i);
  if(k&&k[1].trim()){const sm=n.match(/\b(?:temporada|season)\s*(\d{1,2})\b|\b[ST](\d{1,2})\b/i);return{t:k[1].replace(/[\s\-–|:]*(?:\b(?:temporada|season)\s*\d{1,2}|\b[ST]\d{1,2})\s*$/i,'').trim()||k[1].trim(),s:sm?+(sm[1]||sm[2]):1,e:+k[2]}}
  return null}
function guess(u,g,n){const x=u.toLowerCase().split('?')[0];
  if(x.includes('/live/'))return'live';if(epInfo(n||''))return'series';if(x.includes('/series/'))return'series';if(x.includes('/movie/'))return'movies';
  if(/s[eé]rie|novela|anime|dorama|temporada/i.test(g))return'series';
  if(/filme|movie|vod|cinema|lan[çc]amento/i.test(g)||/\.(mp4|mkv|avi)$/.test(x))return'movies';
  if(/replay|catch.?up/i.test(g))return'replay';return'live'}
function parseM3U(text){const out=[];let m=null;
  for(const raw of text.replace(/^\uFEFF/,'').split(/\r?\n/)){const l=raw.trim();if(!l)continue;
    if(l.startsWith('#EXTINF')){m={};let q=false,c=-1;for(let i=0;i<l.length;i++){if(l[i]=='"')q=!q;else if(l[i]==','&&!q){c=i;break}}
      const head=c<0?l:l.slice(0,c);m.name=c<0?'':l.slice(c+1).trim();for(const a of head.matchAll(/([\w-]+)="([^"]*)"/g))m[a[1].toLowerCase()]=a[2];continue}
    if(l.startsWith('#EXTGRP:')){m=m||{};m['group-title']=m['group-title']||l.slice(8).trim();continue}
    if(l[0]=='#'||!/^[a-z]+:\/\//i.test(l))continue;m=m||{};
    const name=m.name||m['tvg-name']||l.split('/').pop().split('?')[0]||'Canal';
    out.push({name,url:l,group:m['group-title']||'Sem grupo',logo:m['tvg-logo']||'',cat:guess(l,m['group-title']||'',name)});m=null}
  return out}
const cleanG=g=>(g.replace(/^\s*(filmes?|movies?|s[eé]ries?|series|vod|canais?)\s*[|:\->»]+\s*/i,'').trim()||g.trim()||'Sem grupo');
/* animes: pelo grupo, pelo nome (títulos japoneses conhecidos) ou pela internet (TMDB/TVMaze: animação japonesa) */
const ANI_G=/anim[eê]s?\b|otaku|crunchyroll|funimation|animax|toonami|desenhos? japones|japanese anim/i;
const ANI_T=new RegExp('\\b('+['naruto','boruto','one piece','dragon ball','bleach','shingeki','attack on titan','ataque dos tit[aã]s','kimetsu','demon slayer','jujutsu','boku no hero','my hero academia','death note','hunter x hunter','fullmetal','pok[eé]mon','sailor moon','cavaleiros do z[oó]d[ií]aco','saint seiya','yu.?gi.?oh','digimon','one punch','tokyo ghoul','sword art','fairy tail','black clover','haikyu','chainsaw man','spy x family','evangelion','cowboy bebop','inuyasha','yu yu hakusho','dr\\.? stone','vinland','mob psycho','blue lock','solo leveling','frieren','oshi no ko','hellsing','berserk','code geass','steins','re:? ?zero','overlord','tensei shitara','konosuba','tokyo revengers','fire force','dororo','gintama','toradora','clannad','kimi no na wa','your name','chihiro','spirited away','totoro','mononoke','castelo animado','ponyo','ghibli','akira','ghost in the shell','doraemon','detective conan','detetive conan','captain tsubasa','super campe[oõ]es','shaman king','beyblade','hajime no ippo','kuroko','slam dunk','initial d','made in abyss','promised neverland','yakusoku','violet evergarden','boku dake','parasyte','kiseijuu','psycho.?pass','trigun','samurai x','rurouni kenshin','zatch bell','medabots','sakura card','card captor','hamtaro','lupin iii','dan da dan','dandadan','kaiju n','sakamoto days','wind breaker','undead unluck','hell.?s paradise','jigokuraku','mashle','bocchi','horimiya','kaguya','nanatsu','seven deadly sins','the rising of the shield','tate no yuusha','mushoku','danmachi','no game no life','noragami','soul eater','d\\.?gray','blue exorcist','ao no exorcist','magi\\b','assassination classroom','ansatsu','food wars','shokugeki','great teacher onizuka','gto\\b','zetsuen','darling in the franxx','gurren','kill la kill','cyberpunk edgerunners','dragon quest','urusei','ranma','city hunter','captain harlock','macross','gundam','ninja scroll','perfect blue','paprika','a voz do sil[eê]ncio','koe no katachi','suzume','tenki no ko','weathering with you','boy and the heron','menino e a gar[cç]a'].join('|')+')','i');
function theme(e,g){const t=e.kind=='l'?g+' '+e.title:g,inf=INFO[e.key];
  if(ANI_G.test(g)||ANI_T.test(e.title)||(e.kind=='l'&&ANI_G.test(e.title))||(inf&&inf.an))return'ANIMES';
  if(/(kids|infantil|desenho|cartoon|baby|junior|gloob|nick|disney jr|gloobinho)/i.test(t))return'INFANTIL';return''}
function build(){
  const live=[],mv=[],sm=new Map();S.byUrl=new Map();const hideA=!showAdult();
  for(const c of S.all){if(hideA&&ADR.test(c.name+' '+c.group))continue;
    if(c.cat=='movies'){const e={kind:'m',key:'m:'+c.url,title:c.name,group:cleanG(c.group),logo:c.logo,ch:[c]};mv.push(e);S.byUrl.set(c.url,{e,c})}
    else if(c.cat=='series'){const i=epInfo(c.name)||(()=>{const z=c.name.match(/^(.+?)[\s\-–|:#]+(\d{1,4})\s*$/);return z?{t:z[1],s:1,e:+z[2]}:null})(),t=serT(i?i.t:c.name),leg=/legendad|\[l\]|\(l\)|\bleg\b/i.test(c.name+' '+c.group)?'|L':'',k='s:'+(norm(t)||t.toLowerCase())+leg;let o=sm.get(k);
      if(!o){o={kind:'s',key:k,title:t+(leg?' (Legendado)':''),group:cleanG(c.group),logo:c.logo,ch:[],se:new Set()};sm.set(k,o)}
      c.s=i?i.s:1;c.e=i?i.e:o.ch.length+1;const se=c.s+'x'+c.e;if(i&&o.se.has(se))continue;o.se.add(se);if(!o.logo)o.logo=c.logo;o.ch.push(c);S.byUrl.set(c.url,{e:o,c})}
    else{const e={kind:'l',key:'l:'+c.url,title:c.name,group:cleanG(c.group),logo:c.logo,ch:[c]};live.push(e);S.byUrl.set(c.url,{e,c})}}
  const adl=a=>a.sort((x,y)=>isAdult(x)-isAdult(y));adl(live);adl(mv);const ser=adl([...sm.values()]);for(const o of ser){o.ch.sort((a,b)=>a.s-b.s||a.e-b.e);o.ns=new Set(o.ch.map(c=>c.s)).size;delete o.se}
  // capa genérica do servidor (a mesma imagem em vários títulos diferentes) é descartada; versões dublada/legendada do mesmo título não contam
  const cnt=new Map(),vod=[...mv,...ser];vod.forEach(o=>{if(!o.logo)return;let st=cnt.get(o.logo);if(!st)cnt.set(o.logo,st=new Set());st.add(norm(o.kind=='s'?serT(o.title.replace(/\s*\(Legendado\)$/,'')):o.title))});vod.forEach(o=>{if(o.logo&&cnt.get(o.logo).size>2)o.logo=''});
  S.live=live;S.mov=mv;S.ser=ser;S.every=live.concat(mv,ser);S.map=new Map(S.every.map(e=>[e.key,e]));
  S.every.forEach(e=>{e.th=theme(e,e.group)});
  S.idx={m:new Map(),s:new Map()};mv.forEach(e=>{const k=norm(e.title);if(k&&!S.idx.m.has(k))S.idx.m.set(k,e)});ser.forEach(e=>{const k=norm(e.title);if(k&&!S.idx.s.has(k))S.idx.s.set(k,e)});
  applyHL()}

/* ---------- progresso / favoritos ---------- */
let lastSave=0;
function saveProg(force){const x=S.cur;if(!x||x.c.cat=='live')return;const t=curT(),d=durT();if(!isFinite(d)||!d||t<1)return;
  if(!force&&Date.now()-lastSave<5000)return;lastSave=Date.now();
  P[x.c.url]={t:Math.floor(t),d:Math.floor(d),ts:Date.now(),done:t>d*.95};
  const ks=Object.keys(P);if(ks.length>800){ks.sort((a,b)=>P[a].ts-P[b].ts).slice(0,ks.length-700).forEach(k=>delete P[k])}
  try{LS.ntProg=JSON.stringify(P)}catch(e){}}
const pct=c=>{const p=P[c.url];return p&&p.d&&!p.done?Math.min(100,p.t/p.d*100):0};
function lastWatched(e){let b=null;for(const c of e.ch){const p=P[c.url];if(p&&(!b||p.ts>b.p.ts))b={c,p}}return b}
function startEp(e){if(e.kind!='s')return e.ch[0];const b=lastWatched(e);if(!b)return e.ch[0];if(b.p.done){return e.ch[Math.min(e.ch.indexOf(b.c)+1,e.ch.length-1)]}return b.c}
const eprog=e=>{if(e.kind=='l')return 0;if(e.kind=='m')return pct(e.ch[0]);const b=lastWatched(e);return b?pct(b.c):0};
function removeProg(k){const e=S.map.get(k);if(!e)return;e.ch.forEach(c=>{delete P[c.url]});try{LS.ntProg=JSON.stringify(P)}catch(x){}toast('Removido de Continuar assistindo.');if(S.view=='browse')renderBrowse()}
function continueList(){const seen=new Set(),out=[];
  Object.keys(P).filter(u=>!P[u].done).sort((a,b)=>P[b].ts-P[a].ts).forEach(u=>{const x=S.byUrl.get(u);if(x&&!seen.has(x.e.key)){seen.add(x.e.key);out.push(x)}});return out.slice(0,24)}
function toggleFav(k){S.fav.has(k)?S.fav.delete(k):S.fav.add(k);LS.ntFav=JSON.stringify([...S.fav]);
  document.querySelectorAll('[data-f]').forEach(b=>{if(b.dataset.f==k)b.classList.toggle('on',S.fav.has(k))});
  if(S.view=='browse'&&(S.tab=='FAVORITOS'||S.fav1))renderBrowse();updBar()}

/* ---------- destaques da internet ---------- */
const J=async u=>{const r=await fetch(u);if(!r.ok)throw Error('HTTP '+r.status);return r.json()};
async function loadHL(force){
  let c=null;try{c=JSON.parse(LS.ntHl||'null')}catch(e){}
  if(!force&&c&&Date.now()-c.ts<6*36e5&&(c.movies.length||c.series.length)){HL=c;applyHL();return}
  const key=(LS.tmdbKey||'').trim(),out={ts:Date.now(),movies:[],series:[],tips:[]};
  try{
    if(key){const T=p=>tmdb(p+(p.includes('?')?'&':'?')+'language=pt-BR');
      const mp=(x,k)=>({t:x.title||x.name,o:x.original_title||x.original_name,y:(x.release_date||x.first_air_date||'').slice(0,4),r:x.vote_average?+x.vote_average.toFixed(1):0,img:x.poster_path?'https://image.tmdb.org/t/p/w342'+x.poster_path:'',k});
      const [m,s,tm,ts]=await Promise.all([T('trending/movie/week'),T('trending/tv/week'),T('movie/top_rated'),T('tv/top_rated')].map(x=>x.then(r=>r||{results:[]})));
      out.movies=m.results.map(x=>mp(x,'m'));out.series=s.results.map(x=>mp(x,'s'));out.tips=tm.results.map(x=>mp(x,'m')).concat(ts.results.map(x=>mp(x,'s')))}
    else{
      const days=[...Array(5)].map((_,i)=>new Date(Date.now()-i*864e5).toISOString().slice(0,10));
      const arr=await Promise.all(days.map(d=>J('https://api.tvmaze.com/schedule/web?date='+d).catch(()=>[])));const seen=new Map();
      for(const ep of arr.flat()){const sh=ep&&ep._embedded&&ep._embedded.show;if(!sh||seen.has(sh.id))continue;
        seen.set(sh.id,{t:sh.name,o:sh.name,y:(sh.premiered||'').slice(0,4),r:(sh.rating&&sh.rating.average)||0,img:sh.image?String(sh.image.medium||'').replace('http:','https:'):'',k:'s',w:sh.weight||0})}
      out.series=[...seen.values()].sort((a,b)=>b.w-a.w).slice(0,50);out.tips=[...seen.values()].filter(x=>x.r>=7.3).sort((a,b)=>b.r-a.r).slice(0,30);
      try{const m=await J('https://itunes.apple.com/br/rss/topmovies/limit=50/json');
        out.movies=(m.feed.entry||[]).map(x=>{const im=(x['im:image']||[]).slice(-1)[0];return{t:x['im:name'].label,o:x['im:name'].label,y:String(x['im:releaseDate']&&x['im:releaseDate'].label||'').slice(0,4),r:0,img:im?im.label.replace(/\d+x\d+bb/,'300x450bb'):'',k:'m'}})}catch(e){out.merr=1}}
  }catch(e){out.err=String(e.message||e)}
  if(out.movies.length||out.series.length)try{LS.ntHl=JSON.stringify(out)}catch(e){}
  HL=out;applyHL();if(S.view=='browse'&&['RECOMENDAÇÕES','FILMES','SÉRIES'].includes(S.tab)&&!S.q.trim()&&!S.group&&!S.fav1&&S.type=='all')renderBrowse()}
function applyHL(){const f=i=>{i.e=S.idx[i.k].get(norm(i.t))||S.idx[i.k].get(norm(i.o))||null;if(i.e&&i.r)i.e.r=i.r;return i};
  HL.movies.forEach(f);HL.series.forEach(f);HL.tips.forEach(f)}

/* ---------- player ---------- */
const v=$('#v');let eng=null,tok=0,retries=0,wd=null,resumeAt=0,FFM=null,chk=null;
// FFM: modo FFmpeg ativo {eng,base,id,dur,live} — o tempo do vídeo começa em "base" (avançar = reiniciar o FFmpeg nesse ponto)
const FFOK=()=>!!(window.NT_PROXY&&window.NT_FF);
const curT=()=>FFM&&!FFM.live?FFM.base+(v.currentTime||0):v.currentTime;
const durT=()=>FFM&&!FFM.live?(FFM.dur||0):v.duration;
function order(u,cat){const x=u.split('?')[0].toLowerCase();if(/m3u8/.test(u))return['hls','native','mpegts'];if(x.endsWith('.ts'))return['mpegts','native'];
  if(/\.(mp4|mkv|webm|mov|m4v|mp3|aac)$/.test(x))return['native','mpegts'];return cat=='live'?['mpegts','hls','native']:['native','hls','mpegts']}
function stop(){if(window.ntNative&&ntNative.stop)ntNative.stop();const qs=$('#qsel');if(qs)qs.hidden=true;clearTimeout(chk);if(eng){try{eng.destroy()}catch(e){}eng=null}v.pause();v.removeAttribute('src');v.load();FFM=null;ffBar()}
function ph(a,b,show){$('#ph').hidden=!show;if(a)$('#ph1').textContent=a;if(b!=null)$('#ph2').textContent=b}
function status(s){$('#st').textContent=s}
/* conta Xtream: vencimento e telas (usado no aviso de erro e no selo das listas) */
function credsOf(u){try{const x=new URL(u),us=x.searchParams.get('username'),pw=x.searchParams.get('password');if(us&&pw&&/(get|player_api)\.php$/i.test(x.pathname))return{base:x.origin,u:us,p:pw}}catch(e){}
  const m=String(u).match(/^(https?:\/\/[^/]+)\/(?:live\/|movie\/|series\/)?([^/]+)\/([^/]+)\/\d+(?:\.\w+)?(?:\?.*)?$/);return m&&!/^(live|movie|series|hls|play|auth)$/i.test(m[2])?{base:m[1],u:decodeURIComponent(m[2]),p:decodeURIComponent(m[3])}:null}
const api=(c,act,extra)=>c.base+'/player_api.php?username='+encodeURIComponent(c.u)+'&password='+encodeURIComponent(c.p)+(act?'&action='+act:'')+(extra||'');
const ACC={};
async function account(c,force){if(!c)return null;const k=c.base+'|'+c.u,o=ACC[k];if(!force&&o&&Date.now()-o.ts<60000)return o;
  try{const r=await fetch(api(c),{cache:'no-store'});const j=await r.json(),ui=j&&j.user_info;if(!ui)return null;
    const a={exp:(+ui.exp_date||0)*1000,status:String(ui.status||''),max:+ui.max_connections||0,act:+ui.active_cons||0,ts:Date.now()};ACC[k]=a;return a}catch(e){return null}}
async function listCredsFor(c){const L=(await dbAll().catch(()=>[]))||[];const o=L.find(x=>x.name==c.src&&x.url)||L.find(x=>x.url&&getActive().includes(x.id));return(o&&credsOf(o.url))||credsOf(c.url)}
/* motivo da falha em português, a partir do que o repasse recebeu do servidor e da conta */
async function whyFail(c){const PX=window.NT_PROXY;let le=null,ac=null;
  if(PX)try{le=await(await fetch('http://127.0.0.1:'+PX+'/lasterr')).json()}catch(e){}
  if(le&&Date.now()-le.t>120000)le=null;
  if(le&&(le.status==404||le.status==410))return'Este canal/arquivo está fora do ar no servidor da lista (HTTP '+le.status+'). Tente outra versão dele (FHD/HD/SD).';
  try{ac=await account(await listCredsFor(c),true)}catch(e){}
  if(ac){if(ac.exp&&ac.exp<Date.now())return'A conta desta lista venceu em '+new Date(ac.exp).toLocaleDateString('pt-BR')+'. Renove com o seu fornecedor.';
    if(ac.status&&!/active/i.test(ac.status))return'A conta desta lista está '+ac.status+' no servidor (não está ativa).';
    if(ac.max&&ac.act>=ac.max)return'Sua conta permite '+ac.max+' tela'+(ac.max>1?'s':'')+' e '+(ac.max>1?'todas estão':'ela está')+' em uso (outro aparelho, VLC ou app aberto). Feche o outro e toque em Tentar de novo.'}
  if(le){const st=le.status;if(st==404||st==410)return'Este canal está fora do ar no servidor da lista (HTTP '+st+'). Tente outra versão dele (FHD/HD/SD).';
    if(st==0)return'O servidor da lista não respondeu ('+le.body+'). Pode estar lento ou fora do ar — tente de novo em instantes.';
    if(st==401||st==403||st==405||st==456||st==458||st==509||st==551||st==429)return'O servidor recusou o canal (HTTP '+st+(le.body?': '+le.body.slice(0,90):'')+'). Normalmente é a conta em uso em outro aparelho ou o limite de telas.';
    if(st>=500)return'O servidor da lista está com problema (HTTP '+st+'). Tente de novo em instantes.'}
  return''}
function play(c,auto,force){
  stop();const my=++tok;if(!auto)retries=0;const x=S.cur;
  if(c.cat!='live'&&!auto&&!force){const p=P[c.url];resumeAt=p&&!p.done&&p.t>10?p.t:0}
  updBar();document.querySelectorAll('.pc.cur,.rw.cur').forEach(el=>el.classList.remove('cur'));
  document.querySelectorAll('[data-u]').forEach(el=>{if(el.dataset.u==c.url)el.classList.add('cur')});
  if(window.bobNative&&LS.bobVlc=='1'&&!force){const id=LS.ntExt||'vlc';ph('Tocando no player externo','Esta janela continua com a lista; o vídeo abre fora.',true);bobNative.openExt(id,c.url,c.cat=='live').then(r=>{if(r&&r.error)toast(r.error,'',null,'er')});return}
  if(window.ntNative&&ntNative.ok&&ntNative.play(c,x?x.e.title:'',resumeAt,my)){ph('Tocando em tela cheia','Use Voltar para retornar à lista.',true);return}
  ph('Carregando…',x?x.e.title:'',true);
  const q=order(c.url,c.cat),errs=[];
  // PC: canais ao vivo passam pelo repasse local (cara de VLC, espera a conexão única liberar e emenda os pedaços)
  const PX=window.NT_PROXY,live=c.cat=='live',ff=FFOK(),pm=LS.ntPMode||'auto';
  const viaP=(u,ts)=>PX&&live?'http://127.0.0.1:'+PX+'/p?'+(ts?'live=1&':'')+'u='+encodeURIComponent(u):u;
  const hlsUrl=(live&&/\/live\/[^/]+\/[^/]+\/\d+\.ts(\?|$)/.test(c.url))?c.url.replace(/\.ts(\?|$)/,'.m3u8$1'):(live&&/\/\/[^/]+\/[^/]+\/[^/]+\/\d+$/.test(c.url.split('?')[0])?c.url.split('?')[0]+'.m3u8':c.url);
  const tsUrl=/\/live\//.test(c.url)&&/\.m3u8(\?|$)/i.test(c.url)?c.url.replace(/\.m3u8(\?|$)/i,'.ts$1'):(/\/live\//.test(c.url)&&!/\.[a-z0-9]{2,4}(\?|$)/i.test(c.url.split('/').pop())?c.url+'.ts':'');
  dbgReset(c);
  if(live){q.length=0;
    if(PX){dbg('conexão única (sem sonda)');if(tsUrl&&/\.m3u8(\?|$)/i.test(c.url))q.push('mpegtsT');q.push('mpegts','hls','native')}
    else{q.push('mpegts');if(window.Hls)q.push('hls2');q.push('native')}}
  // 4K / H.265: se o Chromium não decodifica HEVC, converte direto no FFmpeg
  const hevcName=/h\.?265|hevc|\b4k\b|uhd|2160/i.test((x?x.e.title:'')+' '+c.name),hevcOk=!!(window.MediaSource&&MediaSource.isTypeSupported('video/mp4; codecs="hvc1.1.6.L150.B0"'));
  // PC, ao vivo: começa pelo FFmpeg (copia o vídeo e refaz os tempos dos pedaços emendados; é o que evita as travadas)
  if(ff&&live&&pm=='auto'&&LS.ntLiveFF!='0')q.unshift('ffcopy');
  if(ff){if(pm=='ff')q.unshift('ffcopy');else if(pm=='ffenc'||(hevcName&&!hevcOk))q.unshift('ffenc');q.push('ffcopy','ffenc')}
  if(force&&force.eng){q.length=0;q.push(force.eng);if(force.eng=='ffcopy')q.push('ffenc')}
  const uniq=[];q.forEach(e=>{if(!uniq.includes(e))uniq.push(e)});q.length=0;q.push(...uniq);
  const fail=async()=>{if(my!=tok)return;const why=await whyFail(c);if(my!=tok)return;
    ph('Não foi possível reproduzir',why||'Tente outro item ou um player externo.',true);dbg('motivo: '+(why||errs.join(' | ')));
    toast(why?why:'Falha ao reproduzir: '+errs.join(' | ')+(window.bobNative?' — tente um player externo (VLC, mpv, PotPlayer).':''),'Tentar de novo',()=>play(c,true),'er')};
  const next=why=>{if(my!=tok)return;errs.push(why);dbg('falhou: '+why);stop();
    // recusa HTTP (conta em uso, canal fora do ar): outros players pegariam a mesma recusa
    if(/HttpStatusCodeInvalid|manifestLoadError/.test(why)&&PX){fetch('http://127.0.0.1:'+PX+'/lasterr').then(r=>r.json()).then(le=>{if(my!=tok)return;
      if(le&&Date.now()-le.t<60000&&le.status&&![404,410].includes(le.status)){q.length=0;fail()}else if(q.length)run();else fail()}).catch(()=>q.length?run():fail());return}
    if(q.length)run();else fail()};
  v.onerror=()=>next('mídia ('+(v.error?v.error.code:'?')+')');
  v.onplaying=()=>{if(my!=tok)return;ph('','',false);dbg('tocando ('+(FFM?'FFmpeg '+FFM.eng+' • ':'')+(isFinite(v.duration)&&!FFM?Math.round(v.duration)+'s':live?'ao vivo':'vod')+')');
    // depois de alguns segundos: tem imagem? tem som? (codecs que o Chromium não abre ficam mudos ou pretos)
    clearTimeout(chk);chk=setTimeout(()=>{if(my!=tok||v.paused)return;const vf=v.getVideoPlaybackQuality?v.getVideoPlaybackQuality().totalVideoFrames:1,ab=v.webkitAudioDecodedByteCount;
      dbg('checagem: quadros='+vf+' áudio='+(ab==null?'?':ab)+' '+v.videoWidth+'x'+v.videoHeight);
      if(!ff||!q.length)return;const at=curT();
      if((!vf||!v.videoWidth)&&!(FFM&&FFM.eng=='ffenc')){dbg('sem imagem (codec de vídeo não suportado) — convertendo no FFmpeg');toast('Vídeo em formato não suportado (ex.: H.265). Convertendo…');q.length=0;q.push('ffenc');if(!live)resumeAt=at;stop();run()}
      else if(ab===0&&!FFM&&!/radio|r[aá]dio|fm\b/i.test(c.name)){dbg('sem som (AC3/EAC3/DTS) — convertendo o áudio no FFmpeg');toast('Áudio em formato não suportado (AC3/DTS). Convertendo…');q.length=0;q.push('ffcopy','ffenc');if(!live)resumeAt=at;stop();run()}},6000)};
  v.onloadedmetadata=()=>{if(my==tok&&!FFM&&resumeAt&&isFinite(v.duration)&&resumeAt<v.duration-30){v.currentTime=resumeAt;const t=resumeAt;resumeAt=0;
    toast('Continuando de '+fmt(t),'Do início',()=>{v.currentTime=0})}};
  function run(){if(my!=tok)return;let e=q.shift();
    if(e=='ffcopy'||e=='ffenc'){const ss=!live?(force&&force.ss!=null?force.ss:resumeAt||0):0,id=String(Date.now())+Math.random().toString(36).slice(2,6);
      if(ss&&!force)toast('Continuando de '+fmt(ss),'Do início',()=>play(c,true,{eng:e,ss:0}));force=null;resumeAt=0;
      const src='http://127.0.0.1:'+PX+'/ff?v='+(e=='ffenc'?'enc':'copy')+'&live='+(live?1:0)+'&ss='+Math.floor(ss)+'&id='+id+'&u='+encodeURIComponent(c.url);
      dbg('tentando: FFmpeg '+(e=='ffenc'?'(converte vídeo + áudio)':'(copia vídeo, áudio AAC)')+(ss?' a partir de '+fmt(ss):''));ph('Carregando…',(x?x.e.title:'')+' • modo compatibilidade (FFmpeg)',true);
      const p=eng=mpegts.createPlayer({type:'mpegts',isLive:true,url:src},{enableWorker:true,enableStashBuffer:true,stashInitialSize:live?512*1024:1024*1024,liveBufferLatencyChasing:false,liveBufferLatencyMaxLatency:6,liveBufferLatencyMinRemain:1.2,lazyLoad:false,autoCleanupSourceBuffer:true,autoCleanupMaxBackwardDuration:60,autoCleanupMinBackwardDuration:30});
      FFM={eng:e,base:ss,id,dur:0,live};ffBar();p.attachMediaElement(v);p.on(mpegts.Events.ERROR,(t,d)=>{if(my!=tok)return;if(FFM&&!live&&FFM.dur&&curT()>FFM.dur-25){onEnded();return}ffErr(id).then(m=>next('ffmpeg: '+d+(m?' ('+m+')':'')))});p.load();p.play().catch(()=>{});return}
    const src=e=='hls2'?viaP(hlsUrl):e=='hlsD'?hlsUrl:e=='mpegtsD'?c.url:e=='mpegtsT'?viaP(tsUrl,1):viaP(c.url,e=='mpegts'||e=='native');
    dbg('tentando: '+e);
    if(e=='hlsD')e='hls';if(e=='hls2')e='hls';if(e=='mpegtsD'||e=='mpegtsT')e='mpegts';
    if(e=='hls'&&window.Hls&&Hls.isSupported()){const h=eng=new Hls({enableWorker:true,manifestLoadingTimeOut:60000,levelLoadingTimeOut:30000,fragLoadingTimeOut:60000,manifestLoadingMaxRetry:2,levelLoadingMaxRetry:3,fragLoadingMaxRetry:4,maxBufferLength:60,maxMaxBufferLength:120,backBufferLength:30,liveSyncDurationCount:5,maxBufferSize:120e6,capLevelToPlayerSize:true});let rec=0;
      h.loadSource(src);h.attachMedia(v);h.on(Hls.Events.MANIFEST_PARSED,()=>{v.play().catch(()=>{});fillQ(h)});
      h.on(Hls.Events.LEVEL_LOADED,(_,d)=>dbg('hls lista: '+(d.details.live?'AO VIVO':'VOD')+' '+d.details.fragments.length+' seg')) ;h.on(Hls.Events.ERROR,(_,d)=>{dbg('hls erro: '+d.details+(d.fatal?' (fatal)':''));if(!d.fatal)return;if(d.type==Hls.ErrorTypes.MEDIA_ERROR&&!rec++)h.recoverMediaError();else next('hls: '+d.details)})}
    else if(e=='mpegts'&&window.mpegts&&mpegts.isSupported()){
      const p=eng=mpegts.createPlayer({type:'mpegts',isLive:live,url:src},{enableWorker:true,enableStashBuffer:true,stashInitialSize:1024*1024,liveBufferLatencyChasing:false,lazyLoadMaxDuration:180,autoCleanupSourceBuffer:true,autoCleanupMaxBackwardDuration:60,autoCleanupMinBackwardDuration:30});
      p.attachMediaElement(v);p.on(mpegts.Events.ERROR,(t,d)=>next('mpegts: '+d));p.load();p.play().catch(()=>{})}
    else if(e=='native'){v.src=src;v.play().catch(()=>{})}else next(e+': indisponível')}
  run()}
async function ffErr(id){try{const i=await(await fetch('http://127.0.0.1:'+window.NT_PROXY+'/ffinfo?id='+id)).json();return i&&i.err||''}catch(e){return''}}
/* barra própria no modo FFmpeg (filmes/séries): avançar/voltar reinicia a conversão no ponto escolhido */
let ffDrag=false;
function ffBar(){const b=$('#ffb');if(!b)return;const on=!!FFM&&S.view=='player';b.hidden=!on;v.controls=!(FFM&&!FFM.live);if(!on)return;
  $('#ffm').textContent=FFM.eng=='ffenc'?'FFmpeg • vídeo convertido':'FFmpeg • áudio convertido';$('#ffr').disabled=FFM.live;$('#ffseek').hidden=FFM.live}
setInterval(async()=>{if(!FFM||S.view!='player')return;
  if(!FFM.dur&&!FFM.live){try{const i=await(await fetch('http://127.0.0.1:'+window.NT_PROXY+'/ffinfo?id='+FFM.id)).json();if(i&&i.dur&&FFM)FFM.dur=i.dur;if(i&&i.v)$('#ffm').textContent='FFmpeg • '+i.v.toUpperCase()+(i.h?' '+i.h+'p':'')+' → '+(i.enc=='copy'?'vídeo original':'H.264')+' • '+(i.a||'?').toUpperCase()+' → AAC'}catch(e){}}
  if(!FFM)return;const t=curT(),d=durT();$('#fft1').textContent=fmt(t||0);$('#fft2').textContent=FFM.live?'ao vivo':d?fmt(d):'--:--';
  if(!ffDrag&&d){const r=$('#ffr');r.max=Math.floor(d);r.value=Math.floor(t)}},1000);
const fmt=t=>{t=Math.floor(t);const h=Math.floor(t/3600),m=Math.floor(t%3600/60),s=t%60;return(h?h+':'+String(m).padStart(2,'0'):m)+':'+String(s).padStart(2,'0')};
v.addEventListener('timeupdate',()=>saveProg(false));v.addEventListener('pause',()=>saveProg(true));addEventListener('beforeunload',()=>saveProg(true));
v.addEventListener('waiting',()=>{clearTimeout(wd);const be=bufEnd();wd=setTimeout(()=>{if(gapJump('espera'))return;if(S.cur&&S.cur.c.cat=='live'&&window.NT_PROXY&&bufEnd()>be+1)return;if(S.cur&&retries++<3&&v.readyState<3){toast('Reconectando…');saveProg(true);play(S.cur.c,true,FFM?{eng:FFM.eng,ss:FFM.live?0:curT()}:null)}},FFM&&FFM.eng=='ffenc'?20000:S.cur&&S.cur.c.cat=='live'&&window.NT_PROXY?25000:10000)});
/* pula "buracos" no vídeo: quando o servidor demora para mandar o próximo pedaço, o player fica parado
   antes de um trecho que já chegou. O VLC pula sozinho; aqui fazemos o mesmo. */
const bufEnd=()=>{const b=v.buffered;return b.length?b.end(b.length-1):0};
// só pula quando o vídeo REALMENTE travou antes de um trecho que já chegou — um salto único, sem micro-ajustes (que é o que fazia "pular/pipocar")
function gapJump(why){const b=v.buffered,t=v.currentTime;if(!b.length||v.seeking)return false;
  for(let i=0;i<b.length;i++){if(b.start(i)<=t+.1&&b.end(i)>t+.25)return false} // ainda há o que tocar à frente: não mexe
  for(let i=0;i<b.length;i++){const st=b.start(i);if(st>t&&st-t<120&&b.end(i)-st>.8){v.currentTime=st+.03;dbg('pulou um buraco de '+(st-t).toFixed(1)+'s ('+why+')');return true}}
  return false}
// detecta travamento de verdade: tempo parado por ~4 s com o vídeo sem conseguir avançar
let gjT=0,gjLast=-1;setInterval(()=>{if(S.view!='player'||v.paused||!S.cur||v.seeking){gjT=0;return}const t=v.currentTime;
  if(Math.abs(t-gjLast)<.02&&v.readyState<3){gjT+=700;if(gjT>=4000){gjT=0;gapJump('travado')}}else gjT=0;gjLast=t;},700);
v.addEventListener('playing',()=>clearTimeout(wd));
v.addEventListener('ended',onEnded);
function onEnded(){if(S.cur&&S.cur.c.cat=='live'){dbg('fluxo encerrado pelo servidor — reconectando');const f=FFM?{eng:FFM.eng}:null;setTimeout(()=>{if(S.cur&&S.cur.c.cat=='live')play(S.cur.c,true,f)},400);return}if(!S.cur)return;const u=S.cur.c.url;P[u]={t:0,d:Math.floor(durT())||0,ts:Date.now(),done:true};try{LS.ntProg=JSON.stringify(P)}catch(e){}
  if(S.autoNext){const n=nextOf();if(n){toast('Próximo: '+n.e.title+(n.e.kind=='s'?' • T'+n.c.s+' E'+n.c.e:''));switchTo(n.e,n.c)}else toast('Fim da lista. Nada mais para reproduzir.')}}
function nextOf(){const x=S.cur;if(!x)return null;const e=x.e;
  if(e.kind=='s'){const i=e.ch.indexOf(x.c);if(i>=0&&i<e.ch.length-1)return{e,c:e.ch[i+1]}}
  const L=(S.qlist||[]).filter(o=>o.kind==e.kind),i=L.findIndex(o=>o.key==e.key);
  for(let j=i+1;j<L.length&&i>=0;j++){const c=startEp(L[j]);if(c)return{e:L[j],c}}return null}
function prevOf(){const x=S.cur;if(!x)return null;const e=x.e;
  if(e.kind=='s'){const i=e.ch.indexOf(x.c);if(i>0)return{e,c:e.ch[i-1]}}
  const L=(S.qlist||[]).filter(o=>o.kind==e.kind),i=L.findIndex(o=>o.key==e.key);return i>0?{e:L[i-1],c:startEp(L[i-1])}:null}
/* ---------- UI ---------- */
function toast(msg,btn,fn,cls){const t=$('#toast');t.className=cls||'';t.innerHTML='<span>'+esc(msg)+'</span>'+(btn?'<button>'+esc(btn)+'</button>':'');t.hidden=false;
  if(btn)t.querySelector('button').onclick=()=>{fn&&fn();t.hidden=true};clearTimeout(toast.t);toast.t=setTimeout(()=>t.hidden=true,cls=='er'?9000:5000)}

/* imagens, sinopse e notas da internet (capa, banner) */
let INFO={};try{delete LS.ntInfo;INFO=JSON.parse(LS.ntInfo2||'{}')}catch(e){}
const cleanT=t=>t.replace(/\(.*?\)|\[.*?\]/g,' ').replace(/\b(dublado|legendado|dual|nacional|4k|fhd|uhd|hd|1080p|720p|web-?dl)\b/ig,' ').replace(/\s+/g,' ').trim();
const yearOf=t=>{const m=t.match(/\((\d{4})\)/);return m?m[1]:''};const stripH=s=>String(s||'').replace(/<[^>]+>/g,'').trim();
const GEN={Drama:'Drama',Comedy:'Comédia',Crime:'Crime',Action:'Ação',Adventure:'Aventura',Fantasy:'Fantasia','Science-Fiction':'Ficção científica',Horror:'Terror',Thriller:'Suspense',Romance:'Romance',Mystery:'Mistério',Family:'Família',Animation:'Animação',Anime:'Anime',Children:'Infantil',Music:'Música',Western:'Faroeste',War:'Guerra',History:'História',Medical:'Médico',Legal:'Jurídico',Espionage:'Espionagem',Supernatural:'Sobrenatural',Sports:'Esportes',Food:'Culinária',Nature:'Natureza',Travel:'Viagem',Documentary:'Documentário'};
const genPT=g=>String(g||'').split(/\s*[•,]\s*/).filter(Boolean).map(x=>GEN[x]||x).join(' • ');
const ADR=/\bxxx\b|adult|\+18|18\+|er[óo]tic|porn|sexy|sexo/i;const isAdult=e=>ADR.test(e.title+' '+e.group);const showAdult=()=>LS.ntAdult=='1';
function looksPT(s){const w=' '+String(s).toLowerCase().replace(/[^a-zà-ú ]/g,' ')+' ',pt=(w.match(/ (que|não|uma|para|com|dos|das|ele|ela|seu|sua|mais|como|por|são|em) /g)||[]).length,en=(w.match(/ (the|and|of|his|her|with|that|for|who|their|from|are|was|is) /g)||[]).length;return pt>en}
async function trChunk(c){
  try{const r=await J('https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=pt&dt=t&q='+encodeURIComponent(c));const o=(r[0]||[]).map(x=>x[0]).join('');if(o)return o}catch(e){}
  try{const r=await J('https://api.mymemory.translated.net/get?langpair=en|pt-BR&q='+encodeURIComponent(c.slice(0,480)));const o=r&&r.responseData&&r.responseData.translatedText;if(o&&!/MYMEMORY|QUERY LENGTH/i.test(o))return o}catch(e){}
  return c}
async function toPT(t){if(!t||looksPT(t))return t;
  const parts=t.match(/[^.!?]+[.!?]*\s*/g)||[t],ch=[];let cur='';for(const p of parts){if((cur+p).length>450){if(cur)ch.push(cur);cur=p}else cur+=p}if(cur)ch.push(cur);
  const out=[];for(const c of ch)out.push(await trChunk(c));return out.join(' ').trim()||t}
/* garante sinopse em português (usa cache e atualiza a tela) */
function ensurePT(e,redo){const inf=e&&INFO[e.key];if(!inf||!inf.ov||inf.pt===1||TRS.has(e.key))return;
  if(looksPT(inf.ov)){inf.pt=1;return}TRS.add(e.key);
  toPT(inf.ov).then(x=>{if(x&&x!==inf.ov){inf.ov=x;inf.pt=1;saveInfo();redo()}else TRS.delete(e.key)}).catch(()=>TRS.delete(e.key))}
const pend=new Map(),TRS=new Set();let infoT=0;function saveInfo(){clearTimeout(infoT);infoT=setTimeout(()=>{const ks=Object.keys(INFO);if(ks.length>3000)ks.sort((a,b)=>(INFO[a].ts||0)-(INFO[b].ts||0)).slice(0,ks.length-2500).forEach(k=>delete INFO[k]);try{LS.ntInfo2=JSON.stringify(INFO)}catch(e){}},1500)}
const BAN={};async function Jr(p,u){if(BAN[p]>Date.now())throw Error('limite');const r=await fetch(u);if(r.status==404)return null;if(r.status==429||r.status==403){BAN[p]=Date.now()+60000;throw Error('limite')}if(!r.ok)throw Error('HTTP '+r.status);return r.json()}
/* comparação de títulos: igual, ou quase igual (palavras em comum) — evita capa de outro filme */
const okT=(a,b)=>{const x=norm(a),y=norm(b);if(!x||!y)return false;if(x==y)return true;const A=x.split(' '),B=y.split(' '),sb=new Set(B);let n=0;A.forEach(w=>{if(sb.has(w))n++});return 2*n/(A.length+B.length)>=.85};
const okY=(a,b)=>!a||!b||Math.abs(+a-+b)<=1;
/* gêneros TMDB em português (os mesmos de /3/genre/movie/list?language=pt-BR; atualizados pela API quando há chave) */
const TG={28:'Ação',12:'Aventura',16:'Animação',35:'Comédia',80:'Crime',99:'Documentário',18:'Drama',10751:'Família',14:'Fantasia',36:'História',27:'Terror',10402:'Música',9648:'Mistério',10749:'Romance',878:'Ficção científica',10770:'Cinema TV',53:'Thriller',10752:'Guerra',37:'Faroeste',10759:'Ação e Aventura',10762:'Kids',10763:'Notícias',10764:'Reality',10765:'Ficção científica e Fantasia',10766:'Novela',10767:'Talk show',10768:'Guerra e Política'};
const tmdbKey=()=>(LS.tmdbKey||'').trim();
function tmdb(path){const k=tmdbKey(),v4=k.length>40,u='https://api.themoviedb.org/3/'+path+(path.includes('?')?'&':'?')+(v4?'':'api_key='+encodeURIComponent(k));
  if(BAN.tmdb>Date.now())return Promise.reject(Error('limite'));
  return fetch(u,v4?{headers:{Authorization:'Bearer '+k,accept:'application/json'}}:{}).then(r=>{if(r.status==404)return null;if(r.status==429){BAN.tmdb=Date.now()+60000;throw Error('limite')}if(!r.ok)throw Error('HTTP '+r.status);return r.json()})}
let tgP=null;const loadTG=()=>tgP||(tgP=Promise.all(['movie','tv'].map(k=>tmdb('genre/'+k+'/list?language=pt-BR').then(r=>(r&&r.genres||[]).forEach(g=>{TG[g.id]=g.name})).catch(()=>{}))));
const tmImg=(p,w)=>p?'https://image.tmdb.org/t/p/'+w+p:'';
async function pTMDB(e,t,y,id){if(!tmdbKey())return null;loadTG();const tv=e.kind=='s'?'tv':'movie';let x=null;
  if(id)x=await tmdb(tv+'/'+id+'?language=pt-BR').catch(()=>null);
  if(!x){const r=await tmdb('search/'+tv+'?language=pt-BR&include_adult=false&query='+encodeURIComponent(t)+(y&&tv=='movie'?'&primary_release_year='+y:''));
    let best=null,bs=0;for(const o of (r&&r.results||[]).slice(0,10)){const yy=(o.release_date||o.first_air_date||'').slice(0,4);let sc=0;
      if(okT(o.title||o.name,t)||okT(o.original_title||o.original_name,t))sc+=3;if(y&&yy==y)sc+=2;else if(y&&!okY(y,yy))sc-=3;if(o.poster_path)sc+=.5;sc+=Math.min(1,(o.popularity||0)/100);
      if(sc>bs){bs=sc;best=o}}
    if(!best||bs<3)return null;x=best}
  let ov=x.overview||'',pt=1;
  if(!ov){const en=await tmdb(tv+'/'+x.id+'?language=en-US').catch(()=>null);ov=en&&en.overview||'';pt=0}
  const gs=(x.genres||[]).map(g=>g.name).concat((x.genre_ids||[]).map(i=>TG[i]).filter(Boolean));
  const ja=x.original_language=='ja',an=ja&&((x.genre_ids||[]).includes(16)||(x.genres||[]).some(g=>g.id==16));
  return{img:tmImg(x.poster_path,'w342'),bn:tmImg(x.backdrop_path,'w1280'),ov,pt,r:x.vote_average?+x.vote_average.toFixed(1):0,y:(x.release_date||x.first_air_date||'').slice(0,4),g:[...new Set(gs)].join(' • '),an:an?1:0}}
/* Xtream: capa e sinopse direto do servidor da lista (mesmo ID do item, então a capa é a certa) */
const XS=new Map();
function xSeries(c){const k=c.base+'|'+c.u;if(!XS.has(k))XS.set(k,fetch(api(c,'get_series')).then(r=>r.ok?r.json():[]).then(a=>{const m=new Map();(Array.isArray(a)?a:[]).forEach(o=>{const n=norm(serT(o.name||''));if(n&&!m.has(n))m.set(n,o)});return m}).catch(()=>{XS.delete(k);return new Map()}));return XS.get(k)}
const xs=(o,k)=>{const v=o&&o[k];return v==null||v=='null'?'':Array.isArray(v)?String(v[0]||''):String(v).trim()};
function xOut(o){if(!o)return null;const img=xs(o,'movie_image')||xs(o,'cover_big')||xs(o,'cover')||xs(o,'stream_icon'),ov=xs(o,'plot')||xs(o,'description'),r=parseFloat(xs(o,'rating').replace(',','.'))||0;
  const out={img:/^https?:/.test(img)?img:'',bn:xs(o,'backdrop_path'),ov,r:r>10?+(r/10).toFixed(1):+r.toFixed(1),y:(xs(o,'releasedate')||xs(o,'releaseDate')||xs(o,'release_date')).slice(0,4).replace(/\D/g,''),g:xs(o,'genre'),tmdb:xs(o,'tmdb_id')||xs(o,'tmdb')};
  if(!/^https?:/.test(out.bn))out.bn='';if(/^(n\/a|sem sinopse|no plot|null)$/i.test(out.ov))out.ov='';return out.img||out.ov?out:null}
async function pXtream(e,t){const u=e.ch[0].url;
  if(e.kind=='m'){const m=u.match(/^(https?:\/\/[^/]+)\/movie\/([^/]+)\/([^/]+)\/(\d+)\.\w+/);if(!m)return null;const c={base:m[1],u:decodeURIComponent(m[2]),p:decodeURIComponent(m[3])};
    const j=await Jr('xt',api(c,'get_vod_info','&vod_id='+m[4]));return xOut(j&&j.info&&!Array.isArray(j.info)?j.info:null)}
  const c=credsOf(u);if(!c)return null;const map=await xSeries(c);const n=norm(t);let o=map.get(n);
  if(!o)for(const[k,x]of map){if(okT(k,n)){o=x;break}}if(!o)return null;let out=xOut(o);
  if((!out||!out.ov)&&o.series_id){const j=await Jr('xt',api(c,'get_series_info','&series_id='+o.series_id)).catch(()=>null);const i=xOut(j&&j.info);if(i)out=Object.assign(i,out||{},{ov:i.ov})}
  return out}
async function pWiki(e,t,y,lang){lang=lang||'pt';const q=t+(e.kind=='s'?(lang=='pt'?' série':' TV series'):(lang=='pt'?' filme':' film')+(y?' '+y:''));
  const r=await Jr('wiki'+lang,'https://'+lang+'.wikipedia.org/w/api.php?action=query&format=json&origin=*&generator=search&gsrnamespace=0&gsrlimit=5&prop=pageimages|extracts&piprop=thumbnail&pithumbsize=500&exintro=1&explaintext=1&exlimit=max&exsentences=4&gsrsearch='+encodeURIComponent(q));
  const pg=Object.values((r&&r.query&&r.query.pages)||{}).sort((a,b)=>a.index-b.index).find(p=>okT(p.title.replace(/\s*\(.*?\)\s*/g,' '),t)&&(p.thumbnail||p.extract)&&(!y||!p.extract||p.extract.includes(y)||e.kind=='s'));
  return pg?{img:pg.thumbnail?pg.thumbnail.source:'',ov:pg.extract||'',pt:lang=='pt'?1:0}:null}
async function pTVMaze(e,t,y){const x=await Jr('tvmaze','https://api.tvmaze.com/singlesearch/shows?q='+encodeURIComponent(t));if(!x||!okT(x.name,t)||!okY(y,String(x.premiered||'').slice(0,4)))return null;
  const an=(x.genres||[]).includes('Anime')||(x.language=='Japanese'&&x.type=='Animation');
  return{img:x.image?String(x.image.original||x.image.medium||'').replace('http:','https:'):'',ov:stripH(x.summary),r:(x.rating&&x.rating.average)||0,y:String(x.premiered||'').slice(0,4),g:genPT((x.genres||[]).join(' • ')),an:an?1:0}}
async function pITunes(e,t,y){const r=await Jr('itunes','https://itunes.apple.com/search?media='+(e.kind=='s'?'tvShow&entity=tvSeason':'movie')+'&country=br&limit=8&term='+encodeURIComponent(t));
  const x=r&&(r.results||[]).find(x=>okT(e.kind=='s'?String(x.artistName||x.collectionName||'').replace(/,?\s*(temporada|season)\s*\d+.*$/i,''):x.trackName,t)&&okY(y,String(x.releaseDate||'').slice(0,4)));
  return x?{img:String(x.artworkUrl100||'').replace('100x100bb','600x900bb'),ov:x.longDescription||x.shortDescription||x.description||'',y:String(x.releaseDate||'').slice(0,4),g:x.primaryGenreName||''}:null}

function getInfo(e){if(!e||e.kind=='l')return Promise.resolve(null);if(isAdult(e))return Promise.resolve({none:1,ts:Date.now()});
  const c=INFO[e.key];if(c&&(!c.none||Date.now()-c.ts<216e5)){
    if(c.ov&&c.pt!==1&&!looksPT(c.ov)&&!TRS.has(e.key)){TRS.add(e.key);return toPT(c.ov).then(x=>{if(x!==c.ov){c.ov=x;c.pt=1;saveInfo()}return c})}return Promise.resolve(c)}
  if(pend.has(e.key))return pend.get(e.key);
  const t=cleanT(e.kind=='s'?serT(e.title.replace(/\s*\(Legendado\)$/,'')):e.title),y=yearOf(e.title);
  const p=(async()=>{let o={},tmp=false,tm='';const take=r=>{if(!r)return;if(r.tmdb&&!tm)tm=r.tmdb;if(r.an)o.an=1;for(const k of['img','bn','ov','r','y','g'])if(!o[k]&&r[k]){o[k]=r[k];if(k=='ov')o.pt=r.pt?1:0}};
    const done=()=>o.img&&o.ov&&(o.r||o.g),step=async f=>{if(done())return;try{take(await f())}catch(err){tmp=true}};
    await step(()=>pXtream(e,t));await step(()=>pTMDB(e,t,y||o.y,tm));
    await step(()=>e.kind=='s'?pTVMaze(e,t,y):pITunes(e,t,y));await step(()=>pWiki(e,t,y,'pt'));
    if(!o.img||!o.ov){await step(()=>e.kind=='s'?pITunes(e,t,y):pTVMaze(e,t,y).catch(()=>null));await step(()=>pWiki(e,t,y,'en'))}
    if(o.ov){o.ov=stripH(o.ov).slice(0,900);if(!o.pt){if(looksPT(o.ov))o.pt=1;else{const x=await toPT(o.ov);o.pt=x!==o.ov?1:0;o.ov=x}}}
    if(o.g)o.g=genPT(o.g);
    if(!(o.img||o.ov||o.r)){if(tmp){pend.delete(e.key);return{none:1,tmp:1,ts:Date.now()}}o={none:1}}
    if(o.an&&e.th!='ANIMES')e.th='ANIMES';
    o.ts=Date.now();INFO[e.key]=o;saveInfo();pend.delete(e.key);return o})();
  pend.set(e.key,p);return p}
const Q=[];let busy=0;function pump(){while(busy<2&&Q.length){const[e,ok]=Q.shift();busy++;getInfo(e).then(ok).finally(()=>setTimeout(()=>{busy--;pump()},500))}}
const qinfo=e=>new Promise(ok=>{Q.push([e,ok]);pump()});
function fillImg(el){const e=S.map.get(el.dataset.k);if(!e||e.kind=='l'||isAdult(e))return;
  const apply=inf=>{if(inf&&inf.img&&el.isConnected){const box=el.querySelector('.im')||el.querySelector('.th');if(box&&!box.querySelector('img')){const im=document.createElement('img');im.referrerPolicy='no-referrer';im.onerror=()=>im.remove();im.src=inf.img;box.insertBefore(im,box.querySelector('.grad'))}}};
  const c=INFO[e.key];if(c&&(!c.none||Date.now()-c.ts<216e5)){apply(c);return}qinfo(e).then(apply)}
function needImg(img){const card=img.closest('.pc,.rw');img.remove();if(card&&card.dataset.k&&IO){card.dataset.need='1';IO.observe(card)}}
const IO=window.IntersectionObserver?new IntersectionObserver(es=>es.forEach(en=>{if(en.isIntersecting){IO.unobserve(en.target);fillImg(en.target)}}),{rootMargin:LITE()?'80px':'250px'}):null;

const isMixed=()=>!!S.q.trim()||['RECOMENDAÇÕES','INFANTIL','ANIMES','FAVORITOS'].includes(S.tab);
function baseList(){const q=norm(S.q);let L;
  if(q||S.tab=='RECOMENDAÇÕES')L=S.every;else if(S.tab=='Vivo')L=S.live;else if(S.tab=='FILMES')L=S.mov;else if(S.tab=='SÉRIES')L=S.ser;
  else if(S.tab=='FAVORITOS')L=S.every.filter(e=>S.fav.has(e.key));else if(S.tab=='FUTEBOL')L=futSportCh();else L=S.every.filter(e=>e.th==S.tab);
  if(q)L=L.filter(e=>norm(e.title).includes(q)||norm(e.group).includes(q));return L}
function view(){let L=baseList();if(isMixed()&&S.type!='all')L=L.filter(e=>e.kind==S.type);if(S.fav1)L=L.filter(e=>S.fav.has(e.key));
  const gl=L;if(S.group)L=L.filter(e=>e.group==S.group);if(S.sort=='az')L=[...L].sort((a,b)=>a.title.localeCompare(b.title,'pt'));return{L,gl}}

/* componentes */
const TITLES={Vivo:'TV ao vivo',FUTEBOL:'Futebol','RECOMENDAÇÕES':'Recomendações',FILMES:'Filmes','SÉRIES':'Séries',INFANTIL:'Infantil',ANIMES:'Animes',FAVORITOS:'Meus favoritos'};
const lab=t=>t=='Vivo'?'TV ao vivo':t;
const bgOf=t=>{let h=0;for(const ch of t)h=(h*31+ch.charCodeAt(0))%360;return`background:linear-gradient(160deg,hsl(${h} 60% 32%),hsl(${(h+50)%360} 65% 14%))`};
const ini=e=>esc((e.title.replace(/[^\p{L}\p{N}]/gu,'')[0]||'?').toUpperCase());
const imgTag=u=>u?`<img loading="lazy" referrerpolicy="no-referrer" src="${esc(u)}" onerror="needImg(this)">`:'';
const star=e=>`<button class="fv${S.fav.has(e.key)?' on':''}" data-f="${esc(e.key)}" title="Favoritar">★</button>`;
function pcard(e,c,rm){const cur=S.cur&&S.cur.e.key==e.key,p=eprog(e),inf=INFO[e.key]||{},lg=e.logo||inf.img||'',rt=e.r||inf.r;
  return`<div class="pc${cur?' cur':''}" data-k="${esc(e.key)}"${c?` data-c="${esc(c.url)}"`:''} data-u="${esc(e.ch[0].url)}"${lg||inf.none||isAdult(e)?'':' data-need="1"'} tabindex="0">${rm?`<span class="rmb" data-rm="${esc(e.key)}" role="button" tabindex="0" title="Remover de Continuar assistindo" aria-label="Remover">✕</span>`:''}<div class="im" style="${bgOf(e.title)}"><span class="ini">${ini(e)}</span>${imgTag(lg)}<div class="grad"></div>
   ${rt?`<span class="rt">★ ${rt}</span>`:''}<span class="tr">${e.kind=='s'?e.ns+(e.ns>1?' temps.':' temp.'):'HD'}</span><div class="ov"><i>${ic('play',1)}</i></div>
   <div class="nm"><div class="t">${esc(e.title)}</div><div class="g">${esc(e.group)}</div></div>${star(e)}${p?`<div class="pb"><i style="width:${p}%"></i></div>`:''}</div></div>`}
function wcard(e){const cur=S.cur&&S.cur.e.key==e.key;
  return`<div class="pc wc${cur?' cur':''}" data-k="${esc(e.key)}" data-u="${esc(e.ch[0].url)}" tabindex="0"><div class="im"><span class="ini">${ini(e)}</span>${imgTag(e.logo)}<div class="grad"></div><span class="tl">AO VIVO</span><div class="ov"><i>${ic('play',1)}</i></div></div><div class="nm"><div class="t">${esc(e.title)}</div><div class="g">${esc(e.group)}</div></div>${star(e)}</div>`}
const card=e=>e.kind=='l'?wcard(e):pcard(e);
const row=(t,items,sub,all)=>items.length?`<section class="rs"><div class="sh"><h3>${t}</h3><span>${sub||''}</span>${all?`<button class="va" data-all="${esc(all)}">Ver tudo ›</button>`:''}</div><div class="rw2"><button class="ar l" data-ar="-1">‹</button><div class="hs">${items.join('')}</div><button class="ar r" data-ar="1">›</button></div></section>`:'';
function groupRows(L,max){const m=new Map();L.forEach(e=>{if(!m.has(e.group))m.set(e.group,[]);m.get(e.group).push(e)});
  return[...m].sort((a,b)=>b[1].length-a[1].length).slice(0,max||30).map(([g,a])=>row(esc(g),a.slice(0,LITE()?12:24).map(card),a.length.toLocaleString('pt-BR')+(a[0].kind=='l'?' canais':' títulos'),a[0].kind+'|'+g)).join('')}
function heroItems(){const t=S.tab,hm=HL.movies.filter(i=>i.e).map(i=>i.e),hs=HL.series.filter(i=>i.e).map(i=>i.e);let a=[];
  if(t=='FILMES')a=hm;else if(t=='SÉRIES')a=hs;else if(t=='RECOMENDAÇÕES'){for(let i=0;i<Math.max(hm.length,hs.length);i++){if(hm[i])a.push(hm[i]);if(hs[i])a.push(hs[i])}}
  if(a.length<4){const seen=new Set(a.map(e=>e.key)),th=t=='INFANTIL'||t=='ANIMES',add=L=>{for(const e of L){if(a.length>=6)return;if(e.logo&&!seen.has(e.key)&&(!th||e.th==t)){a.push(e);seen.add(e.key)}}};
    if(t=='FILMES')add(S.mov);else if(t=='SÉRIES')add(S.ser);else{add(S.mov);add(S.ser)}}
  return a.slice(0,6)}
function drawHero(){const H=$('#hero');if(!H||!S.hero.items.length)return;const e=S.hero.items[S.hero.i%S.hero.items.length],inf=INFO[e.key]||{};ensurePT(e,()=>{if(S.view=='browse'&&$('#hero')&&S.hero.items[S.hero.i%S.hero.items.length]==e)drawHero()});
  const bn=inf.bn||'',poster=inf.img||e.logo||'',bg=bn||poster,rt=e.r||inf.r,pr=eprog(e),w=lastWatched(e);
  H.innerHTML=`<div class="hbg" style="${bg?`background-image:url('${esc(bg)}')`:bgOf(e.title)}"></div><div class="hsh"></div>
   <div class="hc"><div class="hm"><span class="tag">${e.kind=='s'?'SÉRIE':'FILME'}</span>${inf.y?`<span>${esc(inf.y)}</span>`:''}${rt?`<span class="rt2">★ ${rt}</span>`:''}${inf.g?`<span>${esc(genPT(inf.g))}</span>`:`<span>${esc(e.group)}</span>`}</div>
   <h1>${esc(e.title)}</h1><p>${esc(inf.ov||'')}</p>
   <div class="hb"><button class="pk big" data-play="${esc(e.key)}">▶ ${pr||w?'Continuar':'Assistir'}</button><button class="ib2" data-det="${esc(e.key)}">ℹ Detalhes</button><button class="ib2${S.fav.has(e.key)?' on':''}" data-f="${esc(e.key)}">★ Minha lista</button></div></div>
   ${!bn&&poster?`<div class="hp"><img referrerpolicy="no-referrer" src="${esc(poster)}" onerror="this.parentNode.remove()"></div>`:''}
   <div class="hd2">${S.hero.items.map((_,j)=>`<i class="${j==S.hero.i?'on':''}" data-hi="${j}"></i>`).join('')}</div>`;
  if(!INFO[e.key])getInfo(e).then(()=>{if(S.view=='browse'&&S.hero.items[S.hero.i%S.hero.items.length]===e)drawHero()})}
function filterBar(gl,rows,n){const T=[['all','Tudo'],['l','Canais'],['m','Filmes'],['s','Séries']],q=S.q.trim();
  let h=`<div class="fb"><div class="fr"><h2 class="pgt">${q?'Resultados para “'+esc(q)+'”':TITLES[S.tab]}<small>${rows?'':n.toLocaleString('pt-BR')+' itens'}</small></h2>`;
  if(isMixed())h+=T.map(t=>`<button class="ch2${S.type==t[0]?' on':''}" data-t="${t[0]}">${t[1]}</button>`).join('');
  h+=`<button class="ch2${S.fav1?' on':''}" data-fv="1">★ Favoritos</button><select id="sort"><option value="">Ordem: padrão</option><option value="az"${S.sort=='az'?' selected':''}>Ordem: A–Z</option></select></div>`;
  if(!(rows&&isMixed())&&!catMode()){const m=new Map();gl.forEach(e=>m.set(e.group,(m.get(e.group)||0)+1));const gs=[...m].sort((a,b)=>ADR.test(a[0])-ADR.test(b[0])||a[0].localeCompare(b[0],'pt')).slice(0,80);
    if(gs.length>1)h+=`<div class="fr"><div class="gc"><button class="ch2${S.group?'':' on'}" data-g="">Todas<em>${gl.length}</em></button>${gs.map(g=>`<button class="ch2${S.group==g[0]?' on':''}" data-g="${esc(g[0])}">${esc(g[0])}<em>${g[1]}</em></button>`).join('')}</div></div>`}
  return h+'</div>'}
function pageRows(){const t=S.tab;let h='';S.hero={items:t=='Vivo'||t=='FAVORITOS'||t=='FUTEBOL'?[]:heroItems(),i:0};if(S.hero.items.length)h+='<div id="hero"></div>';
  if(t=='RECOMENDAÇÕES'){const cnt=tt=>S.every.filter(e=>e.th==tt).length;h+='<div class="sc">'+[['Vivo','📺','TV ao vivo',S.live.length],['FUTEBOL','⚽','Futebol',futSportCh().length],['FILMES','🎬','Filmes',S.mov.length],['SÉRIES','🍿','Séries',S.ser.length],['INFANTIL','🧸','Infantil',cnt('INFANTIL')],['ANIMES','🎌','Animes',cnt('ANIMES')],['FAVORITOS','⭐','Favoritos',S.fav.size]].map(x=>`<button class="st2" data-tab="${x[0]}"><span class="ico">${x[1]}</span><span><b>${x[2]}</b><small>${x[3].toLocaleString('pt-BR')}</small></span></button>`).join('')+'</div>'}
  const cw=continueList(),kcw=k=>cw.filter(x=>x.e.kind==k).map(x=>pcard(x.e,x.c,1));
  const dest=(arr,tt)=>{const a=arr.filter(i=>i.e);return row(tt,a.map(i=>pcard(i.e)),a.length+' de '+arr.length+' estão na sua lista')};
  const nohl=!HL.movies.length&&!HL.series.length;
  if(t=='RECOMENDAÇÕES'){h+=row('Continuar assistindo',cw.map(x=>pcard(x.e,x.c,1)),cw.length+' em andamento');
    h+=nohl?`<div class="hint">${HL.err?'Não consegui buscar os destaques da internet ('+esc(HL.err)+'). ':'Buscando destaques da internet… '}Informe uma chave TMDB em “Carregar M3U” para resultados melhores.</div>`:dest(HL.movies,'Filmes em destaque')+dest(HL.series,'Séries em destaque');
    const seeds=[...cw.map(x=>x.e),...[...S.fav].map(k=>S.map.get(k)).filter(Boolean)].slice(0,12),gs=new Set(seeds.filter(e=>e.kind!='l').map(e=>e.group)),seen=new Set(seeds.map(e=>e.key));
    const net=HL.tips.filter(i=>i.e&&!seen.has(i.e.key)).map(i=>i.e);net.forEach(e=>seen.add(e.key));const sim=[];for(const e of S.mov.concat(S.ser)){if(sim.length>=24)break;if(gs.has(e.group)&&!seen.has(e.key)&&!lastWatched(e))sim.push(e)}
    const tips=net.concat(sim).slice(0,30);h+=row('Dicas para você',tips.map(pcard),net.length?'mais bem avaliados + parecidos com o que você vê':'parecidos com o que você assiste');
    const fv=[...S.fav].map(k=>S.map.get(k)).filter(Boolean);h+=row('Seus favoritos',fv.map(card),fv.length+' itens');
    const lv=[...S.live.filter(e=>S.fav.has(e.key)),...S.live].slice(0,14);h+=row('TV ao vivo',lv.map(wcard),'canais em destaque','l|')}
  else if(t=='Vivo'){const fv=S.live.filter(e=>S.fav.has(e.key));h+=row('★ Canais favoritos',fv.slice(0,24).map(wcard),fv.length+' canais','l|');h+=groupRows(S.live,40)}
  else if(t=='FILMES'){h+=row('Continuar assistindo',kcw('m'),'');h+=dest(HL.movies,'Em destaque agora');h+=groupRows(S.mov)}
  else if(t=='SÉRIES'){h+=row('Continuar assistindo',kcw('s'),'');h+=dest(HL.series,'Em destaque agora');h+=groupRows(S.ser)}
  else if(t=='FUTEBOL')h+=futPage();
  else if(t=='FAVORITOS'){const f=S.every.filter(e=>S.fav.has(e.key));if(!f.length)return'<div class="empty">Você ainda não tem favoritos.<br>Toque na ★ de qualquer canal, filme ou série.</div>';
    for(const[k,n,w]of[['l','Canais',1],['m','Filmes',0],['s','Séries',0]]){const a=f.filter(e=>e.kind==k);h+=row(n,a.map(w?wcard:pcard),a.length+' itens',k+'|')}}
  else{const f=S.every.filter(e=>e.th==t);for(const[k,n]of[['l','Canais'],['m','Filmes'],['s','Séries']]){const a=f.filter(e=>e.kind==k);h+=row(n,a.slice(0,30).map(card),a.length.toLocaleString('pt-BR')+' itens',k+'|')}
    if(!f.length)h+=`<div class="empty">Nada encontrado para ${t.toLowerCase()} na sua lista.<br>O app procura pelo nome da categoria (ex.: “Infantil”, “Anime”).</div>`}
  return h}
function sections(L){const g={l:[],m:[],s:[]};L.forEach(e=>g[e.kind].push(e));let h='';
  for(const[t,k]of[['Canais ao vivo','l'],['Filmes','m'],['Séries','s']]){const a=g[k];if(!a.length)continue;const n=S.lim[k];
    h+=`<div class="sh"><h3>${t}</h3><span>${a.length.toLocaleString('pt-BR')} ${k=='l'?'canais':'títulos'}</span></div><div class="grid${k=='l'?' cg':''}">${a.slice(0,n).map(card).join('')}</div>`;
    if(a.length>n)h+=`<button class="more" data-more="${k}">Mostrar mais (${(a.length-n).toLocaleString('pt-BR')})</button>`}
  return h||'<div class="empty">Nenhum conteúdo encontrado.</div>'}
const catMode=()=>['Vivo','FILMES','SÉRIES'].includes(S.tab)&&!S.q.trim()&&S.view=='browse';
function renderCatPanel(gl){const P0=$('#catp');if(!catMode()||!S.all.length){P0.hidden=true;$('#catfab').hidden=true;return}P0.hidden=false;$('#catfab').hidden=false;
  const m=new Map();gl.forEach(e=>m.set(e.group,(m.get(e.group)||0)+1));const gs=[...m].sort((a,b)=>a[0].localeCompare(b[0],'pt')),L=$('#cpl'),st=L.scrollTop;
  $('#cph').innerHTML='<h3>'+(S.tab=='Vivo'?'TV ao vivo':S.tab=='FILMES'?'Filmes':'Séries')+' › Categorias</h3>';
  L.innerHTML=`<button class="${S.group?'':'on'}" data-g=""><span>Todas</span><em>${gl.length.toLocaleString('pt-BR')}</em></button>`+gs.map(g=>`<button class="${S.group==g[0]?'on':''}" data-g="${esc(g[0])}"><span>${esc(g[0])}</span><em>${g[1].toLocaleString('pt-BR')}</em></button>`).join('');L.scrollTop=st}
function renderBrowse(){clearInterval(S.heroT);const B=$('#bmain'),st=B.scrollTop,q=S.q.trim(),rows=!q&&!S.fav1&&!S.group&&S.type=='all';
  const{L,gl}=view();S.lastList=rows?[]:L.filter(e=>e.kind!='l');S.hero={items:[],i:0};let h=rows&&S.tab=='FUTEBOL'?'':filterBar(gl,rows,L.length);
  if(!S.all.length)h+='<div class="empty">Nenhuma lista carregada.<br><br><button class="more" id="e1">Carregar M3U</button></div>';else if(rows)h+=pageRows();else h+=sections(L);
  B.innerHTML=h;B.scrollTop=st;drawHero();
  if(S.hero.items.length>1&&!LITE())S.heroT=setInterval(()=>{if(S.view!='browse'||!$('#hero'))return;S.hero.i=(S.hero.i+1)%S.hero.items.length;drawHero()},9000);
  const s=$('#sort');if(s)s.onchange=()=>{S.sort=s.value;renderBrowse()};const e1=$('#e1');if(e1)e1.onclick=openModal;
  if(IO)B.querySelectorAll('[data-need]').forEach(el=>IO.observe(el));renderCatPanel(gl)}

/* detalhes */
function renderDetail(){const e=S.det,D=$('#pg-detail');if(!e)return;ensurePT(e,()=>{if(S.view=='detail'&&S.det==e)renderDetail()});const inf=INFO[e.key]||{},poster=inf.img||e.logo||'',bg=inf.bn||poster,rt=e.r||inf.r,w=lastWatched(e),c=startEp(e),pr=eprog(e);
  const sim=(e.kind=='m'?S.mov:S.ser).filter(o=>o.group==e.group&&o.key!=e.key).slice(0,24);
  let btn=e.kind=='s'?(w?`▶ Continuar T${c.s} E${c.e}`:`▶ Assistir T${c.s} E${c.e}`):(pr?'▶ Continuar de '+fmt(P[e.ch[0].url].t):'▶ Assistir');
  let h=`<div class="dh"><div class="hbg" style="${bg?`background-image:url('${esc(bg)}');filter:${inf.bn?'none':'blur(18px) brightness(.55)'};transform:scale(1.15)`:bgOf(e.title)}"></div><div class="hsh"></div>
   <button class="bk2" data-back="1">← Voltar</button>
   <div class="dc"><div class="dp" style="${bgOf(e.title)}">${ini(e)}${imgTag(poster)}</div><div class="dt"><div class="hm"><span class="tag">${e.kind=='s'?'SÉRIE':'FILME'}</span>${inf.y?`<span>${esc(inf.y)}</span>`:''}${rt?`<span class="rt2">★ ${rt}</span>`:''}${inf.g?`<span>${esc(genPT(inf.g))}</span>`:''}<span>${esc(e.group)}</span>${e.kind=='s'?`<span>${e.ns} temporada${e.ns>1?'s':''} • ${e.ch.length} ep.</span>`:''}</div>
   <h1>${esc(e.title)}</h1><p>${esc(inf.ov||(INFO[e.key]?'Sinopse não encontrada nas bases consultadas.':'Buscando sinopse…'))}</p>
   <div class="hb"><button class="pk big" data-play="${esc(e.key)}">${btn}</button><button class="ib2${S.fav.has(e.key)?' on':''}" data-f="${esc(e.key)}">★ Minha lista</button></div></div></div></div>`;
  if(e.kind=='s'){const seas=[...new Set(e.ch.map(x=>x.s))].sort((a,b)=>a-b);if(!seas.includes(S.season))S.season=seas[0];
    h+=`<div class="sec"><h3>Episódios</h3>${seas.map(s=>`<button class="ch2${s==S.season?' on':''}" data-s="${s}">Temporada ${s}</button>`).join('')}</div>`;
    h+=e.ch.filter(x=>x.s==S.season).map(x=>{const p=P[x.url],pc=pct(x);return`<div class="ep${S.cur&&S.cur.c.url==x.url?' cur':''}" data-ep="${esc(x.url)}" tabindex="0"><span class="n">${x.e}</span><b>${esc(epName(x))}</b>${p&&p.done?'<span class="ok">✓ assistido</span>':pc?`<small>${Math.round(pc)}%</small>`:''}<span class="chip">▶</span>${pc?`<div class="pb"><i style="width:${pc}%"></i></div>`:''}</div>`}).join('')}
  h+=row('Você também pode gostar',sim.map(pcard),'em '+esc(e.group));D.innerHTML=h+'<div style="height:30px"></div>';
  if(IO)D.querySelectorAll('[data-need]').forEach(el=>IO.observe(el));
  if(!INFO[e.key])getInfo(e).then(()=>{if(S.view=='detail'&&S.det===e)renderDetail()})}

/* player + painel direito */
const livePanel=()=>{const q=norm(S.pq);return S.live.filter(e=>(!S.pg2||e.group==S.pg2)&&(!q||norm(e.title).includes(q)))};
const metaH=(e,inf)=>`<div class="mm"><span class="chip">${e.kind=='s'?'SÉRIE':'FILME'}</span>${inf.y?`<span class="chip">${esc(inf.y)}</span>`:''}${(e.r||inf.r)?`<span class="chip rt2">★ ${e.r||inf.r}</span>`:''}<span class="chip">${esc(e.group)}</span></div>`;
function panelHead(){const x=S.cur;if(!x)return;const e=x.e,inf=INFO[e.key]||{};ensurePT(e,()=>{if(S.cur&&S.cur.e==e)panelHead()});S.pkey=e.key;
  if(e.kind=='l'){const gs=[...new Set(S.live.map(o=>o.group))].sort((a,b)=>a.localeCompare(b,'pt'));
    $('#rx').innerHTML=`<div class="r1" style="display:flex;justify-content:space-between;align-items:center"><h3>TV ao vivo</h3><span class="chip" id="rc"></span></div><input class="in" id="pq" placeholder="Buscar canal…" value="${esc(S.pq)}"><select class="in" id="pg2"><option value="">Todas as categorias</option>${gs.map(g=>`<option${g==S.pg2?' selected':''}>${esc(g)}</option>`).join('')}</select>`}
  else{const seas=e.kind=='s'?[...new Set(e.ch.map(c=>c.s))].sort((a,b)=>a-b):[];
    $('#rx').innerHTML=`<div class="ph2"><div class="th" style="${bgOf(e.title)}">${ini(e)}${imgTag(inf.img||e.logo)}</div><div class="x"><h3>${esc(e.title)}</h3>${metaH(e,inf)}</div></div><div class="syn">${esc(inf.ov||'')}</div>${seas.length?`<div class="ss">${seas.map(s=>`<button class="ch2${s==S.season?' on':''}" data-s="${s}">Temp.${s}</button>`).join('')}</div>`:''}`;
    if(!INFO[e.key])getInfo(e).then(()=>{if(S.view=='player'&&S.pkey==e.key)panelHead()})}}
function panelList(){const x=S.cur;if(!x)return;const e=x.e,L=$('#rl');
  if(e.kind=='l'){const a=livePanel();$('#rc').textContent=a.length.toLocaleString('pt-BR');
    L.innerHTML=a.length?a.slice(0,400).map((o,i)=>`<div class="rw lv${o.key==e.key?' cur':''}" data-k="${esc(o.key)}" data-u="${esc(o.ch[0].url)}" tabindex="0"><span class="n0">${String(i+1).padStart(2,'0')}</span><div class="th"><span>${ini(o)}</span>${imgTag(o.logo)}</div><div class="tx"><b>${esc(o.title)}</b><small>${esc(o.group)}</small></div>${star(o)}</div>`).join(''):'<div class="empty">Nenhum canal encontrado</div>';
    $('#rf').textContent=a.length>400?'Mostrando 400 de '+a.length+' — refine a busca':'Use PgUp/PgDn para trocar de canal'}
  else if(e.kind=='s'){L.innerHTML='<div class="rl0">Episódios</div>'+e.ch.filter(c=>c.s==S.season).map(c=>{const p=P[c.url],pc=pct(c);return`<div class="rw ee${x.c.url==c.url?' cur':''}" data-ep="${esc(c.url)}" data-u="${esc(c.url)}" tabindex="0"><span class="n0">${c.e}</span><div class="tx"><b>${esc(epName(c))}</b><small>Temp.${c.s} • Ep.${c.e}</small></div>${p&&p.done?'<span class="ok">✓</span>':''}${pc?`<div class="pb"><i style="width:${pc}%"></i></div>`:''}</div>`}).join('');$('#rf').textContent='Os episódios seguem automaticamente'}
  else{const sim=S.mov.filter(o=>o.group==e.group&&o.key!=e.key).slice(0,20);
    L.innerHTML='<div class="rl0">Você também pode gostar</div>'+sim.map(o=>`<div class="rw" data-k="${esc(o.key)}" data-u="${esc(o.ch[0].url)}" tabindex="0"><div class="th" style="${bgOf(o.title)}"><span>${ini(o)}</span>${imgTag(o.logo)}</div><div class="tx"><b>${esc(o.title)}</b><small>${esc(o.group)}</small></div>${star(o)}</div>`).join('');$('#rf').textContent='Ao terminar, o próximo filme da categoria começa'}}
function updBar(){const x=S.cur,e=x&&x.e,c=x&&x.c;const t=x?(e.kind=='s'?e.title+' • T'+c.s+' E'+c.e:e.title):'—';
  $('#bt').textContent=t;$('#pt2').textContent=t;$('#bg').hidden=!x;$('#bg').textContent=x?e.group:'';$('#bf').hidden=!x;$('#bf').classList.toggle('on',!!x&&S.fav.has(e.key));
  $('#lvb').hidden=!(x&&e.kind=='l');$('#tags').innerHTML=x&&e.kind=='l'?'<span class="tg">• LIVE</span>':''}

/* navegação entre páginas */
function setView(vw){S.view=vw;if(vw!='browse'){$('#catp').hidden=true;$('#catfab').hidden=true}$('#pg-browse').hidden=vw!='browse';$('#pg-detail').hidden=vw!='detail';$('#pg-player').hidden=vw!='player';clearInterval(S.heroT);navOn()}
function navOn(){document.querySelectorAll('#nav button').forEach(b=>b.classList.toggle('on',b.dataset.tab==S.tab&&S.view=='browse'));const s=$('#navs');if(s)s.value=S.tab}
function renderNav(){$('#nav').innerHTML=TABS.map(t=>`<button data-tab="${t}">${lab(t)}</button>`).join('');$('#navs').innerHTML=TABS.map(t=>`<option value="${t}">${lab(t)}</option>`).join('');navOn()}
const snap=()=>({view:S.view,tab:S.tab,q:S.q,group:S.group,type:S.type,fav1:S.fav1,det:S.det,st:$('#bmain').scrollTop});
function pushState(){S.stack.push(snap());if(S.stack.length>40)S.stack.shift()}
function leavePlayer(){if(document.fullscreenElement)document.exitFullscreen();saveProg(true);stop();S.cur=null;S.pkey='';updBar()}
function restore(st){S.tab=st.tab;S.q=st.q;$('#q').value=st.q;S.type=st.type;S.group=st.group;S.fav1=st.fav1;S.det=st.det;setView(st.view);
  if(st.view=='browse'){renderBrowse();$('#bmain').scrollTop=st.st||0}else if(st.view=='detail')renderDetail()}
function go(tab){if(S.view=='player')leavePlayer();else if(S.view!='browse'||tab!=S.tab)pushState();
  S.tab=tab;LS.ntTab=tab;S.group='';S.type='all';S.fav1=false;S.lim=LIM0();S.q='';S.bq='';S.bg='';S.bsKey='';$('#q').value='';setView('browse');renderBrowse();$('#bmain').scrollTop=0}
Object.defineProperty(window,'ntCanBack',{get:()=>S.stack.length>0||!!S.group||!!S.q});
function back(){if(S.view=='player')leavePlayer();const st=S.stack.pop();if(st)restore(st);else if(S.q){S.q='';$('#q').value='';setView('browse');renderBrowse()}else if(S.view!='browse'){setView('browse');renderBrowse()}else if(S.group){S.group='';renderBrowse()}}
function openDetail(e){if(S.view=='player')leavePlayer();else pushState();S.det=e;if(e.kind=='s'){const w=lastWatched(e);S.season=(w?w.c:e.ch[0]).s}setView('detail');renderDetail();$('#pg-detail').scrollTop=0}
const qlistFor=e=>e.kind=='l'?livePanel():(e.kind=='m'?S.mov:S.ser).filter(o=>o.group==e.group);
function openPlayer(e,c){if(S.view=='player')leavePlayer();else pushState();
  if(e.kind=='l'){S.pg2=e.group;S.pq=''}S.cur={e,c:c||startEp(e)};if(e.kind=='s')S.season=S.cur.c.s;S.qlist=qlistFor(e);setView('player');updBar();panelHead();panelList();play(S.cur.c)}
function switchTo(e,c){const fresh=e.key!=S.pkey||(e.kind=='s'&&c.s!=S.season);S.cur={e,c};if(e.kind=='s')S.season=c.s;if(e.key!=S.pkey)S.qlist=qlistFor(e);
  if(fresh)panelHead();panelList();play(c)}
function step(d){const n=d>0?nextOf():prevOf();if(n)switchTo(n.e,n.c)}
function renderAll(){renderNav();if(S.view=='browse')renderBrowse();updBar();const n=S.all.length;
  status(n?`✓ ${S.live.length.toLocaleString('pt-BR')} canais • ${S.mov.length.toLocaleString('pt-BR')} filmes • ${S.ser.length.toLocaleString('pt-BR')} séries`:'Nenhuma lista carregada');$('.st').title='Listas ativas: '+(S.pl||'—')}

/* ---------- listas salvas (várias, usadas juntas) ---------- */
const DB=new Promise((ok,no)=>{const r=indexedDB.open('ntplay',1);r.onupgradeneeded=()=>r.result.createObjectStore('lists',{keyPath:'id'});r.onsuccess=()=>ok(r.result);r.onerror=()=>no(r.error)});
const tx=(m,f)=>DB.then(db=>new Promise((ok,no)=>{const t=db.transaction('lists',m),r=f(t.objectStore('lists'));t.oncomplete=()=>ok(r.result);t.onerror=()=>no(t.error)}));
const dbAll=()=>tx('readonly',s=>s.getAll()),dbPut=o=>tx('readwrite',s=>s.put(o)),dbDel=id=>tx('readwrite',s=>s.delete(id)),dbGet=id=>tx('readonly',s=>s.get(id));
const hostOf=u=>{try{return new URL(u).host}catch(e){return u}};
const getActive=()=>{try{return JSON.parse(LS.ntActive||'[]')}catch(e){return[]}},setActive=a=>{LS.ntActive=JSON.stringify(a)};
function setList(ch,name){if(!ch.length){toast('Nenhum canal encontrado. Verifique se é um M3U válido.','',null,'er');status('Falha ao ler a lista');return false}
  S.all=ch;S.pl=name||'';build();S.stack=[];renderAll();return true}
async function applyLists(){const all=((await dbAll().catch(()=>[]))||[]).sort((a,b)=>b.date-a.date);let act=getActive().filter(id=>all.some(o=>o.id==id));
  if(!act.length&&LS.bobList&&all.some(o=>o.id==LS.bobList))act=[LS.bobList];if(!act.length&&all.length)act=[all[0].id];setActive(act);
  const on=all.filter(o=>act.includes(o.id)),ch=[],seen=new Set();
  for(const o of on)for(const c of parseM3U(o.text)){if(seen.has(c.url))continue;seen.add(c.url);c.src=o.name;ch.push(c)}
  if(!on.length){S.all=[];S.pl='';build();S.stack=[];renderAll()}else setList(ch,on.map(o=>o.name).join(' + '));renderPls()}
async function addList(t,name,url){const n=parseM3U(t).length;if(!n){toast('Nenhum canal encontrado nessa lista. Verifique se é um M3U válido.','',null,'er');return false}
  const id=url||'file:'+name,all=(await dbAll().catch(()=>[]))||[],old=all.find(o=>o.id==id);let nm=(old&&old.name)||name;
  if(!old&&all.some(o=>o.name==nm)){let i=2;while(all.some(o=>o.name==nm+' ('+i+')'))i++;nm+=' ('+i+')'}
  await dbPut({id,name:nm,url:url||'',text:t,date:Date.now(),count:n});const a=getActive();if(!a.includes(id))a.push(id);setActive(a);await applyLists();return true}
const loadText=(t,name,url)=>addList(t,name,url);
/* selo de vencimento (contas Xtream): verde > 7 dias, amarelo ≤ 7, vermelho vencida */
let ACCT={};try{ACCT=JSON.parse(LS.ntAcct||'{}')}catch(e){}
async function listAcct(o,force){const c=o.url&&credsOf(o.url);if(!c)return null;const k=o.id,old=ACCT[k];
  if(!force&&old&&Date.now()-old.ts<6*36e5)return old;const a=await account(c,true);if(a){ACCT[k]=a;try{LS.ntAcct=JSON.stringify(ACCT)}catch(e){}}return a||old||null}
function badge(a){if(!a)return'';const d=a.exp?Math.ceil((a.exp-Date.now())/864e5):null,dt=a.exp?new Date(a.exp).toLocaleDateString('pt-BR'):'';
  const tl=a.max?`<span class="bdg n" title="Telas em uso / permitidas">📺 ${a.act}/${a.max} tela${a.max>1?'s':''}</span>`:'';
  if(a.status&&!/active/i.test(a.status)&&!(d!=null&&d<0))return`<span class="bdg r">⛔ ${esc(a.status.toUpperCase())}</span>`+tl;
  if(d==null)return`<span class="bdg g">✅ sem vencimento</span>`+tl;if(d<0)return`<span class="bdg r">⛔ VENCIDA</span>`+tl;
  return`<span class="bdg ${d>7?'g':'y'}">${d>7?'✅':'⚠'} vence em ${dt} (${d}d)</span>`+tl}
async function renderPls(){const L=$('#pls'),all=((await dbAll().catch(()=>[]))||[]).sort((a,b)=>b.date-a.date),act=getActive();
  L.innerHTML=all.length?'':'<p class="small">Nenhuma lista salva. Adicione uma abaixo.</p>';
  for(const o of all){const d=document.createElement('div');d.className='pl'+(act.includes(o.id)?' cur':'');
    d.innerHTML='<div class="pr1"><label class="ck"><input type="checkbox"> Usar</label><div class="n"><input class="pln" type="text" title="Clique para renomear"></div>'+(o.url?'<button class="bt" data-a="r" title="Atualizar pela internet e checar o vencimento">↻</button>':'')+'<button class="bt" data-a="d" title="Excluir">🗑</button></div><div class="pr2"><small></small><span class="bdgs"></span></div>';
    const cb=d.querySelector('input[type=checkbox]');cb.checked=act.includes(o.id);d.querySelector('.pln').value=o.name;d.querySelector('small').textContent=o.count.toLocaleString('pt-BR')+' itens • '+new Date(o.date).toLocaleDateString('pt-BR');
    const bx=d.querySelector('.bdgs'),show=a=>{bx.innerHTML=badge(a)};if(ACCT[o.id])show(ACCT[o.id]);if(o.url&&credsOf(o.url))listAcct(o).then(show);
    cb.onchange=async()=>{let a=getActive();a=cb.checked?[...new Set([...a,o.id])]:a.filter(x=>x!=o.id);if(!a.length){cb.checked=true;toast('Mantenha pelo menos uma lista ativa.');return}setActive(a);await applyLists()};
    d.querySelector('.pln').onchange=async e=>{o.name=e.target.value.trim()||o.name;await dbPut(o);applyLists()};
    const r=d.querySelector('[data-a=r]');if(r)r.onclick=async()=>{r.textContent='…';listAcct(o,true).then(show);await loadUrl(o.url,o.name);renderPls()};
    d.querySelector('[data-a=d]').onclick=async()=>{await dbDel(o.id);setActive(getActive().filter(x=>x!=o.id));await applyLists()};L.append(d)}
  if(all.some(o=>o.url&&credsOf(o.url)))L.insertAdjacentHTML('beforeend','<p class="small pln2">O selo é recalculado sozinho: verde &gt; 7 dias, amarelo ≤ 7 dias, vermelho vencida. Clique no ↻ da lista para forçar a checagem. Listas que não são Xtream (sem usuário/senha no link) não mostram selo.</p>')}
async function fetchList(u){const urls=window.bobNative?[u]:['https://api.allorigins.win/raw?url='+encodeURIComponent(u),'https://corsproxy.io/?'+encodeURIComponent(u),u.replace('http://','https://'),u];let last;
  for(let i=0;i<urls.length;i++){try{status('Baixando lista… ('+(i+1)+'/'+urls.length+')');const r=await fetch(urls[i],{cache:'no-store'});if(!r.ok)throw Error('HTTP '+r.status);const t=await r.text();if(!t.includes('#EXTINF'))throw Error('Não é um M3U');return t}catch(e){last=e}}throw last}
async function loadUrl(u,name,quiet){u=(u||'').trim();if(!u)return false;if(!quiet)status('Baixando lista…');
  try{const t=await fetchList(u);return await addList(t,name||hostOf(u),u)}catch(e){if(!quiet){toast('Falha ao carregar a lista: '+(e.message||e)+'.','Gerenciar listas',openModal,'er');status('Erro ao carregar lista')}return false}}
async function refreshStale(){const all=(await dbAll().catch(()=>[]))||[];let ch=false;
  for(const o of all){if(!o.url||!getActive().includes(o.id)||Date.now()-o.date<12*36e5)continue;
    try{const t=await fetchList(o.url),n=parseM3U(t).length;if(n){await dbPut({...o,text:t,date:Date.now(),count:n});ch=true}}catch(e){}}
  if(ch)applyLists();else renderAll()}
/* ---------- enviar lista pelo celular (QR + link) ---------- */
const b64u=a=>btoa(String.fromCharCode(...a)).replace(/\+/g,'-').replace(/\//g,'_').replace(/=+$/,'');
const unb64u=u=>{u=u.replace(/-/g,'+').replace(/_/g,'/');while(u.length%4)u+='=';return Uint8Array.from(atob(u),c=>c.charCodeAt(0))};
function relay(){let r;try{r=JSON.parse(LS.ntRelay||'null')}catch(e){}
  if(!r||!r.t||!r.k){const rnd=n=>crypto.getRandomValues(new Uint8Array(n));r={t:'ntplay-'+[...rnd(12)].map(x=>x.toString(16).padStart(2,'0')).join(''),k:b64u(rnd(16))};LS.ntRelay=JSON.stringify(r)}return r}
function sendBase(){let b=(LS.ntSendBase||window.NT_SEND_BASE||'').trim();
  if(!b&&/^https?:$/.test(location.protocol)&&!/^(localhost|127\.|192\.168\.|10\.)/.test(location.hostname))b=location.href.replace(/[#?].*$/,'').replace(/[^/]*$/,'');
  if(b&&!/\.html?$/i.test(b))b=b.replace(/\/?$/,'/')+'enviar.html';return b}
function sendLink(){const b=sendBase(),r=relay();return b?b+'#t='+r.t+'&k='+r.k:'https://ntfy.sh/'+r.t}
function renderSend(){const l=sendLink(),box=$('#qrbox');$('#sbase').value=LS.ntSendBase||window.NT_SEND_BASE||'';
  if(!l||!window.qrcode){box.innerHTML='<p class="small">Não foi possível gerar o QR code.</p>';$('#slink').value=l||'';return}
  const q=qrcode(0,'M');q.addData(l);q.make();box.innerHTML='<div class="qr">'+q.createSvgTag({cellSize:4,margin:2,scalable:true})+'</div>'+(sendBase()?'':'<p class="small">Modo simples: no site que abrir no celular, use a opção de <b>enviar/publicar mensagem</b> e cole o link da lista M3U. (Para a página própria do NTPlay, informe o endereço da <b>enviar.html</b> abaixo.)</p>');$('#slink').value=l}
let sse=null,seen=new Set();
function startRelay(){stopRelay();if(!window.EventSource)return;const r=relay();
  try{sse=new EventSource('https://ntfy.sh/'+r.t+'/sse');sse.onmessage=async ev=>{try{const m=JSON.parse(ev.data);if(m.event&&m.event!='message'||!m.message||seen.has(m.id))return;seen.add(m.id);
    let o=null;const raw=m.message.trim();
    try{const all=unb64u(raw),key=await crypto.subtle.importKey('raw',unb64u(r.k),'AES-GCM',false,['decrypt']),
      pt=await crypto.subtle.decrypt({name:'AES-GCM',iv:all.slice(0,12)},key,all.slice(12));o=JSON.parse(new TextDecoder().decode(pt))}catch(x){}
    if(!o){const u=raw.match(/https?:\/\/\S+/);if(/#EXTM3U|#EXTINF/.test(raw))o={t:raw};else if(u)o={u:u[0]};else throw Error('x')}
    toast('Lista recebida do celular!');
    if(o.u){await loadUrl(o.u,o.n||undefined)}else if(o.t){await loadText(o.t,o.n||'Lista do celular')}
    closeModal()}catch(e){toast('Não foi possível ler a lista enviada.','','','er')}}}catch(e){}}
function stopRelay(){if(sse){try{sse.close()}catch(e){}sse=null}}
function openModal(){renderSend();startRelay();$('#tkey').value=LS.tmdbKey||'';$('#url').value='';$('#nname').value='';renderPls();$('#modal').hidden=false}
const closeModal=()=>{$('#modal').hidden=true;stopRelay()};

/* ---------- qualidade / 4K / tela cheia ---------- */
function fillQ(h){const s=$('#qsel');if(!s||!h.levels||h.levels.length<2)return;const hs=[...new Set(h.levels.map(l=>l.height).filter(Boolean))].sort((a,b)=>b-a);if(hs.length<2)return;
  s.innerHTML='<option value="-1">Qualidade: Auto</option>'+hs.map(x=>`<option value="${x}">${x>=2000?'4K ('+x+'p)':x+'p'}</option>`).join('');s.hidden=false;
  s.onchange=()=>{const x=+s.value;if(x<0){h.currentLevel=-1;return}let best=-1,bb=-1;h.levels.forEach((l,i)=>{if(l.height==x&&(l.bitrate||0)>bb){bb=l.bitrate||0;best=i}});h.currentLevel=best}}
const resTag=()=>{const w=v.videoWidth,hh=v.videoHeight,t=$('#rsb');if(!hh){t.hidden=true;return}t.hidden=false;t.textContent=(hh>=2000?'4K ':hh>=1400?'2K ':hh>=1000?'FHD ':hh>=700?'HD ':'SD ')+w+'×'+hh};
['loadedmetadata','resize','emptied'].forEach(n=>v.addEventListener(n,resTag));
let lastTot=0,lastDrop=0,heavy=0;
setInterval(()=>{if(S.view!='player'||v.paused||!v.getVideoPlaybackQuality)return;const q=v.getVideoPlaybackQuality(),dt=q.totalVideoFrames-lastTot,dd=q.droppedVideoFrames-lastDrop;lastTot=q.totalVideoFrames;lastDrop=q.droppedVideoFrames;
  if(dt>100&&dd/dt>.2)heavy++;else heavy=0;if(heavy>=2&&window.bobNative){heavy=0;toast('O player interno está perdendo quadros (comum em 4K/H.265).',window.NT_FF&&!FFM?'Converter no FFmpeg':'Outros players',()=>window.NT_FF&&!FFM&&S.cur?play(S.cur.c,true,{eng:'ffenc',ss:S.cur.c.cat=='live'?0:curT()}):$('#vlc').click())}},5000);
const vidEl=$('#vid');let idleT;vidEl.addEventListener('mousemove',()=>{vidEl.classList.remove('idle');clearTimeout(idleT);if(document.fullscreenElement)idleT=setTimeout(()=>vidEl.classList.add('idle'),2800)});
document.addEventListener('fullscreenchange',()=>{clearTimeout(idleT);vidEl.classList.remove('idle');if(document.fullscreenElement)idleT=setTimeout(()=>vidEl.classList.add('idle'),2800)});
v.addEventListener('dblclick',()=>$('#fs').click());
/* ---------- eventos ---------- */
document.querySelectorAll('[data-ic]').forEach(el=>el.innerHTML=ic(el.dataset.ic));
$('#nav').onclick=e=>{const b=e.target.closest('[data-tab]');if(b)go(b.dataset.tab)};$('#navs').onchange=e=>go(e.target.value);
function openSearch(){$('#sdr').hidden=false;$('#q').focus();$('#q').select()}
function closeSearch(clear){$('#sdr').hidden=true;if(clear&&S.q){S.q='';$('#q').value='';S.group='';if(S.view=='browse')renderBrowse()}}
$('#hsearch').onclick=()=>$('#sdr').hidden?openSearch():closeSearch(false);$('#sdx').onclick=()=>closeSearch(true);
let qt;$('#q').oninput=e=>{clearTimeout(qt);qt=setTimeout(()=>{S.q=e.target.value;S.group='';S.lim=LIM0();if(S.view!='browse'){if(S.view=='player')leavePlayer();else pushState();setView('browse')}renderBrowse()},250)};
$('#hload').onclick=openModal;$('#mx').onclick=closeModal;$('#modal').onclick=e=>{if(e.target.id=='modal')closeModal()};
$('#mload').onclick=async()=>{const u=$('#url').value.trim(),n=$('#nname').value.trim();LS.tmdbKey=$('#tkey').value.trim();if(!u){toast('Cole o link da lista M3U.');return}
  const ok=await loadUrl(u,n||undefined);if(ok){$('#url').value='';$('#nname').value='';toast('Lista adicionada e ativada. Marque/desmarque “Usar” para combinar listas.')}};
$('#sbase').onchange=e=>{LS.ntSendBase=e.target.value.trim();renderSend();startRelay()};
$('#scopy').onclick=()=>{const l=$('#slink').value;if(!l)return;(navigator.clipboard?navigator.clipboard.writeText(l):Promise.reject()).then(()=>toast('Link copiado.')).catch(()=>{$('#slink').select();document.execCommand('copy');toast('Link copiado.')})};
$('#tkey').onchange=()=>{LS.tmdbKey=$('#tkey').value.trim()};
$('#mfix').onclick=()=>{$('#url').value=window.NT_DEFAULT_M3U||'';$('#nname').value=window.NT_DEFAULT_NAME||''};
$('#mfile').onclick=()=>window.bobNative?(closeModal(),bobNative.pickFile()):$('#fi').click();
$('#fi').onchange=async e=>{const f=e.target.files[0];if(f){closeModal();loadText(await f.text(),f.name)}e.target.value=''};
$('#mhl').onclick=()=>{LS.tmdbKey=$('#tkey').value.trim();toast('Buscando destaques…');loadHL(true).then(()=>toast('Destaques atualizados: '+HL.movies.length+' filmes, '+HL.series.length+' séries'))};
$('#bk').onclick=back;$('#pv').onclick=()=>step(-1);$('#nx').onclick=()=>step(1);
$('#fs').onclick=()=>document.fullscreenElement?document.exitFullscreen():$('#vid').requestFullscreen();
$('#bf').onclick=()=>S.cur&&toggleFav(S.cur.e.key);
$('#an').checked=S.autoNext;$('#an').onchange=e=>{S.autoNext=e.target.checked;LS.ntAuto=S.autoNext?'1':'0'};
$('#rx').addEventListener('input',e=>{if(e.target.id=='pq'){S.pq=e.target.value;panelList()}});
$('#rx').addEventListener('change',e=>{if(e.target.id=='pg2'){S.pg2=e.target.value;panelList()}});
if(window.bobNative&&bobNative.proxyPort)bobNative.proxyPort().then(p=>{window.NT_PROXY=p||0}).catch(()=>{});
if(window.bobNative){$('#vlc').hidden=false;$('#vlcl').hidden=false;$('#vlca').checked=LS.bobVlc=='1';$('#vlca').onchange=e=>{LS.bobVlc=e.target.checked?'1':'0'};
  let PLS=[];if(bobNative.players)bobNative.players().then(a=>{PLS=a||[]}).catch(()=>{});
  if(bobNative.ffOk)bobNative.ffOk().then(r=>{window.NT_FF=!!(r&&r.ok);$('#pmodew').hidden=!window.NT_FF;$('#pmode').value=LS.ntPMode||'auto';
    $('#pmodei').textContent=window.NT_FF?'FFmpeg pronto • vídeo convertido com '+({h264_nvenc:'placa NVIDIA',h264_qsv:'Intel Quick Sync',h264_amf:'placa AMD',libx264:'processador (até 1080p)'}[r.enc]||r.enc):''}).catch(()=>{});
  $('#pmode').onchange=e=>{LS.ntPMode=e.target.value};$('#plff').checked=LS.ntLiveFF!='0';$('#plff').onchange=e=>{LS.ntLiveFF=e.target.checked?'1':'0'};
  // menu "Outros players": players instalados no PC + modos do FFmpeg
  $('#vlc').onclick=ev=>{ev.stopPropagation();if(!S.cur)return;let m=$('#xpm');if(m){m.remove();return}m=document.createElement('div');m.id='xpm';
    const it=[...PLS.map(p=>['x:'+p.id,'▶ '+p.n+(p.id==(LS.ntExt||'vlc')?' ✓':'')]),...(window.NT_FF?[['f:ffcopy','⚙ Modo compatibilidade (FFmpeg, áudio AAC)'],['f:ffenc','⚙ Converter vídeo 4K/H.265 (FFmpeg)']]:[])];
    m.innerHTML=it.length?it.map(x=>`<button data-xp="${x[0]}">${esc(x[1])}</button>`).join(''):'<p class="small">Nenhum player externo encontrado. Instale o VLC, mpv, PotPlayer ou MPC-HC.</p>';
    $('#bar').appendChild(m);m.onclick=e=>{const b=e.target.closest('[data-xp]');if(!b)return;m.remove();const[k,id]=b.dataset.xp.split(':'),c=S.cur.c;
      if(k=='f'){play(c,true,{eng:id,ss:c.cat=='live'?0:curT()});return}
      LS.ntExt=id;saveProg(true);stop();bobNative.openExt(id,c.url,c.cat=='live').then(r=>{if(r&&r.error)toast(r.error,'',null,'er');else ph('Tocando no '+r.name,'Esta janela continua com a lista.',true)})}};
  document.addEventListener('click',e=>{const m=$('#xpm');if(m&&!e.target.closest('#xpm,#vlc'))m.remove()});
  bobNative.onOpen(f=>loadText(f.text,f.name))}
/* barra do modo FFmpeg */
const ffGo=t=>{if(!FFM||FFM.live||!S.cur)return;const d=FFM.dur||1e9;t=Math.max(0,Math.min(d-5,t));play(S.cur.c,true,{eng:FFM.eng,ss:t})};
$('#ffpp').onclick=()=>v.paused?v.play().catch(()=>{}):v.pause();
$('#ffr').oninput=e=>{ffDrag=true;$('#fft1').textContent=fmt(+e.target.value)};$('#ffr').onchange=e=>{ffDrag=false;ffGo(+e.target.value)};
$('#ffseek').onclick=e=>{const b=e.target.closest('[data-ffs]');if(b)ffGo(curT()+(+b.dataset.ffs))};
$('#padult').checked=showAdult();$('#padult').onchange=e=>{LS.ntAdult=e.target.checked?'1':'0';build();S.group='';renderAll()};
$('#plite').checked=LITE();$('#plite').onchange=e=>{LS.ntLite=e.target.checked?'1':'0';document.documentElement.classList.toggle('lite',e.target.checked);S.lim=LIM0();renderBrowse()};
$('#catfab').onclick=()=>$('#catp').classList.toggle('open');$('#catp').addEventListener('click',e=>{if(e.target.closest('[data-g]'))$('#catp').classList.remove('open')});
const byKey=k=>S.map.get(k);
document.addEventListener('click',e=>{const t=e.target;let b;
  if(!$('#sdr').hidden&&!t.closest('#sdr,#hsearch'))closeSearch(false);
  if(b=t.closest('[data-rm]')){e.stopPropagation();e.preventDefault();removeProg(b.dataset.rm);return}
  if(b=t.closest('.st2')){go(b.dataset.tab);return}
  if(b=t.closest('[data-f]')){e.stopPropagation();toggleFav(b.dataset.f);return}
  if(b=t.closest('[data-ar]')){const h=b.parentNode.querySelector('.hs');h.scrollBy({left:+b.dataset.ar*h.clientWidth*.85,behavior:'smooth'});return}
  if(b=t.closest('[data-all]')){const[k,g]=b.dataset.all.split('|');S.fav1=S.tab=='FAVORITOS'&&false;if(k&&isMixed())S.type=k;S.group=g;S.lim=LIM0();renderBrowse();$('#bmain').scrollTop=0;return}
  if(b=t.closest('[data-hi]')){S.hero.i=+b.dataset.hi;drawHero();return}
  if(b=t.closest('[data-play]')){const en=byKey(b.dataset.play);if(en)openPlayer(en,startEp(en));return}
  if(b=t.closest('[data-det]')){const en=byKey(b.dataset.det);if(en)openDetail(en);return}
  if(b=t.closest('[data-back]')){back();return}
  if(b=t.closest('[data-t]')){S.type=b.dataset.t;S.group='';renderBrowse();return}
  if(b=t.closest('[data-fv]')){S.fav1=!S.fav1;S.group='';renderBrowse();return}
  if(b=t.closest('[data-g]')){S.group=b.dataset.g;S.lim=LIM0();renderBrowse();$('#bmain').scrollTop=0;return}
  if(b=t.closest('[data-more]')){S.lim[b.dataset.more]+=LITE()?48:120;renderBrowse();return}
  if(b=t.closest('[data-s]')){S.season=+b.dataset.s;if(S.view=='detail')renderDetail();else{panelHead();panelList()}return}
  if(b=t.closest('[data-ep]')){const en=S.view=='detail'?S.det:S.cur&&S.cur.e;const c=en&&en.ch.find(c=>c.url==b.dataset.ep);if(!c)return;S.view=='detail'?openPlayer(en,c):switchTo(en,c);return}
  if(b=t.closest('[data-k]')){const en=byKey(b.dataset.k);if(!en)return;
    if(b.closest('#rside')){switchTo(en,startEp(en));return}
    const c=b.dataset.c&&en.ch.find(c=>c.url==b.dataset.c);if(c)openPlayer(en,c);else if(en.kind=='l')openPlayer(en,en.ch[0]);else openDetail(en)}});
document.addEventListener('keydown',e=>{const tg=e.target;if((e.ctrlKey||e.metaKey)&&(e.key=='f'||e.key=='F')){e.preventDefault();openSearch();return}
  if(tg.tagName=='INPUT'||tg.tagName=='SELECT'){if(e.key=='Escape'){if(tg.id=='q')closeSearch(false);tg.blur()}else if(e.key=='Enter'&&tg.id=='q')closeSearch(false);return}
  if(!$('#sdr').hidden&&e.key=='Escape'){closeSearch(false);return}
  if((e.key=='Enter'||e.key=='Delete')&&tg.dataset&&tg.dataset.rm){e.preventDefault();tg.click()}
  else if(e.key=='Delete'&&tg.dataset&&tg.dataset.c&&tg.dataset.k){removeProg(tg.dataset.k)}
  else if(e.key=='Enter'&&tg.dataset&&(tg.dataset.k||tg.dataset.ep))tg.click();
  else if(e.key==' '&&FFM&&S.view=='player'){e.preventDefault();$('#ffpp').click()}else if(e.key=='PageDown')step(1);else if(e.key=='PageUp')step(-1);else if(e.key=='f'||e.key=='F')$('#fs').click();else if(e.key=='m'||e.key=='M')v.muted=!v.muted;
  else if((e.key=='Escape'||e.key=='Backspace'||(e.altKey&&e.key=='ArrowLeft'))&&!document.fullscreenElement){e.preventDefault();if(!$('#modal').hidden)closeModal();else back()}});
addEventListener('mouseup',e=>{if(e.button==3){e.preventDefault();back()}});
addEventListener('dragover',e=>e.preventDefault());addEventListener('drop',async e=>{e.preventDefault();const f=e.dataTransfer.files[0];if(f)loadText(await f.text(),f.name)});
const tick=()=>{$('#clock').textContent=new Date().toLocaleTimeString('pt-BR',{hour:'2-digit',minute:'2-digit'})};tick();setInterval(tick,30000);


/* player nativo: progresso, próximo automático, voltar */
if(window.ntNative&&ntNative.ok){const H=ntNative.hooks;
  H.time=t=>{const x=S.cur;if(!x||x.c.cat=='live')return;const o=P[x.c.url]||{};P[x.c.url]={t:Math.floor(t),d:o.d||0,ts:Date.now(),done:false};try{LS.ntProg=JSON.stringify(P)}catch(e){}};
  H.ended=()=>{const x=S.cur;if(!x)return;if(x.c.cat!='live'){P[x.c.url]={t:0,d:0,ts:Date.now(),done:true};try{LS.ntProg=JSON.stringify(P)}catch(e){}}
    if(S.autoNext&&x.c.cat!='live'){const n=nextOf();if(n){toast('Próximo: '+n.e.title);switchTo(n.e,n.c);return}}back()};
  H.exit=t=>{H.time(t);if(S.view=='player')back()};
  H.fail=m=>{toast('Falha no player: '+m,'','','er');if(S.view=='player')back()}}
/* ---------- início ---------- */
setView('browse');renderAll();renderPls();
(async()=>{await applyLists();if(!S.all.length&&window.NT_DEFAULT_M3U)loadUrl(window.NT_DEFAULT_M3U,window.NT_DEFAULT_NAME);else refreshStale()})();
loadHL(false);

/* ---------- diagnóstico (Ctrl+D) ---------- */
const DBG=[];let dbgOn=false,dbgT=0;
function dbgReset(c){DBG.length=0;dbg('canal: '+(c.name||'')+' | tipo do link: '+((c.url.split('?')[0].split('/').pop().match(/\.[a-z0-9]{2,4}$/i)||['sem extensão'])[0]))}
function dbg(s){DBG.push(new Date().toTimeString().slice(0,8)+' '+s);if(DBG.length>60)DBG.shift();if(dbgOn)dbgDraw()}
function dbgDraw(){let el=document.getElementById('dbgp');if(!el){el=document.createElement('pre');el.id='dbgp';el.style.cssText='position:fixed;right:8px;bottom:8px;width:46vw;max-height:60vh;overflow:auto;z-index:99999;background:rgba(0,0,0,.88);color:#9ef;font:11px/1.35 monospace;padding:10px;border:1px solid #2ad;border-radius:8px;white-space:pre-wrap;margin:0';document.body.appendChild(el)}
  el.hidden=!dbgOn;if(!dbgOn)return;const PX=window.NT_PROXY;
  const v2=document.getElementById('v');const st='video: t='+(v2?v2.currentTime.toFixed(1):'-')+' dur='+(v2?(isFinite(v2.duration)?v2.duration.toFixed(1):'inf'):'-')+' pronto='+(v2?v2.readyState:'-')+(v2&&v2.paused?' PAUSADO':'')+(v2&&v2.ended?' FIM':'');
  const head='== NTPlay diagnóstico (Ctrl+D fecha) ==\n'+st+'\n'+DBG.join('\n');
  if(PX)fetch('http://127.0.0.1:'+PX+'/log').then(r=>r.text()).then(t=>{el.textContent=head+'\n\n== repasse ==\n'+t;el.scrollTop=el.scrollHeight}).catch(()=>{el.textContent=head});else el.textContent=head}
addEventListener('keydown',e=>{if(e.ctrlKey&&(e.key=='d'||e.key=='D')){e.preventDefault();dbgOn=!dbgOn;clearInterval(dbgT);if(dbgOn)dbgT=setInterval(dbgDraw,2000);dbgDraw()}});
