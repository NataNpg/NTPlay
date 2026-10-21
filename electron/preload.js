const { contextBridge, ipcRenderer } = require('electron');
contextBridge.exposeInMainWorld('bobNative', {
  openVlc: (url, name, live) => ipcRenderer.invoke('open-vlc', { url, name, live }),
  players: () => ipcRenderer.invoke('players'),
  openExt: (id, url, live) => ipcRenderer.invoke('open-ext', { id, url, live }),
  ffOk: () => ipcRenderer.invoke('ff-ok'),
  version: () => ipcRenderer.invoke('app-version'),
  pickFile: () => ipcRenderer.invoke('pick-file'),
  proxyPort: () => ipcRenderer.invoke('proxy-port'),
  onOpen: (cb) => ipcRenderer.on('open-m3u', (_, f) => cb(f))
});
