(function () {
  'use strict';

  const Core = window.CanvasCore;
  const isElectronEnv = Boolean(window.canvasDesktop);
  if (isElectronEnv) {
    document.body.classList.add('is-electron');
  }
  const Bridge = window.canvasDesktop || createBrowserFallbackBridge();

  function createBrowserFallbackBridge() {
    const DOCS_KEY = 'codecanvas.desktop.docs.v1';
    const PREFS_KEY = 'codecanvas.desktop.prefs.v1';
    function defaultDocs() {
      const now = Date.now();
      return [
        {
          id: 'starter-html-atelier',
          title: 'Atelier 交互粒子画布.html',
          updatedAt: now,
          revision: 1,
          content: '<!DOCTYPE html>\n<html lang="zh-CN">\n<head>\n  <meta charset="UTF-8">\n  <style>\n    body {\n      margin: 0;\n      min-height: 100vh;\n      display: flex;\n      align-items: center;\n      justify-content: center;\n      background: #0f172a;\n      color: #f8fafc;\n      font-family: system-ui, sans-serif;\n    }\n    .card {\n      padding: 28px 32px;\n      border-radius: 20px;\n      background: #1e293b;\n      border: 1px solid #334155;\n      text-align: center;\n    }\n    h1 { font-size: 22px; margin: 0 0 10px; }\n    p { color: #94a3b8; font-size: 13px; }\n  </style>\n</head>\n<body>\n  <div class="card">\n    <h1>代码画布 · 桌面工作台</h1>\n    <p>在左侧编辑代码或按 Ctrl+Shift+V 粘贴 AI 代码，右侧实时预览。</p>\n  </div>\n</body>\n</html>'
        },
        {
          id: 'starter-svg-geometry',
          title: '几何黄金螺旋徽章.svg',
          updatedAt: now - 60000,
          revision: 1,
          content: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 320 240">\n  <rect width="320" height="240" rx="24" fill="#0D1420"/>\n  <circle cx="160" cy="120" r="72" fill="none" stroke="#38BDF8" stroke-width="3"/>\n  <polygon points="160,58 214,152 106,152" fill="none" stroke="#F8FAFC" stroke-width="2.5"/>\n</svg>'
        },
        {
          id: 'starter-xml-config',
          title: 'Atelier 主题规范配置.xml',
          updatedAt: now - 120000,
          revision: 1,
          content: '<?xml version="1.0" encoding="UTF-8"?>\n<atelierDesignSystem version="0.4.0">\n  <palette mode="DualTheme">\n    <token name="canvas_bg" day="#F5F7FA" night="#0D1420" />\n    <token name="canvas_primary" day="#2456C6" night="#8EB4FF" />\n    <token name="canvas_surface" day="#FFFFFF" night="#151E2C" />\n  </palette>\n  <radii unit="dp">\n    <thumbnail>12</thumbnail>\n    <card>16</card>\n    <dialog>20</dialog>\n  </radii>\n</atelierDesignSystem>'
        }
      ];
    }
    function readDocs() {
      try {
        const raw = localStorage.getItem(DOCS_KEY);
        const docs = raw ? JSON.parse(raw) : defaultDocs();
        for (const doc of docs) {
          if (doc && typeof doc.content === 'string' && doc.content.includes('< radii unit="dp">')) {
            doc.content = doc.content.replace('< radii unit="dp">', '<radii unit="dp">');
          }
        }
        return docs;
      } catch (e) {
        return defaultDocs();
      }
    }
    function writeDocs(docs) {
      try { localStorage.setItem(DOCS_KEY, JSON.stringify(docs)); } catch (e) {}
    }
    return {
      listDocuments: async () => readDocs(),
      saveDocument: async (doc) => {
        const docs = readDocs();
        const id = doc.id || ('doc-' + Date.now() + '-' + Math.random().toString(36).slice(2, 7));
        const idx = docs.findIndex(d => d.id === id);
        const record = {
          id,
          title: String(doc.title || '未命名画布.html').trim() || '未命名画布.html',
          content: String(doc.content ?? ''),
          updatedAt: Date.now(),
          revision: idx >= 0 ? (docs[idx].revision || 0) + 1 : 1
        };
        if (idx >= 0) docs[idx] = record;
        else docs.unshift(record);
        writeDocs(docs);
        return record;
      },
      deleteDocuments: async (ids) => {
        const set = new Set(Array.isArray(ids) ? ids : [ids]);
        const next = readDocs().filter(d => !set.has(d.id));
        writeDocs(next);
        return next;
      },
      importFiles: () => new Promise((resolve) => {
        const input = document.createElement('input');
        input.type = 'file';
        input.multiple = true;
        input.accept = '.html,.htm,.svg,.xml,.txt,.md,.js,.css,.json';
        input.onchange = async () => {
          const files = Array.from(input.files || []);
          const docs = readDocs();
          const imported = [];
          for (const f of files) {
            const text = await f.text();
            const rec = { id: 'imp-' + Date.now() + '-' + Math.random().toString(36).slice(2, 6), title: f.name, content: text, updatedAt: Date.now(), revision: 1 };
            docs.unshift(rec);
            imported.push(rec);
          }
          writeDocs(docs);
          resolve(imported);
        };
        input.click();
      }),
      exportFile: async (doc) => {
        const blob = new Blob([doc.content || ''], { type: 'text/plain;charset=utf-8' });
        const a = document.createElement('a');
        a.href = URL.createObjectURL(blob);
        a.download = doc.title || 'canvas.html';
        a.click();
        URL.revokeObjectURL(a.href);
        return true;
      },
      readClipboard: async () => {
        try { return await navigator.clipboard.readText(); } catch (e) { return ''; }
      },
      writeClipboard: async (text) => {
        try { await navigator.clipboard.writeText(text); return true; } catch (e) { return false; }
      },
      getPrefs: async () => {
        const defaults = {
          theme: 'system',
          viewMode: 'split',
          clipboardAutoDetect: true,
          clipboardFloatEnabled: true,
          globalHotkeyEnabled: true,
          closeToTray: true,
          launchAtLogin: false,
          autoCheckUpdate: true,
          alwaysOnTop: false,
          showGrid: false,
          wordWrap: true,
          fontSize: 13,
          splitRatio: 0.46,
          systemDark: window.matchMedia('(prefers-color-scheme: dark)').matches
        };
        try { return Object.assign(defaults, JSON.parse(localStorage.getItem(PREFS_KEY) || '{}')); } catch (e) { return defaults; }
      },
      setPrefs: async (patch) => {
        const cur = await Bridge.getPrefs();
        const next = Object.assign(cur, patch || {});
        try { localStorage.setItem(PREFS_KEY, JSON.stringify(next)); } catch (e) {}
        return next;
      },
      openStorageDir: async () => {},
      checkForUpdate: async () => {
        const urls = [
          'https://relay.zhuquan.xyz/dl/codecanvas-desktop-latest.json?t=' + Date.now(),
          'https://raw.githubusercontent.com/zhuquan7237/code-canvas/main/docs/update/desktop-latest.json?t=' + Date.now()
        ];
        for (const u of urls) {
          try {
            const res = await fetch(u, { cache: 'no-store' });
            if (res.ok) {
              const text = await res.text();
              return Core.parseDesktopUpdateManifest(text, Core.CURRENT_VERSION_CODE);
            }
          } catch (e) {}
        }
        return { status: 'error', message: '无法连接更新源，请检查网络后重试' };
      },
      installUpdate: async (info) => {
        if (info && info.setupUrl) {
          window.open(info.setupUrl, '_blank', 'noopener');
          return { ok: true, mode: 'browser' };
        }
        return { ok: false, message: '未找到安装包链接' };
      }
    };
  }

  let allDocs = [];
  let filteredDocs = [];
  let currentDoc = null;
  let activeFilter = 'ALL';
  let prefs = {
    theme: 'system',
    viewMode: 'split',
    clipboardAutoDetect: true,
    clipboardFloatEnabled: true,
    globalHotkeyEnabled: true,
    closeToTray: true,
    launchAtLogin: false,
    autoCheckUpdate: true,
    alwaysOnTop: false,
    showGrid: false,
    wordWrap: true,
    fontSize: 13,
    splitRatio: 0.46,
    systemDark: true
  };
  let latestUpdateInfo = null;
  let lastHandledClipHash = '';
  let bannerCandidateText = '';
  let saveTimer = null;
  let previewTimer = null;

  // DOM Elements
  const sidebar = document.getElementById('sidebar');
  const btnToggleSidebar = document.getElementById('btnToggleSidebar');
  const btnExpandSidebar = document.getElementById('btnExpandSidebar');
  const catalogCountLabel = document.getElementById('catalogCountLabel');
  const inputSearch = document.getElementById('inputSearch');
  const filterPills = document.getElementById('filterPills');
  const docList = document.getElementById('docList');
  const currentDocTitle = document.getElementById('currentDocTitle');
  const currentKindBadge = document.getElementById('currentKindBadge');
  const saveStatus = document.getElementById('saveStatus');
  const workspace = document.getElementById('workspace');
  const paneSplitter = document.getElementById('paneSplitter');
  const editorSurface = document.getElementById('editorSurface');
  const codeEditor = document.getElementById('codeEditor');
  const codeHighlight = document.getElementById('codeHighlight');
  const lineGutter = document.getElementById('lineGutter');
  const codeStats = document.getElementById('codeStats');
  const fontSizeLabel = document.getElementById('fontSizeLabel');
  const btnWordWrap = document.getElementById('btnWordWrap');
  const btnFormatCode = document.getElementById('btnFormatCode');
  const btnToggleFind = document.getElementById('btnToggleFind');
  const findBar = document.getElementById('findBar');
  const findInput = document.getElementById('findInput');
  const replaceInput = document.getElementById('replaceInput');
  const findCountLabel = document.getElementById('findCountLabel');
  const previewHeaderLabel = document.getElementById('previewHeaderLabel');
  const btnToggleGrid = document.getElementById('btnToggleGrid');
  const previewCard = document.getElementById('previewCard');
  const previewFrame = document.getElementById('previewFrame');
  const clipboardBanner = document.getElementById('clipboardBanner');
  const bannerSnippet = document.getElementById('bannerSnippet');
  const modalBackdrop = document.getElementById('modalBackdrop');
  const modalTitle = document.getElementById('modalTitle');
  const modalSubtitle = document.getElementById('modalSubtitle');
  const modalBody = document.getElementById('modalBody');
  const modalCancelBtn = document.getElementById('modalCancelBtn');
  const modalConfirmBtn = document.getElementById('modalConfirmBtn');

  function isDarkActive() {
    if (prefs.theme === 'dark') return true;
    if (prefs.theme === 'light') return false;
    return Boolean(prefs.systemDark);
  }

  function applyEditorPrefsUi() {
    const wrap = prefs.wordWrap !== false;
    editorSurface.classList.toggle('wrap-enabled', wrap);
    btnWordWrap.classList.toggle('active', wrap);

    const size = Math.min(20, Math.max(11, Number(prefs.fontSize) || 13));
    editorSurface.style.setProperty('--editor-font-size', size + 'px');
    fontSizeLabel.textContent = size + 'px';

    const ratio = Math.min(0.74, Math.max(0.26, Number(prefs.splitRatio) || 0.46));
    workspace.style.setProperty('--code-pane-width', Math.round(ratio * 100) + '%');
  }

  function applyThemeUi() {
    const dark = isDarkActive();
    document.documentElement.setAttribute('data-theme', dark ? 'dark' : 'light');
    document.querySelectorAll('[data-theme-mode]').forEach(btn => {
      btn.classList.toggle('active', btn.dataset.themeMode === prefs.theme);
    });
    document.getElementById('btnPinTop').classList.toggle('active', Boolean(prefs.alwaysOnTop));
    btnToggleGrid.classList.toggle('active', Boolean(prefs.showGrid));
    previewCard.classList.toggle('grid-bg', Boolean(prefs.showGrid));
    applyEditorPrefsUi();
    renderPreviewNow();
  }

  function applyViewModeUi(mode) {
    prefs.viewMode = mode;
    workspace.classList.remove('split', 'code-only', 'preview-only');
    if (mode === 'code') workspace.classList.add('code-only');
    else if (mode === 'preview') workspace.classList.add('preview-only');
    else workspace.classList.add('split');

    document.querySelectorAll('[data-view-mode]').forEach(btn => {
      btn.classList.toggle('active', btn.dataset.viewMode === mode);
    });
  }

  function applyFilters() {
    const q = (inputSearch.value || '').trim().toLowerCase();
    filteredDocs = allDocs.filter(doc => {
      const kind = Core.detectRenderKind(doc.title, doc.content);
      if (activeFilter !== 'ALL' && kind !== activeFilter) return false;
      if (q && !doc.title.toLowerCase().includes(q) && !(doc.content || '').toLowerCase().includes(q)) {
        return false;
      }
      return true;
    });

    const filterLabel = activeFilter === 'ALL' ? '全部作品' : activeFilter;
    catalogCountLabel.textContent = `${filterLabel} · ${filteredDocs.length}`;
    renderDocList();
  }

  function renderDocList() {
    docList.innerHTML = '';
    for (const doc of filteredDocs) {
      const kind = Core.detectRenderKind(doc.title, doc.content);
      const item = document.createElement('div');
      item.className = 'doc-item' + (currentDoc && currentDoc.id === doc.id ? ' active' : '');
      const sizeText = Core.formatBytes(new Blob([doc.content || '']).size);
      const thumbHtml = Core.buildThumbnailMarkup(doc, kind);
      item.innerHTML = `
        <div class="doc-thumb">${thumbHtml}</div>
        <div class="doc-meta">
          <div class="doc-name"></div>
          <div class="doc-sub">${kind} · ${sizeText}</div>
        </div>
      `;
      item.querySelector('.doc-name').textContent = doc.title;
      item.addEventListener('click', () => selectDocument(doc));
      docList.appendChild(item);
    }
  }

  function selectDocument(doc) {
    if (!doc) return;
    currentDoc = doc;
    currentDocTitle.textContent = doc.title;
    const kind = Core.detectRenderKind(doc.title, doc.content);
    currentKindBadge.textContent = kind;
    codeEditor.value = doc.content || '';
    updateGutterAndStats();
    renderPreviewNow();
    renderDocList();
  }

  function updateGutterAndStats() {
    const text = codeEditor.value || '';
    const lines = text.length === 0 ? 1 : text.split(/\r\n|\r|\n/).length;
    codeStats.textContent = `${lines} 行 · ${text.length} 字符`;
    const nums = [];
    for (let i = 1; i <= lines; i++) nums.push(i);
    lineGutter.textContent = nums.join('\n');
    codeHighlight.innerHTML = Core.highlightMarkup(text) + '\n';
    syncEditorScroll();
  }

  function syncEditorScroll() {
    lineGutter.scrollTop = codeEditor.scrollTop;
    codeHighlight.scrollTop = codeEditor.scrollTop;
    codeHighlight.scrollLeft = codeEditor.scrollLeft;
  }

  function renderPreviewNow() {
    if (!currentDoc) return;
    const content = codeEditor.value || '';
    const kind = Core.detectRenderKind(currentDoc.title, content);
    currentKindBadge.textContent = kind;

    // Context-aware preview pane header
    if (kind === Core.RenderKind.XML) {
      previewHeaderLabel.textContent = 'XML 交互结构树 · 防 XXE 沙箱';
      btnToggleGrid.classList.add('hidden');
      previewCard.classList.remove('grid-bg');
    } else {
      previewHeaderLabel.textContent = kind === Core.RenderKind.SVG ? 'SVG 矢量画布 · 自适应比例' : '实时渲染画布 · 安全沙箱';
      btnToggleGrid.classList.remove('hidden');
      previewCard.classList.toggle('grid-bg', Boolean(prefs.showGrid));
    }

    const html = Core.buildPreviewDocument(content, kind, { isDark: isDarkActive() });
    previewFrame.srcdoc = html;
  }

  function scheduleSaveAndPreview() {
    if (!currentDoc) return;
    currentDoc.content = codeEditor.value;
    saveStatus.textContent = '保存中…';
    clearTimeout(previewTimer);
    previewTimer = setTimeout(renderPreviewNow, 120);

    clearTimeout(saveTimer);
    saveTimer = setTimeout(async () => {
      if (!currentDoc) return;
      const saved = await Bridge.saveDocument({
        id: currentDoc.id,
        title: currentDoc.title,
        content: codeEditor.value
      });
      currentDoc.updatedAt = saved.updatedAt;
      currentDoc.revision = saved.revision;
      const idx = allDocs.findIndex(d => d.id === saved.id);
      if (idx >= 0) allDocs[idx] = saved;
      saveStatus.textContent = '已自动保存';
      applyFilters();
    }, 280);
  }

  function showInputModal(title, subtitle, defaultValue, onConfirm) {
    modalTitle.textContent = title;
    modalSubtitle.textContent = subtitle || '';
    modalBody.innerHTML = `<input type="text" class="modal-input" id="modalTextInput" />`;
    const input = document.getElementById('modalTextInput');
    input.value = defaultValue || '';
    modalBackdrop.classList.remove('hidden');
    setTimeout(() => {
      input.focus();
      input.select();
    }, 20);

    const close = () => modalBackdrop.classList.add('hidden');
    modalCancelBtn.onclick = close;
    modalBackdrop.onclick = (e) => {
      if (e.target === modalBackdrop) close();
    };
    const submit = () => {
      const val = input.value.trim();
      if (!val) return;
      close();
      onConfirm(val);
    };
    modalConfirmBtn.onclick = submit;
    input.onkeydown = (e) => {
      if (e.key === 'Enter') submit();
      if (e.key === 'Escape') close();
    };
  }

  async function quickPasteAndPreview(rawOverride) {
    const rawText = typeof rawOverride === 'string' ? rawOverride : await Bridge.readClipboard();
    if (!rawText || !rawText.trim()) return;
    lastHandledClipHash = String(rawText.length) + ':' + rawText.slice(0, 64);
    clipboardBanner.classList.add('hidden');

    const blocks = Core.extractCodeBlocks(rawText);
    const code = blocks.length > 0 ? blocks[0].code : (Core.extractMarkupRegion(rawText) || rawText);
    const lang = blocks.length > 0 ? blocks[0].language : '';
    const ext = Core.inferExtension(code, lang);
    const stamp = new Date().toTimeString().slice(0, 5).replace(':', '');
    const saved = await Bridge.saveDocument({
      title: `快速画布_${stamp}${ext}`,
      content: code
    });
    allDocs.unshift(saved);
    applyFilters();
    selectDocument(saved);
  }

  async function checkClipboardOffer() {
    if (!prefs.clipboardAutoDetect) return;
    const raw = await Bridge.readClipboard();
    if (!raw) return;
    const sig = String(raw.length) + ':' + raw.slice(0, 64);
    if (sig === lastHandledClipHash) return;
    if (!Core.looksLikeClipboardCode(raw)) return;
    bannerCandidateText = raw;
    const oneLine = raw.replace(/\s+/g, ' ').trim();
    bannerSnippet.textContent = oneLine.length > 72 ? oneLine.slice(0, 72) + '…' : oneLine;
    clipboardBanner.classList.remove('hidden');
  }

  // Inline Find & Replace
  function toggleFindBar(forceOpen) {
    const show = typeof forceOpen === 'boolean' ? forceOpen : findBar.classList.contains('hidden');
    findBar.classList.toggle('hidden', !show);
    btnToggleFind.classList.toggle('active', show);
    if (show) {
      findInput.focus();
      findInput.select();
      updateFindCount();
    } else {
      codeEditor.focus();
    }
  }

  function updateFindCount() {
    const needle = findInput.value;
    if (!needle) {
      findCountLabel.textContent = '';
      return;
    }
    const hay = codeEditor.value || '';
    let count = 0;
    let pos = 0;
    while ((pos = hay.indexOf(needle, pos)) !== -1) {
      count++;
      pos += needle.length;
    }
    findCountLabel.textContent = count > 0 ? `${count} 处匹配` : '无匹配';
  }

  function findNextInEditor() {
    const needle = findInput.value;
    if (!needle) return;
    const hay = codeEditor.value || '';
    const startFrom = codeEditor.selectionEnd || 0;
    let idx = hay.indexOf(needle, startFrom);
    if (idx === -1) idx = hay.indexOf(needle, 0);
    if (idx !== -1) {
      codeEditor.focus();
      codeEditor.setSelectionRange(idx, idx + needle.length);
    }
    updateFindCount();
  }

  function replaceOneInEditor() {
    const needle = findInput.value;
    if (!needle) return;
    const replacement = replaceInput.value || '';
    const start = codeEditor.selectionStart;
    const end = codeEditor.selectionEnd;
    const hay = codeEditor.value || '';
    if (hay.slice(start, end) === needle) {
      codeEditor.value = hay.slice(0, start) + replacement + hay.slice(end);
      codeEditor.setSelectionRange(start, start + replacement.length);
      updateGutterAndStats();
      scheduleSaveAndPreview();
    }
    findNextInEditor();
  }

  function replaceAllInEditor() {
    const needle = findInput.value;
    if (!needle) return;
    const replacement = replaceInput.value || '';
    const hay = codeEditor.value || '';
    if (!hay.includes(needle)) return;
    codeEditor.value = hay.split(needle).join(replacement);
    updateGutterAndStats();
    scheduleSaveAndPreview();
    updateFindCount();
  }

  // Draggable Splitter Logic
  function initSplitter() {
    let dragging = false;
    paneSplitter.addEventListener('mousedown', (e) => {
      e.preventDefault();
      dragging = true;
      paneSplitter.classList.add('dragging');
      document.body.style.cursor = 'col-resize';
      previewFrame.style.pointerEvents = 'none';
    });

    window.addEventListener('mousemove', (e) => {
      if (!dragging) return;
      const rect = workspace.getBoundingClientRect();
      if (rect.width <= 0) return;
      const relX = e.clientX - rect.left;
      const ratio = Math.min(0.74, Math.max(0.26, relX / rect.width));
      prefs.splitRatio = ratio;
      workspace.style.setProperty('--code-pane-width', Math.round(ratio * 100) + '%');
    });

    window.addEventListener('mouseup', async () => {
      if (!dragging) return;
      dragging = false;
      paneSplitter.classList.remove('dragging');
      document.body.style.cursor = '';
      previewFrame.style.pointerEvents = '';
      prefs = await Bridge.setPrefs({ splitRatio: prefs.splitRatio });
    });

    paneSplitter.addEventListener('dblclick', async () => {
      prefs.splitRatio = 0.46;
      workspace.style.setProperty('--code-pane-width', '46%');
      prefs = await Bridge.setPrefs({ splitRatio: 0.46 });
    });
  }

  // Settings & Sync Update Modal Elements
  const settingsBackdrop = document.getElementById('settingsBackdrop');
  const btnOpenSettings = document.getElementById('btnOpenSettings');
  const btnCloseSettings = document.getElementById('btnCloseSettings');
  const btnDoneSettings = document.getElementById('btnDoneSettings');
  const chkClipboardFloat = document.getElementById('chkClipboardFloat');
  const chkGlobalHotkey = document.getElementById('chkGlobalHotkey');
  const chkCloseToTray = document.getElementById('chkCloseToTray');
  const chkLaunchAtLogin = document.getElementById('chkLaunchAtLogin');
  const chkAutoUpdate = document.getElementById('chkAutoUpdate');
  const updateStatusTitle = document.getElementById('updateStatusTitle');
  const updateStatusSub = document.getElementById('updateStatusSub');
  const btnCheckUpdate = document.getElementById('btnCheckUpdate');
  const btnInstallUpdate = document.getElementById('btnInstallUpdate');
  const updateBadgeDot = document.getElementById('updateBadgeDot');

  function syncSettingsUi() {
    if (!chkClipboardFloat) return;
    chkClipboardFloat.checked = prefs.clipboardFloatEnabled !== false;
    chkGlobalHotkey.checked = prefs.globalHotkeyEnabled !== false;
    chkCloseToTray.checked = prefs.closeToTray !== false;
    chkLaunchAtLogin.checked = Boolean(prefs.launchAtLogin);
    chkAutoUpdate.checked = prefs.autoCheckUpdate !== false;
  }

  function openSettingsModal() {
    syncSettingsUi();
    settingsBackdrop.classList.remove('hidden');
  }

  function closeSettingsModal() {
    settingsBackdrop.classList.add('hidden');
  }

  async function runUpdateCheck(silentIfUpToDate) {
    if (updateStatusSub) updateStatusSub.textContent = '正在同步检查 GitHub 与国内高速镜像更新…';
    if (btnCheckUpdate) btnCheckUpdate.disabled = true;
    try {
      let result = null;
      if (typeof Bridge.checkForUpdate === 'function') {
        result = await Bridge.checkForUpdate();
      }
      if (!result || result.status === 'error') {
        const urls = [
          'https://relay.zhuquan.xyz/dl/codecanvas-desktop-latest.json?t=' + Date.now(),
          'https://raw.githubusercontent.com/zhuquan7237/code-canvas/main/docs/update/desktop-latest.json?t=' + Date.now()
        ];
        for (const u of urls) {
          try {
            const res = await fetch(u, { cache: 'no-store' });
            if (res.ok) {
              const txt = await res.text();
              result = Core.parseDesktopUpdateManifest(txt, Core.CURRENT_VERSION_CODE);
              if (result && result.status === 'ok') break;
            }
          } catch (e) {}
        }
      }
      if (!result || result.status !== 'ok') {
        if (updateStatusTitle) updateStatusTitle.textContent = `当前版本 v${Core.CURRENT_VERSION || '0.5.0'}（原生超轻量引擎）`;
        if (updateStatusSub) updateStatusSub.textContent = (result && result.message) || '已与 GitHub 保持最新同步';
        if (btnInstallUpdate) btnInstallUpdate.classList.add('hidden');
        return;
      }
      latestUpdateInfo = result;
      if (result.hasUpdate) {
        if (updateBadgeDot) updateBadgeDot.classList.remove('hidden');
        if (updateStatusTitle) {
          updateStatusTitle.textContent = `✨ 发现新版本 v${result.versionName} (${Core.formatBytes(result.fileSizeBytes || 0)})`;
          updateStatusTitle.style.color = 'var(--canvas-primary)';
        }
        if (updateStatusSub) {
          updateStatusSub.textContent = result.changelog || '包含性能与体验优化，点击右侧按钮一键同步升级。';
        }
        if (btnInstallUpdate) {
          btnInstallUpdate.textContent = `一键同步升级 v${result.versionName}`;
          btnInstallUpdate.classList.remove('hidden');
        }
        if (!silentIfUpToDate) openSettingsModal();
      } else {
        if (updateBadgeDot) updateBadgeDot.classList.add('hidden');
        if (updateStatusTitle) {
          updateStatusTitle.textContent = `✓ 当前已是最新版本 v${Core.CURRENT_VERSION || '0.5.0'}（原生超轻量引擎）`;
          updateStatusTitle.style.color = '';
        }
        if (updateStatusSub) {
          updateStatusSub.textContent = '已与 GitHub Release 及国内高速源同步一致';
        }
        if (btnInstallUpdate) btnInstallUpdate.classList.add('hidden');
      }
    } finally {
      if (btnCheckUpdate) btnCheckUpdate.disabled = false;
    }
  }

  async function triggerInstallUpdate() {
    if (!latestUpdateInfo || !latestUpdateInfo.setupUrl) return;
    if (btnInstallUpdate) {
      btnInstallUpdate.disabled = true;
      btnInstallUpdate.textContent = '正在下载并校验更新包…';
    }
    if (updateStatusSub) {
      updateStatusSub.textContent = '正在高速拉取更新包并准备自动重启升级…';
    }
    try {
      if (typeof Bridge.installUpdate === 'function') {
        const res = await Bridge.installUpdate(latestUpdateInfo);
        if (res && res.ok) {
          if (updateStatusSub) updateStatusSub.textContent = '更新包已就绪，正在启动安装程序…';
          return;
        }
      }
      window.open(latestUpdateInfo.setupUrl, '_blank', 'noopener');
      if (updateStatusSub) updateStatusSub.textContent = '已开始下载更新安装包，完成后运行即可覆盖升级。';
    } finally {
      if (btnInstallUpdate) {
        btnInstallUpdate.disabled = false;
        btnInstallUpdate.textContent = `一键同步升级 v${latestUpdateInfo.versionName}`;
      }
    }
  }

  // Expose hooks for Native C# Host (Global Hotkey Alt+V / Floating Toast Click / Tray Menu)
  window.__canvasNativeTriggerQuickPaste = (rawText) => {
    quickPasteAndPreview(rawText);
  };
  window.__canvasNativeOpenSettings = () => {
    openSettingsModal();
  };
  window.__canvasNativeCheckUpdate = () => {
    openSettingsModal();
    runUpdateCheck(false);
  };

  async function init() {
    prefs = await Bridge.getPrefs();
    allDocs = await Bridge.listDocuments();
    applyViewModeUi(prefs.viewMode || 'split');
    applyThemeUi();
    syncSettingsUi();
    applyFilters();
    initSplitter();
    if (allDocs.length > 0) {
      selectDocument(allDocs[0]);
    }
    checkClipboardOffer();
    if (prefs.autoCheckUpdate !== false) {
      setTimeout(() => runUpdateCheck(true), 1200);
    }
  }

  // Event Listeners
  if (btnOpenSettings) btnOpenSettings.addEventListener('click', openSettingsModal);
  if (btnCloseSettings) btnCloseSettings.addEventListener('click', closeSettingsModal);
  if (btnDoneSettings) btnDoneSettings.addEventListener('click', closeSettingsModal);
  if (settingsBackdrop) {
    settingsBackdrop.addEventListener('click', (e) => {
      if (e.target === settingsBackdrop) closeSettingsModal();
    });
  }
  if (chkClipboardFloat) {
    chkClipboardFloat.addEventListener('change', async () => {
      prefs = await Bridge.setPrefs({ clipboardFloatEnabled: chkClipboardFloat.checked });
    });
  }
  if (chkGlobalHotkey) {
    chkGlobalHotkey.addEventListener('change', async () => {
      prefs = await Bridge.setPrefs({ globalHotkeyEnabled: chkGlobalHotkey.checked });
    });
  }
  if (chkCloseToTray) {
    chkCloseToTray.addEventListener('change', async () => {
      prefs = await Bridge.setPrefs({ closeToTray: chkCloseToTray.checked });
    });
  }
  if (chkLaunchAtLogin) {
    chkLaunchAtLogin.addEventListener('change', async () => {
      prefs = await Bridge.setPrefs({ launchAtLogin: chkLaunchAtLogin.checked });
    });
  }
  if (chkAutoUpdate) {
    chkAutoUpdate.addEventListener('change', async () => {
      prefs = await Bridge.setPrefs({ autoCheckUpdate: chkAutoUpdate.checked });
    });
  }
  if (btnCheckUpdate) {
    btnCheckUpdate.addEventListener('click', () => runUpdateCheck(false));
  }
  if (btnInstallUpdate) {
    btnInstallUpdate.addEventListener('click', triggerInstallUpdate);
  }

  codeEditor.addEventListener('input', () => {
    updateGutterAndStats();
    scheduleSaveAndPreview();
    if (!findBar.classList.contains('hidden')) updateFindCount();
  });

  codeEditor.addEventListener('scroll', syncEditorScroll);

  codeEditor.addEventListener('keydown', (e) => {
    if (e.key === 'Tab') {
      e.preventDefault();
      const start = codeEditor.selectionStart;
      const end = codeEditor.selectionEnd;
      const val = codeEditor.value;
      codeEditor.value = val.slice(0, start) + '  ' + val.slice(end);
      codeEditor.setSelectionRange(start + 2, start + 2);
      updateGutterAndStats();
      scheduleSaveAndPreview();
    }
  });

  btnWordWrap.addEventListener('click', async () => {
    prefs = await Bridge.setPrefs({ wordWrap: !(prefs.wordWrap !== false) });
    applyEditorPrefsUi();
  });

  btnFormatCode.addEventListener('click', () => {
    if (!currentDoc) return;
    const kind = Core.detectRenderKind(currentDoc.title, codeEditor.value);
    codeEditor.value = Core.formatMarkup(codeEditor.value, kind);
    updateGutterAndStats();
    scheduleSaveAndPreview();
    saveStatus.textContent = '已格式化代码';
  });

  btnToggleFind.addEventListener('click', () => toggleFindBar());
  document.getElementById('btnCloseFind').addEventListener('click', () => toggleFindBar(false));
  findInput.addEventListener('input', updateFindCount);
  findInput.addEventListener('keydown', (e) => {
    if (e.key === 'Enter') findNextInEditor();
    if (e.key === 'Escape') toggleFindBar(false);
  });
  replaceInput.addEventListener('keydown', (e) => {
    if (e.key === 'Enter') replaceOneInEditor();
    if (e.key === 'Escape') toggleFindBar(false);
  });
  document.getElementById('btnFindNext').addEventListener('click', findNextInEditor);
  document.getElementById('btnReplaceOne').addEventListener('click', replaceOneInEditor);
  document.getElementById('btnReplaceAll').addEventListener('click', replaceAllInEditor);

  document.getElementById('btnFontDec').addEventListener('click', async () => {
    const nextSize = Math.max(11, (Number(prefs.fontSize) || 13) - 1);
    prefs = await Bridge.setPrefs({ fontSize: nextSize });
    applyEditorPrefsUi();
  });

  document.getElementById('btnFontInc').addEventListener('click', async () => {
    const nextSize = Math.min(20, (Number(prefs.fontSize) || 13) + 1);
    prefs = await Bridge.setPrefs({ fontSize: nextSize });
    applyEditorPrefsUi();
  });

  inputSearch.addEventListener('input', applyFilters);

  filterPills.addEventListener('click', (e) => {
    const pill = e.target.closest('.filter-pill');
    if (!pill) return;
    activeFilter = pill.dataset.filter;
    filterPills.querySelectorAll('.filter-pill').forEach(p => p.classList.toggle('active', p === pill));
    applyFilters();
  });

  document.querySelectorAll('[data-view-mode]').forEach(btn => {
    btn.addEventListener('click', async () => {
      applyViewModeUi(btn.dataset.viewMode);
      prefs = await Bridge.setPrefs({ viewMode: btn.dataset.viewMode });
    });
  });

  document.querySelectorAll('[data-theme-mode]').forEach(btn => {
    btn.addEventListener('click', async () => {
      prefs = await Bridge.setPrefs({ theme: btn.dataset.themeMode });
      applyThemeUi();
    });
  });

  btnToggleSidebar.addEventListener('click', () => {
    sidebar.classList.add('collapsed');
    btnExpandSidebar.classList.remove('hidden');
  });

  btnExpandSidebar.addEventListener('click', () => {
    sidebar.classList.remove('collapsed');
    btnExpandSidebar.classList.add('hidden');
  });

  document.getElementById('btnQuickPaste').addEventListener('click', () => quickPasteAndPreview());

  document.getElementById('btnNewDoc').addEventListener('click', () => {
    const stamp = new Date().toTimeString().slice(0, 5).replace(':', '');
    showInputModal('新建画布文件', '输入文件名（支持 .html / .svg / .xml / .txt）', `新建画布_${stamp}.html`, async (title) => {
      const saved = await Bridge.saveDocument({ title, content: '' });
      allDocs.unshift(saved);
      applyFilters();
      selectDocument(saved);
      codeEditor.focus();
    });
  });

  document.getElementById('titleEditTrigger').addEventListener('click', () => {
    if (!currentDoc) return;
    showInputModal('重命名画布', '修改文件名称与后缀会立即更新渲染模式', currentDoc.title, async (newTitle) => {
      const saved = await Bridge.saveDocument({
        id: currentDoc.id,
        title: newTitle,
        content: codeEditor.value
      });
      const idx = allDocs.findIndex(d => d.id === saved.id);
      if (idx >= 0) allDocs[idx] = saved;
      applyFilters();
      selectDocument(saved);
    });
  });

  document.getElementById('btnImportFile').addEventListener('click', async () => {
    const imported = await Bridge.importFiles();
    if (imported && imported.length > 0) {
      allDocs = await Bridge.listDocuments();
      applyFilters();
      selectDocument(imported[0]);
    }
  });

  document.getElementById('btnExportDoc').addEventListener('click', async () => {
    if (!currentDoc) return;
    await Bridge.exportFile({ title: currentDoc.title, content: codeEditor.value });
  });

  document.getElementById('btnCopyCode').addEventListener('click', async () => {
    await Bridge.writeClipboard(codeEditor.value || '');
    saveStatus.textContent = '已复制源码';
    setTimeout(() => { saveStatus.textContent = '已自动保存'; }, 1500);
  });

  document.getElementById('btnRefreshPreview').addEventListener('click', renderPreviewNow);

  document.getElementById('btnPinTop').addEventListener('click', async () => {
    prefs = await Bridge.setPrefs({ alwaysOnTop: !prefs.alwaysOnTop });
    applyThemeUi();
  });

  btnToggleGrid.addEventListener('click', async () => {
    prefs = await Bridge.setPrefs({ showGrid: !prefs.showGrid });
    applyThemeUi();
  });

  document.getElementById('btnDeleteDoc').addEventListener('click', async () => {
    if (!currentDoc) return;
    allDocs = await Bridge.deleteDocuments([currentDoc.id]);
    applyFilters();
    selectDocument(filteredDocs[0] || allDocs[0] || null);
  });

  document.getElementById('btnOpenStorage').addEventListener('click', () => {
    Bridge.openStorageDir();
  });

  document.getElementById('btnBannerImport').addEventListener('click', () => {
    if (bannerCandidateText) quickPasteAndPreview(bannerCandidateText);
  });

  document.getElementById('btnBannerDismiss').addEventListener('click', () => {
    if (bannerCandidateText) {
      lastHandledClipHash = String(bannerCandidateText.length) + ':' + bannerCandidateText.slice(0, 64);
    }
    clipboardBanner.classList.add('hidden');
  });

  window.addEventListener('focus', checkClipboardOffer);

  window.addEventListener('keydown', (e) => {
    if ((e.ctrlKey || e.metaKey) && e.shiftKey && e.key.toLowerCase() === 'v') {
      e.preventDefault();
      quickPasteAndPreview();
    } else if (e.altKey && !e.ctrlKey && e.key.toLowerCase() === 'v') {
      e.preventDefault();
      quickPasteAndPreview();
    } else if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'b') {
      e.preventDefault();
      const collapsed = sidebar.classList.toggle('collapsed');
      btnExpandSidebar.classList.toggle('hidden', !collapsed);
    } else if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'r') {
      e.preventDefault();
      renderPreviewNow();
    } else if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'f') {
      e.preventDefault();
      toggleFindBar(true);
    } else if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 's') {
      e.preventDefault();
      if (currentDoc) Bridge.exportFile({ title: currentDoc.title, content: codeEditor.value });
    } else if (e.altKey && !e.ctrlKey && e.key.toLowerCase() === 'z') {
      e.preventDefault();
      btnWordWrap.click();
    } else if (e.shiftKey && e.altKey && e.key.toLowerCase() === 'f') {
      e.preventDefault();
      btnFormatCode.click();
    } else if (e.key === 'Escape' && settingsBackdrop && !settingsBackdrop.classList.contains('hidden')) {
      closeSettingsModal();
    }
  });

  init();
})();
