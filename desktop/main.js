const { app, BrowserWindow, ipcMain, dialog, clipboard, shell, nativeTheme } = require('electron');
const path = require('path');
const fs = require('fs');
const crypto = require('crypto');

let mainWindow = null;

function getStorePath() {
  return path.join(app.getPath('userData'), 'documents.json');
}

function getPrefsPath() {
  return path.join(app.getPath('userData'), 'prefs.json');
}

function defaultStarterDocuments() {
  const now = Date.now();
  return [
    {
      id: 'starter-html-atelier',
      title: 'Atelier 交互粒子画布.html',
      updatedAt: now,
      revision: 1,
      content: `<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8">
<style>
  * { box-sizing: border-box; margin: 0; padding: 0; }
  body {
    min-height: 100vh;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    background: radial-gradient(circle at 30% 20%, #1e293b, #0f172a 75%);
    color: #f8fafc;
    font-family: system-ui, -apple-system, "Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif;
    padding: 24px;
  }
  .card {
    background: rgba(30, 41, 59, 0.82);
    border: 1px solid rgba(148, 163, 184, 0.22);
    border-radius: 20px;
    padding: 28px 32px;
    max-width: 460px;
    width: 100%;
    box-shadow: 0 20px 50px rgba(0, 0, 0, 0.35);
    backdrop-filter: blur(12px);
    text-align: center;
  }
  .badge {
    display: inline-block;
    padding: 4px 12px;
    border-radius: 999px;
    background: rgba(59, 130, 246, 0.2);
    color: #93c5fd;
    font-size: 12px;
    font-weight: 600;
    margin-bottom: 14px;
  }
  h1 { font-size: 22px; margin-bottom: 8px; letter-spacing: 0.01em; }
  p { font-size: 13px; color: #94a3b8; line-height: 1.6; margin-bottom: 20px; }
  .meter {
    height: 10px;
    background: #0f172a;
    border-radius: 999px;
    overflow: hidden;
    margin-bottom: 18px;
  }
  .bar {
    height: 100%;
    width: 72%;
    background: linear-gradient(90deg, #3b82f6, #2dd4bf);
    border-radius: 999px;
    transition: width 0.35s cubic-bezier(0.22, 1, 0.36, 1);
  }
  button {
    background: #2563eb;
    color: #fff;
    border: none;
    border-radius: 12px;
    padding: 10px 20px;
    font-size: 14px;
    font-weight: 600;
    cursor: pointer;
    transition: transform 0.15s ease, background 0.15s ease;
  }
  button:hover { background: #1d4ed8; transform: translateY(-1px); }
</style>
</head>
<body>
  <div class="card">
    <span class="badge">HTML5 · 实时画布</span>
    <h1>代码画布 · 桌面工作台</h1>
    <p>在左侧编辑代码或直接按 Ctrl+Shift+V 粘贴 AI 生成的 HTML / SVG / XML，右侧画布将立即实时渲染。</p>
    <div class="meter"><div class="bar" id="bar"></div></div>
    <button onclick="randomize()">随机切换能量波形</button>
  </div>
  <script>
    function randomize() {
      const pct = Math.floor(25 + Math.random() * 70);
      document.getElementById('bar').style.width = pct + '%';
    }
  </script>
</body>
</html>`
    },
    {
      id: 'starter-svg-geometry',
      title: '几何黄金螺旋徽章.svg',
      updatedAt: now - 60000,
      revision: 1,
      content: `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 320 240" width="320" height="240">
  <defs>
    <linearGradient id="bgGrad" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0%" stop-color="#0D1420"/>
      <stop offset="100%" stop-color="#1E293B"/>
    </linearGradient>
    <linearGradient id="accentGrad" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0%" stop-color="#38BDF8"/>
      <stop offset="100%" stop-color="#6366F1"/>
    </linearGradient>
  </defs>
  <rect width="320" height="240" rx="24" fill="url(#bgGrad)"/>
  <circle cx="160" cy="120" r="74" fill="none" stroke="url(#accentGrad)" stroke-width="3" opacity="0.85"/>
  <circle cx="160" cy="120" r="48" fill="none" stroke="#2DD4BF" stroke-width="2" stroke-dasharray="6 6"/>
  <polygon points="160,58 214,152 106,152" fill="none" stroke="#F8FAFC" stroke-width="2.5" stroke-linejoin="round"/>
  <circle cx="160" cy="120" r="8" fill="#38BDF8"/>
</svg>`
    },
    {
      id: 'starter-xml-config',
      title: 'Atelier 主题规范配置.xml',
      updatedAt: now - 120000,
      revision: 1,
      content: `<?xml version="1.0" encoding="UTF-8"?>
<atelierDesignSystem version="0.4.0">
  <palette mode="DualTheme">
    <token name="canvas_bg" day="#F5F7FA" night="#0D1420" />
    <token name="canvas_primary" day="#2456C6" night="#8EB4FF" />
    <token name="canvas_surface" day="#FFFFFF" night="#151E2C" />
  </palette>
  <radii unit="dp">
    <thumbnail>12</thumbnail>
    <card>16</card>
    <dialog>20</dialog>
  </radii>
</atelierDesignSystem>`
    }
  ];
}

