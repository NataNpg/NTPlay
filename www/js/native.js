/* Ponte com o player nativo (ExoPlayer no Android/TV, AVPlayer no iOS) via capacitor-video-player */
(function(){
  const C=window.Capacitor,N={ok:false,hooks:{},busy:false,t:0,d:0};window.ntNative=N;
  let plug=null;try{if(C&&C.isNativePlatform&&C.isNativePlatform()){plug=C.Plugins&&C.Plugins.CapacitorVideoPlayer||(C.registerPlugin&&C.registerPlugin('CapacitorVideoPlayer'))}}catch(e){}
  N.native=!!(C&&C.isNativePlatform&&C.isNativePlatform());
  if(!plug)return;
  const ID='ntplayer';let poll=0,seek=0,live=false,seeked=false;
  const on=(n,f)=>{try{plug.addListener(n,f)}catch(e){}};
  const stopPoll=()=>{clearInterval(poll);poll=0};
  on('jeepCapVideoPlayerReady',()=>{seeked=false;if(seek>0){plug.setCurrentTime({playerId:ID,seektime:seek}).catch(()=>{});seek=0}
    stopPoll();poll=setInterval(async()=>{try{const r=await plug.getCurrentTime({playerId:ID}),t=r&&(r.value!=null?r.value:r.result);if(t>0){N.t=t;N.hooks.time&&N.hooks.time(t,0)}}catch(e){}},4000)});
  on('jeepCapVideoPlayerEnded',()=>{stopPoll();N.busy=false;N.hooks.ended&&N.hooks.ended()});
  on('jeepCapVideoPlayerExit',e=>{stopPoll();const was=N.busy;N.busy=false;const t=e&&e.currentTime!=null?+e.currentTime:N.t;N.hooks.exit&&was&&N.hooks.exit(t)});
  N.ok=true;
  N.play=function(c,title,resume,tok){
    if(!/^https?:/i.test(c.url))return false;
    seek=resume||0;live=c.cat=='live';N.busy=true;N.t=0;
    plug.initPlayer({mode:'fullscreen',url:c.url,playerId:ID,componentTag:'div',title:title||'NTPlay',smallTitle:'',accentColor:'#e93a7d',
      exitOnEnd:true,loopOnEnd:false,pipEnabled:false,bkmodeEnabled:false,showControls:true,displayMode:'landscape',
      headers:{'User-Agent':'VLC/3.0.20 LibVLC/3.0.20'}}).then(r=>{if(r&&r.result===false){N.busy=false;N.hooks.fail&&N.hooks.fail(r.message||'erro')}}).catch(e=>{N.busy=false;N.hooks.fail&&N.hooks.fail(String(e&&e.message||e))});
    return true};
  N.stop=function(){if(!N.busy)return;N.busy=false;stopPoll();try{plug.stopAllPlayers()}catch(e){}};
})();
