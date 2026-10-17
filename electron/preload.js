const { contextBridge, ipcRenderer } = require('electron');
contextBridge.exposeInMainWorld('bobNative', {
  openVlc: (url, name) => ipcRenderer.invoke('open-vlc', { url, name }),
  pickFile: () => ipcRenderer.invoke('pick-file'),
  onOpen: (cb) => ipcRenderer.on('open-m3u', (_, f) => cb(f))
});
