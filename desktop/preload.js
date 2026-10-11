const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('canvasDesktop', {
  listDocuments: () => ipcRenderer.invoke('docs:list'),
  saveDocument: (doc) => ipcRenderer.invoke('docs:save', doc),
  deleteDocuments: (ids) => ipcRenderer.invoke('docs:delete', ids),
  importFiles: () => ipcRenderer.invoke('docs:importDialog'),
  exportFile: (doc) => ipcRenderer.invoke('docs:exportDialog', doc),
  readClipboard: () => ipcRenderer.invoke('clipboard:read'),
  writeClipboard: (text) => ipcRenderer.invoke('clipboard:write', text),
  getPrefs: () => ipcRenderer.invoke('prefs:get'),
  setPrefs: (patch) => ipcRenderer.invoke('prefs:set', patch),
  openStorageDir: () => ipcRenderer.invoke('shell:openStorageDir')
});
