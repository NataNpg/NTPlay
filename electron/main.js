const { app, BrowserWindow, dialog, ipcMain, Menu, session } = require('electron');
const fs = require('fs'), path = require('path');
const { spawn } = require('child_process');
const UA = 'VLC/3.0.20 LibVLC/3.0.20';
let win;
const http = require('http'), https = require('https');
// ---- repasse local de vídeo: o app busca o canal como "VLC" (sem cabeçalhos de navegador
// que muitos servidores IPTV bloqueiam), segue redirecionamentos e reescreve listas HLS.
let proxyPort = 0;
const prox = (u) => `http://127.0.0.1:${proxyPort}/p?u=${encodeURIComponent(u)}`;
const keep = { http: new http.Agent({ keepAlive: false }), https: new https.Agent({ keepAlive: false, rejectUnauthorized: false }) };
const plog = [];
const mask = (s) => String(s).replace(/(\/(?:live|movie|series|hls)?\/?)([^/?#]{3,})\/([^/?#]{3,})\//i, (a, p) => p + '***/***/').slice(0, 160);
function logp(s) { plog.push(new Date().toISOString().slice(11, 19) + ' ' + s); if (plog.length > 200) plog.shift(); }
const redir = new Map(); // endereço original -> destino final (servidores lentos no redirecionamento)
let lastErr = null; // última recusa do servidor (o app mostra o motivo ao usuário)
function upstream(u, inHeaders, res, depth = 0, orig = null, useCache = true) {
  if (!res._ntHook) {
    res._ntHook = true;
    res.on('close', () => { res._ntClosed = true; try { res._ntReq && res._ntReq.destroy(); } catch (e) {} try { res._ntUp && res._ntUp.destroy(); } catch (e) {} });
  }
  if (res._ntClosed) return;
  let url;
  const o = orig || u;
  if (depth === 0 && useCache) {
    const c = redir.get(u);
    if (c && Date.now() - c.t < 10 * 60 * 1000) { try { url = new URL(c.u); } catch (e) {} }
  }
  const cached = !!url;
  if (!url) { try { url = new URL(u); } catch (e) { res.writeHead(400); return res.end(); } }
  const isS = url.protocol === 'https:';
  const h = { 'User-Agent': UA, Accept: '*/*', 'Icy-MetaData': '0' };
  if (inHeaders.range) h.Range = inHeaders.range;
  const retryOrig = () => { redir.delete(o); upstream(o, inHeaders, res, 0, null, false); };
  const t0 = Date.now();
  const req = (isS ? https : http).get(url, { headers: h, agent: isS ? keep.https : keep.http, timeout: 60000 }, (up) => {
    logp(up.statusCode + ' ' + (Date.now() - t0) + 'ms ' + (cached ? '[cache] ' : '') + url.host + mask(url.pathname) + ' ' + String(up.headers['content-type'] || '') + (up.headers.location ? ' -> ' + new URL(up.headers.location, url).host : ''));
    if ([301, 302, 303, 307, 308].includes(up.statusCode) && up.headers.location && depth < 8) {
      up.resume();
      const nx = new URL(up.headers.location, url).href;
      redir.set(o, { u: nx, t: Date.now() });
      return upstream(nx, inHeaders, res, depth + 1, o, false);
    }
    if (cached && up.statusCode >= 400) { up.resume(); return retryOrig(); }
    if (inHeaders['x-probe']) {
      up.destroy();
      res.writeHead(200, { 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*' });
      return res.end(JSON.stringify({ status: up.statusCode, ct: String(up.headers['content-type'] || ''), final: url.href }));
    }
    if (up.statusCode >= 400 && !res.headersSent) {
      // servidor recusou antes de começar. Em contas de 1 tela o servidor demora alguns segundos para
      // liberar a conexão anterior (troca de canal, outro player): espera e tenta de novo.
      let body = '';
      up.setEncoding('utf8');
      up.on('data', (d) => { if (body.length < 400) body += d; });
      up.on('end', () => {
        const tries = (res._ntTry = (res._ntTry || 0) + 1);
        lastErr = { status: up.statusCode, body: body.replace(/\s+/g, ' ').trim().slice(0, 300), host: url.host, u: o, t: Date.now(), tries };
        const retry = ![400, 404, 410].includes(up.statusCode) && tries <= 4 && !res._ntClosed;
        logp('  servidor recusou: HTTP ' + up.statusCode + (body ? ' "' + lastErr.body.slice(0, 80) + '"' : '') + (retry ? ' — aguardando liberar a conexão (' + tries + '/4)' : ''));
        if (retry) return setTimeout(() => upstream(o, inHeaders, res, 0, null, false), 1500 * tries);
        if (!res._ntClosed) { res.writeHead(up.statusCode, { 'Access-Control-Allow-Origin': '*', 'Content-Type': 'text/plain; charset=utf-8' }); res.end(body); }
      });
      return;
    }
    const ct = String(up.headers['content-type'] || '');
    const m3u8 = /mpegurl/i.test(ct) || /\.m3u8$/i.test(url.pathname);
    if (m3u8 && up.statusCode < 400) {
      let b = '';
      up.setEncoding('utf8');
      up.on('data', (d) => (b += d));
      up.on('end', () => {
        const out = b.split(/\r?\n/).map((l) => {
          const t = l.trim();
          if (!t) return l;
          if (t.startsWith('#')) return l.replace(/URI="([^"]+)"/g, (_, x) => 'URI="' + prox(new URL(x, url).href) + '"');
          return prox(new URL(t, url).href);
        }).join('\n');
        logp('  m3u8: ' + (b.match(/#EXTINF/g) || []).length + ' seg' + (/#EXT-X-ENDLIST/.test(b) ? ' ENDLIST' : '') + ' seq=' + ((b.match(/MEDIA-SEQUENCE:(\d+)/) || [])[1] || '-') + (/#EXT-X-STREAM-INF/.test(b) ? ' MASTER' : ''));
        res.writeHead(200, { 'Content-Type': 'application/vnd.apple.mpegurl', 'Access-Control-Allow-Origin': '*', 'Cache-Control': 'no-cache' });
        res.end(out);
      });
      return;
    }
    const live = !!inHeaders['x-live'];
    if (up.statusCode >= 400 && res.headersSent) {
      // falha ao pedir o próximo pedaço de um canal ao vivo: tenta de novo
      up.resume();
      res._ntFails = (res._ntFails || 0) + 1;
      if (live && !res._ntClosed && res._ntFails < 12) return setTimeout(() => upstream(o, inHeaders, res, 0, null, false), 800);
      return res.end();
    }
    if (!res.headersSent) {
      const oh = { 'Access-Control-Allow-Origin': '*', 'Content-Type': ct || 'video/mp2t', 'Cache-Control': 'no-cache' };
      for (const k of (live ? [] : ['content-length', 'content-range', 'accept-ranges'])) if (up.headers[k]) oh[k] = up.headers[k];
      res.writeHead(up.statusCode, oh);
    }
    res._ntUp = up;
    let bytes = 0;
    // MPEG-TS: pacotes de 188 bytes começando com 0x47. Cada pedaço do servidor pode terminar
    // no meio de um pacote; só repassamos pacotes inteiros e realinhamos no início do próximo pedaço.
    const ts = live && !/mpegurl|text|json/i.test(ct);
    let carry = null, synced = false;
    const out = (b) => { if (b.length && !res.write(b)) { up.pause(); res.once('drain', () => up.resume()); } };
    up.on('data', (d) => {
      bytes += d.length;
      if (!ts) return out(d);
      let buf = carry ? Buffer.concat([carry, d]) : d;
      carry = null;
      if (!synced) {
        let i = 0;
        while (i + 376 < buf.length && !(buf[i] === 0x47 && buf[i + 188] === 0x47 && buf[i + 376] === 0x47)) i++;
        if (i + 376 >= buf.length) { carry = buf.slice(Math.max(0, i - 1)); return; }
        buf = buf.slice(i); synced = true;
      }
      const whole = buf.length - (buf.length % 188);
      if (whole < buf.length) carry = Buffer.from(buf.slice(whole));
      out(buf.slice(0, whole));
    });
    up.on('end', () => {
      if (live && !res._ntClosed) {
        // o servidor entrega o canal em pedaços (~20 s): pede o próximo e emenda sem o player perceber
        res._ntFails = bytes > 0 ? 0 : (res._ntFails || 0) + 1;
        logp('  pedaço terminou (' + Math.round(bytes / 1024) + ' KB) — emendando o próximo');
        if (res._ntFails < 12) return upstream(o, inHeaders, res, 0, null, false);
      }
      res.end();
    });
    up.on('error', () => { if (!live) res.end(); });
  });
  req.on('timeout', () => req.destroy(new Error('timeout')));
  req.on('error', (er) => {
    logp('ERRO ' + (Date.now() - t0) + 'ms ' + url.host + mask(url.pathname) + ' ' + (er && er.message));
    if (inHeaders['x-live'] && res.headersSent && !res._ntClosed) {
      res._ntFails = (res._ntFails || 0) + 1;
      if (res._ntFails < 12) return setTimeout(() => upstream(o, inHeaders, res, 0, null, false), 1000);
    }
    if (cached && !res.headersSent) return retryOrig();
    if (!res.headersSent && !res._ntClosed) {
      const tries = (res._ntTry = (res._ntTry || 0) + 1);
      lastErr = { status: 0, body: String(er && er.message || 'erro de rede'), host: url.host, u: o, t: Date.now(), tries };
      if (tries <= 2 && !inHeaders['x-probe']) return setTimeout(() => upstream(o, inHeaders, res, 0, null, false), 1500);
    }
    if (!res.headersSent) res.writeHead(502, { 'Access-Control-Allow-Origin': '*' });
    res.end();
  });
  res._ntReq = req;
}
// ---- FFmpeg (modo compatibilidade): converte o áudio (AC3/EAC3/DTS → AAC) e, se preciso, o vídeo
// (H.265/HEVC 10 bits, 4K) para H.264, usando a placa de vídeo quando houver (NVIDIA/Intel/AMD).
let FF = null;
try { FF = process.env.NT_FFMPEG || require('ffmpeg-static'); if (FF) FF = FF.replace('app.asar', 'app.asar.unpacked'); if (!FF || !fs.existsSync(FF)) FF = null; } catch (e) { FF = null; }
const ffInfo = new Map();
let encP = null;
function detectEnc() {
  return new Promise((ok) => {
    if (!FF) return ok(null);
    const cands = ['h264_nvenc', 'h264_qsv', 'h264_amf'];
    let i = 0;
    const nx = () => {
      if (i >= cands.length) return ok('libx264');
      const e = cands[i++];
      let p;
      try { p = spawn(FF, ['-hide_banner', '-loglevel', 'error', '-f', 'lavfi', '-i', 'color=c=black:s=640x360:d=0.2', '-frames:v', '2', '-c:v', e, '-f', 'null', '-'], { windowsHide: true }); } catch (x) { return nx(); }
      const t = setTimeout(() => { try { p.kill(); } catch (x) {} }, 8000);
      p.on('error', () => { clearTimeout(t); nx(); });
      p.on('exit', (c) => { clearTimeout(t); if (c === 0) ok(e); else nx(); });
    };
    nx();
  });
}
const getEnc = () => encP || (encP = detectEnc().then((e) => { logp('FFmpeg: codificador de vídeo = ' + e); return e; }));
async function ffStream(p, r) {
  const CORS = { 'Access-Control-Allow-Origin': '*' };
  if (!FF) { r.writeHead(501, CORS); return r.end('ffmpeg ausente'); }
  const u = p.searchParams.get('u'), live = p.searchParams.get('live') === '1', ss = Math.max(0, +p.searchParams.get('ss') || 0);
  const id = p.searchParams.get('id') || String(Date.now()), enc = p.searchParams.get('v') === 'enc' ? await getEnc() : null;
  const info = { id, dur: 0, v: '', a: '', w: 0, h: 0, enc: enc || 'copy', err: '', t: Date.now() };
  ffInfo.set(id, info);
  if (ffInfo.size > 20) ffInfo.delete(ffInfo.keys().next().value);
  const a = ['-hide_banner', '-nostdin', '-loglevel', 'info'];
  // a entrada passa sempre pelo repasse local: cara de VLC, redirecionamentos, certificados ruins, espera da
  // conexão única e (ao vivo) emenda dos pedaços. Nos filmes o repasse repassa o Range, então o -ss pula direto.
  const input = `http://127.0.0.1:${proxyPort}/p?${live ? 'live=1&' : ''}u=${encodeURIComponent(u)}`;
  if (!live) { a.push('-reconnect', '1', '-reconnect_on_network_error', '1', '-reconnect_delay_max', '4'); if (ss > 0) a.push('-ss', String(ss)); }
  a.push('-fflags', '+genpts+discardcorrupt', '-analyzeduration', '5000000', '-probesize', '10000000', '-i', input, '-map', '0:v:0?', '-map', '0:a:0?', '-sn', '-dn');
  if (!enc) a.push('-c:v', 'copy');
  else if (enc === 'libx264') a.push('-c:v', 'libx264', '-preset', 'veryfast', '-crf', '21', '-vf', "scale=-2:'min(1080,ih)',format=yuv420p", '-g', '60');
  else a.push('-c:v', enc, '-vf', 'format=yuv420p', '-b:v', '14M', '-maxrate', '20M', '-bufsize', '28M', '-g', '60');
  a.push('-c:a', 'aac', '-b:a', '192k', '-ac', '2', '-ar', '48000', '-f', 'mpegts', '-mpegts_flags', '+resend_headers', '-muxdelay', '0', '-muxpreload', '0', 'pipe:1');
  logp('FFmpeg ' + (live ? 'ao vivo' : 'vod' + (ss ? ' a partir de ' + Math.round(ss) + 's' : '')) + ' — vídeo ' + (enc || 'copiado') + ', áudio AAC');
  let ff;
  try { ff = spawn(FF, a, { windowsHide: true }); } catch (e) { r.writeHead(500, CORS); return r.end(String(e.message)); }
  let err = '', head = true, got = false;
  ff.stderr.setEncoding('utf8');
  ff.stderr.on('data', (d) => {
    err = (err + d).slice(-6000);
    if (head) {
      const inp = err.split(/Output #0/)[0];
      const du = inp.match(/Duration: (\d+):(\d+):(\d+(?:\.\d+)?)/);
      if (du) info.dur = +du[1] * 3600 + +du[2] * 60 + +du[3];
      const vv = inp.match(/Video: (\w+)[^\n]*?, (\d{2,5})x(\d{2,5})/);
      if (vv) { info.v = vv[1]; info.w = +vv[2]; info.h = +vv[3]; }
      const aa = inp.match(/Audio: (\w+)/);
      if (aa) info.a = aa[1];
      if (/Output #0/.test(err)) { head = false; logp('  FFmpeg entrada: vídeo ' + (info.v || '?') + ' ' + info.w + 'x' + info.h + ', áudio ' + (info.a || '?') + (info.dur ? ', ' + Math.round(info.dur) + 's' : '')); }
    }
    const bad = String(d).split('\n').find((l) => /error|invalid|failed|refused|forbidden|not found/i.test(l) && !/non monoton|decode_slice|concealing/i.test(l));
    if (bad) info.err = bad.trim().slice(0, 200);
  });
  ff.stdout.on('data', (b) => {
    if (!got) { got = true; r.writeHead(200, { ...CORS, 'Content-Type': 'video/mp2t', 'Cache-Control': 'no-cache' }); }
    if (!r.write(b)) { ff.stdout.pause(); r.once('drain', () => ff.stdout.resume()); }
  });
  const kill = () => { try { ff.kill('SIGKILL'); } catch (e) {} };
  r.on('close', kill);
  ff.on('error', (e) => { info.err = String(e.message); if (!got) { got = true; r.writeHead(502, CORS); } r.end(); });
  ff.on('exit', (code) => {
    if (code) logp('  FFmpeg terminou com código ' + code + (info.err ? ': ' + info.err : ''));
    if (!got) { got = true; r.writeHead(502, CORS); }
    r.end();
  });
}
// ---- players externos instalados no Windows
const PLAYERS = {
  vlc: { n: 'VLC', p: ['VideoLAN\\VLC\\vlc.exe'], a: (u) => ['--one-instance', '--http-user-agent=' + UA, u] },
  mpv: { n: 'mpv', p: ['mpv\\mpv.exe', 'mpv.net\\mpvnet.exe'], a: (u) => ['--force-window=immediate', '--user-agent=' + UA, u] },
  pot: { n: 'PotPlayer', p: ['DAUM\\PotPlayer\\PotPlayerMini64.exe', 'DAUM\\PotPlayer\\PotPlayerMini.exe', 'PotPlayer\\PotPlayerMini64.exe'], a: (u) => [u] },
  mpc: { n: 'MPC-HC', p: ['MPC-HC\\mpc-hc64.exe', 'MPC-HC\\mpc-hc.exe', 'K-Lite Codec Pack\\MPC-HC64\\mpc-hc64.exe', 'MPC-BE x64\\mpc-be64.exe', 'MPC-BE\\mpc-be.exe'], a: (u) => [u] }
};
function findPlayer(id) {
  const P = PLAYERS[id];
  if (!P) return null;
  const dirs = [process.env['ProgramFiles'], process.env['ProgramFiles(x86)'], process.env['LOCALAPPDATA'] && path.join(process.env['LOCALAPPDATA'], 'Programs'), 'C:\\Program Files', 'C:\\Program Files (x86)'].filter(Boolean);
  for (const d of dirs) for (const rel of P.p) { const f = path.join(d, rel); try { if (fs.existsSync(f)) return f; } catch (e) {} }
  return null;
}
function openExt(id, url, live) {
  const exe = findPlayer(id);
  if (!exe) return { error: (PLAYERS[id] ? PLAYERS[id].n : 'Player') + ' não encontrado neste PC.' };
  // ao vivo: passa pelo repasse local, que emenda os pedaços e tem cara de VLC
  const u = live && proxyPort ? `http://127.0.0.1:${proxyPort}/p?live=1&u=${encodeURIComponent(url)}` : url;
  spawn(exe, PLAYERS[id].a(u), { detached: true, stdio: 'ignore' }).unref();
  return { ok: true, name: PLAYERS[id].n };
}
const proxyServer = http.createServer((q, r) => {
  if (q.method === 'OPTIONS') { r.writeHead(204, { 'Access-Control-Allow-Origin': '*', 'Access-Control-Allow-Headers': '*' }); return r.end(); }
  const p = new URL(q.url, 'http://x');
  if (p.pathname === '/log') { r.writeHead(200, { 'Content-Type': 'text/plain; charset=utf-8', 'Access-Control-Allow-Origin': '*' }); return r.end(plog.slice(-40).join('\n')); }
  if (p.pathname === '/lasterr') { r.writeHead(200, { 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*' }); return r.end(JSON.stringify(lastErr)); }
  if (p.pathname === '/ffinfo') { r.writeHead(200, { 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*' }); return r.end(JSON.stringify(ffInfo.get(p.searchParams.get('id')) || null)); }
  if (p.pathname === '/ff') return ffStream(p, r);
  const u = p.searchParams.get('u');
  if (!u) { r.writeHead(400); return r.end(); }
  if (p.pathname === '/probe') q.headers['x-probe'] = '1';
  if (p.searchParams.get('live') === '1') q.headers['x-live'] = '1';
  upstream(u, q.headers, r);
});
proxyServer.listen(0, '127.0.0.1', () => { proxyPort = proxyServer.address().port; });
ipcMain.handle('proxy-port', () => proxyPort);
const fileArg = (argv) => argv.find((a) => /\.m3u8?$/i.test(a) && fs.existsSync(a));
function send(file) {
  if (win && file) win.webContents.send('open-m3u', { name: path.basename(file), text: fs.readFileSync(file, 'utf8') });
}
async function pick() {
  const r = await dialog.showOpenDialog(win, { filters: [{ name: 'M3U', extensions: ['m3u', 'm3u8', 'txt'] }], properties: ['openFile'] });
  if (!r.canceled) send(r.filePaths[0]);
}
// Desempenho de vídeo no Windows
app.commandLine.appendSwitch('ignore-gpu-blocklist');
app.commandLine.appendSwitch('enable-gpu-rasterization');
app.commandLine.appendSwitch('enable-zero-copy');
app.commandLine.appendSwitch('disable-features', 'CalculateNativeWinOcclusion');
// 4K: decodificação por hardware (H.265/HEVC quando a placa de vídeo e o Windows suportam)
app.commandLine.appendSwitch('enable-features', 'PlatformHEVCDecoderSupport,VaapiVideoDecoder');
if (!app.requestSingleInstanceLock()) app.quit();
app.on('second-instance', (_, argv) => { if (win) { win.focus(); send(fileArg(argv)); } });
app.whenReady().then(() => {
  // Streams IPTV costumam exigir User-Agent de player (VLC) e não aceitam CORS de navegador
  session.defaultSession.webRequest.onBeforeSendHeaders((d, cb) => {
    if (/^https?:/i.test(d.url) && d.resourceType !== 'image') {
      d.requestHeaders['User-Agent'] = UA;
      // servidores IPTV costumam recusar pedidos com cara de navegador
      for (const k of Object.keys(d.requestHeaders)) if (/^(origin|referer|sec-fetch-|sec-ch-)/i.test(k)) delete d.requestHeaders[k];
    }
    cb({ requestHeaders: d.requestHeaders });
  });
  win = new BrowserWindow({
    width: 1280, height: 800, backgroundColor: '#080C18', title: 'NTPlay', autoHideMenuBar: true, icon: path.join(__dirname, '..', 'build', 'icon.png'),
    webPreferences: { preload: path.join(__dirname, 'preload.js'), webSecurity: false, autoplayPolicy: 'no-user-gesture-required', backgroundThrottling: false }
  });
  // sem barra de menu (Arquivo/Exibir): atalhos por teclado
  Menu.setApplicationMenu(null);
  win.removeMenu();
  let userFs = false;
  win.webContents.on('before-input-event', (e, i) => {
    if (i.type !== 'keyDown') return;
    const ctrl = i.control || i.meta, k = String(i.key).toLowerCase();
    if (ctrl && k === 'o') { e.preventDefault(); pick(); }
    else if (k === 'f11') { e.preventDefault(); userFs = !win.isFullScreen(); win.setFullScreen(userFs); }
    else if (k === 'f12' || (ctrl && i.shift && k === 'i')) win.webContents.toggleDevTools();
    else if (ctrl && k === 'r') win.reload();
  });
  // vídeo em tela cheia = janela em tela cheia, sem barra de título nem menu
  win.webContents.on('enter-html-full-screen', () => { win.setMenuBarVisibility(false); if (!win.isFullScreen()) win.setFullScreen(true); });
  win.webContents.on('leave-html-full-screen', () => { if (!userFs) win.setFullScreen(false); });
  win.loadFile(path.join(__dirname, '..', 'www', 'index.html'));
  win.webContents.once('did-finish-load', () => send(fileArg(process.argv)));
});
ipcMain.handle('pick-file', pick);
ipcMain.handle('open-vlc', (_, { url, live }) => {
  const r = openExt('vlc', url, live);
  if (r.error) r.error = 'VLC não encontrado. Instale o VLC (videolan.org) e tente de novo.';
  return r;
});
ipcMain.handle('players', () => Object.keys(PLAYERS).filter((k) => findPlayer(k)).map((k) => ({ id: k, n: PLAYERS[k].n })));
ipcMain.handle('open-ext', (_, { id, url, live }) => openExt(id, url, live));
ipcMain.handle('ff-ok', async () => ({ ok: !!FF, enc: FF ? await getEnc() : null }));
app.on('window-all-closed', () => app.quit());
