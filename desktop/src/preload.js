const { contextBridge, ipcRenderer } = require('electron');
contextBridge.exposeInMainWorld('decan', {
  info: () => ipcRenderer.invoke('app:info'),
  window: { minimize: () => ipcRenderer.invoke('window:minimize'), toggleMaximize: () => ipcRenderer.invoke('window:toggleMaximize'), close: () => ipcRenderer.invoke('window:close') },
  settings: { get: () => ipcRenderer.invoke('settings:get'), save: value => ipcRenderer.invoke('settings:save', value), chooseDirectory: () => ipcRenderer.invoke('dialog:choose-directory') },
  history: { get: () => ipcRenderer.invoke('history:get'), save: value => ipcRenderer.invoke('history:save', value) },
  watchlist: { get: () => ipcRenderer.invoke('watchlist:get'), save: value => ipcRenderer.invoke('watchlist:save', value) },
  updates: { latest: () => ipcRenderer.invoke('updates:latest'), download: (asset, destination) => ipcRenderer.invoke('updates:download', asset, destination), openInstaller: file => ipcRenderer.invoke('updates:open-installer', file), onProgress: callback => { const fn = (_, data) => callback(data); ipcRenderer.on('updates:progress', fn); return () => ipcRenderer.removeListener('updates:progress', fn); } },
  shell: { open: target => ipcRenderer.invoke('shell:open', target) },
  notification: { show: (title, body) => ipcRenderer.invoke('notification:show', title, body) }
});
