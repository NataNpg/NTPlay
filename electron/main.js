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
      if (live && !res._ntClosed && res._ntFails < 12) return setTimeout(() => upstream(o, inHeaders, res, 0, null, res._ntFails < 3), 800);
      return res.end();
    }
    if (!res.headersSent) {
      const oh = { 'Access-Control-Allow-Origin': '*', 'Content-Type': ct || 'video/mp2t', 'Cache-Control': 'no-cache' };
      for (const k of (live ? [] : ['content-length', 'content-range', 'accept-ranges'])) if (up.headers[k]) oh[k] = up.headers[k];
      res.writeHead(up.statusCode, oh);
    }
    res._ntUp = up;
    let bytes = 0;
    up.on('data', (d) => {
      bytes += d.length;
      if (!res.write(d)) { up.pause(); res.once('drain', () => up.resume()); }
    });
    up.on('end', () => {
      if (live && !res._ntClosed) {
        // o servidor entrega o canal em pedaços (~20 s): pede o próximo e emenda sem o player perceber
        res._ntFails = bytes > 0 ? 0 : (res._ntFails || 0) + 1;
        logp('  pedaço terminou (' + Math.round(bytes / 1024) + ' KB) — emendando o próximo');
        if (res._ntFails < 12) return upstream(o, inHeaders, res, 0, null, true);
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
      if (res._ntFails < 12) return setTimeout(() => upstream(o, inHeaders, res, 0, null, res._ntFails < 3), 1000);
    }
    if (cached && !res.headersSent) return retryOrig();
    if (!res.headersSent) res.writeHead(502, { 'Access-Control-Allow-Origin': '*' });
    res.end();
  });
  res._ntReq = req;
}
const proxyServer = http.createServer((q, r) => {
  if (q.method === 'OPTIONS') { r.writeHead(204, { 'Access-Control-Allow-Origin': '*', 'Access-Control-Allow-Headers': '*' }); return r.end(); }
  const p = new URL(q.url, 'http://x');
  if (p.pathname === '/log') { r.writeHead(200, { 'Content-Type': 'text/plain; charset=utf-8', 'Access-Control-Allow-Origin': '*' }); return r.end(plog.slice(-40).join('\n')); }
  const u = p.searchParams.get('u');
  if (!u) { r.writeHead(400); return r.end(); }
  if (p.pathname === '/log0') { r.writeHead(200, { 'Content-Type': 'text/plain; charset=utf-8', 'Access-Control-Allow-Origin': '*' }); return r.end(plog.slice(-40).join('\n')); }
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
ipcMain.handle('open-vlc', (_, { url }) => {
  const dirs = [process.env['ProgramFiles'], process.env['ProgramFiles(x86)'], 'C:\\Program Files', 'C:\\Program Files (x86)'].filter(Boolean);
  const exe = dirs.map((d) => path.join(d, 'VideoLAN', 'VLC', 'vlc.exe')).find((p) => fs.existsSync(p));
  if (!exe) return { error: 'VLC não encontrado. Instale o VLC (videolan.org) e tente de novo.' };
  spawn(exe, ['--one-instance', url], { detached: true, stdio: 'ignore' }).unref();
  return { ok: true };
});
app.on('window-all-closed', () => app.quit());