function loadDocuments() {
  const file = getStorePath();
  try {
    if (!fs.existsSync(file)) {
      const initial = defaultStarterDocuments();
      saveDocuments(initial);
      return initial;
    }
    const raw = fs.readFileSync(file, 'utf8');
    const parsed = JSON.parse(raw);
    if (!Array.isArray(parsed)) return defaultStarterDocuments();
    // Auto-heal starter XML if it had the old '< radii' typo
    let mutated = false;
    for (const doc of parsed) {
      if (doc && typeof doc.content === 'string' && doc.content.includes('< radii unit="dp">')) {
        doc.content = doc.content.replace('< radii unit="dp">', '<radii unit="dp">');
        mutated = true;
      }
    }
    if (mutated) saveDocuments(parsed);
    return parsed;
  } catch (e) {
    return defaultStarterDocuments();
  }
}

function saveDocuments(docs) {
  const file = getStorePath();
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, JSON.stringify(docs, null, 2), 'utf8');
}

function loadPrefs() {
  const file = getPrefsPath();
  const defaults = {
    theme: 'system',
    viewMode: 'split',
    clipboardAutoDetect: true,
    alwaysOnTop: false,
    showGrid: false,
    wordWrap: true,
    fontSize: 12,
    splitRatio: 0.50
  };
  try {
    if (!fs.existsSync(file)) return defaults;
    return Object.assign(defaults, JSON.parse(fs.readFileSync(file, 'utf8')));
  } catch (e) {
    return defaults;
  }
}

function savePrefs(prefs) {
  const file = getPrefsPath();
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, JSON.stringify(prefs, null, 2), 'utf8');
}

function isEffectiveDark(prefs) {
  if (prefs.theme === 'dark') return true;
  if (prefs.theme === 'light') return false;
  return Boolean(nativeTheme.shouldUseDarkColors);
}

function computeTitleBarOverlay(prefs) {
  const dark = isEffectiveDark(prefs);
  return {
    color: dark ? '#151E2C' : '#FFFFFF',
    symbolColor: dark ? '#F2F6FB' : '#1A2433',
    height: 52
  };
}

function createWindow() {
  const prefs = loadPrefs();
  const dark = isEffectiveDark(prefs);
  mainWindow = new BrowserWindow({
    width: 1340,
    height: 840,
    minWidth: 920,
    minHeight: 580,
    title: '代码画布 · Code Canvas',
    backgroundColor: dark ? '#0D1420' : '#F5F7FA',
    autoHideMenuBar: true,
    titleBarStyle: 'hidden',
    titleBarOverlay: computeTitleBarOverlay(prefs),
    alwaysOnTop: Boolean(prefs.alwaysOnTop),
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: false
    }
  });

  mainWindow.loadFile(path.join(__dirname, 'src', 'index.html'));
}

app.whenReady().then(() => {
  createWindow();

  nativeTheme.on('updated', () => {
    if (mainWindow && !mainWindow.isDestroyed()) {
      const prefs = loadPrefs();
      if (typeof mainWindow.setTitleBarOverlay === 'function') {
        try { mainWindow.setTitleBarOverlay(computeTitleBarOverlay(prefs)); } catch (e) {}
      }
    }
  });

  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow();
  });
});

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit();
});

