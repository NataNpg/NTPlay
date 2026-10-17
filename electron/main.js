const { app, BrowserWindow, dialog, ipcMain, Menu, session } = require('electron');
const fs = require('fs'), path = require('path');
const { spawn } = require('child_process');
const UA = 'VLC/3.0.20 LibVLC/3.0.20';
let win;
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
    if (!d.url.startsWith('file:') && d.resourceType !== 'image') d.requestHeaders['User-Agent'] = UA;
    cb({ requestHeaders: d.requestHeaders });
  });
  win = new BrowserWindow({
    width: 1280, height: 800, backgroundColor: '#080C18', title: 'NTPlay', autoHideMenuBar: true,
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
