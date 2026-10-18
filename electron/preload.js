const { contextBridge, ipcRenderer } = require('electron');
contextBridge.exposeInMainWorld('bobNative', {
  openVlc: (url, name) => ipcRenderer.invoke('open-vlc', { url, name }),
  pickFile: () => ipcRenderer.invoke('pick-file'),
  proxyPort: () => ipcRenderer.invoke('proxy-port'),
  onOpen: (cb) => ipcRenderer.on('open-m3u', (_, f) => cb(f))
});