// IPC Handlers
ipcMain.handle('docs:list', () => {
  const docs = loadDocuments();
  docs.sort((a, b) => (b.updatedAt || 0) - (a.updatedAt || 0));
  return docs;
});

ipcMain.handle('docs:save', (_event, doc) => {
  const docs = loadDocuments();
  const now = Date.now();
  const id = doc.id || crypto.randomUUID();
  const idx = docs.findIndex(d => d.id === id);
  const record = {
    id,
    title: String(doc.title || '未命名画布.html').trim() || '未命名画布.html',
    content: String(doc.content ?? ''),
    updatedAt: now,
    revision: idx >= 0 ? (docs[idx].revision || 0) + 1 : 1
  };
  if (idx >= 0) {
    docs[idx] = record;
  } else {
    docs.unshift(record);
  }
  saveDocuments(docs);
  return record;
});

ipcMain.handle('docs:delete', (_event, ids) => {
  const idSet = new Set(Array.isArray(ids) ? ids : [ids]);
  const docs = loadDocuments().filter(d => !idSet.has(d.id));
  saveDocuments(docs);
  return docs;
});

ipcMain.handle('docs:importDialog', async () => {
  const result = await dialog.showOpenDialog(mainWindow, {
    title: '导入代码或标记文件',
    properties: ['openFile', 'multiSelections'],
    filters: [
      { name: '代码画布文件', extensions: ['html', 'htm', 'svg', 'xml', 'txt', 'md', 'js', 'css', 'json'] },
      { name: '所有文件', extensions: ['*'] }
    ]
  });
  if (result.canceled || !result.filePaths.length) return [];
  const docs = loadDocuments();
  const imported = [];
  for (const filePath of result.filePaths) {
    const stat = fs.statSync(filePath);
    if (stat.size > 2 * 1024 * 1024) continue;
    const content = fs.readFileSync(filePath, 'utf8');
    const title = path.basename(filePath);
    const record = {
      id: crypto.randomUUID(),
      title,
      content,
      updatedAt: Date.now(),
      revision: 1
    };
    docs.unshift(record);
    imported.push(record);
  }
  saveDocuments(docs);
  return imported;
});

ipcMain.handle('docs:exportDialog', async (_event, doc) => {
  if (!doc) return false;
  const result = await dialog.showSaveDialog(mainWindow, {
    title: '导出源码文件',
    defaultPath: doc.title || 'canvas.html',
    filters: [
      { name: 'HTML / SVG / XML', extensions: ['html', 'svg', 'xml', 'txt'] },
      { name: '所有文件', extensions: ['*'] }
    ]
  });
  if (result.canceled || !result.filePath) return false;
  fs.writeFileSync(result.filePath, String(doc.content ?? ''), 'utf8');
  return true;
});

ipcMain.handle('clipboard:read', () => {
  try {
    return clipboard.readText() || '';
  } catch (e) {
    return '';
  }
});

ipcMain.handle('clipboard:write', (_event, text) => {
  clipboard.writeText(String(text ?? ''));
  return true;
});

ipcMain.handle('prefs:get', () => {
  const prefs = loadPrefs();
  return Object.assign({}, prefs, {
    systemDark: nativeTheme.shouldUseDarkColors,
    storageDir: app.getPath('userData'),
    isElectron: true
  });
});

ipcMain.handle('prefs:set', (_event, patch) => {
  const next = Object.assign(loadPrefs(), patch || {});
  savePrefs(next);
  if (mainWindow && !mainWindow.isDestroyed()) {
    if (typeof patch.alwaysOnTop === 'boolean') {
      mainWindow.setAlwaysOnTop(patch.alwaysOnTop);
    }
    if (typeof mainWindow.setTitleBarOverlay === 'function') {
      try { mainWindow.setTitleBarOverlay(computeTitleBarOverlay(next)); } catch (e) {}
    }
  }
  return Object.assign({}, next, {
    systemDark: nativeTheme.shouldUseDarkColors,
    storageDir: app.getPath('userData'),
    isElectron: true
  });
});

ipcMain.handle('shell:openStorageDir', () => {
  shell.openPath(app.getPath('userData'));
  return true;
});
