(function () {
  'use strict';

  const Core = window.CanvasCore;
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
          content: '<!DOCTYPE html>\n<html><head><meta charset="utf-8"><style>body{margin:0;min-height:100vh;display:flex;align-items:center;justify-content:center;background:#0f172a;color:#f8fafc;font-family:system-ui,sans-serif}.card{padding:28px 32px;border-radius:20px;background:#1e293b;border:1px solid #334155;text-align:center}h1{font-size:22px;margin:0 0 10px}p{color:#94a3b8;font-size:13px}</style></head><body><div class="card"><h1>代码画布 · 桌面工作台</h1><p>在左侧编辑代码或按 Ctrl+Shift+V 粘贴 AI 代码，右侧实时预览。</p></div></body></html>'
        },
        {
          id: 'starter-svg-geometry',
          title: '几何黄金螺旋徽章.svg',
          updatedAt: now - 60000,
          revision: 1,
          content: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 320 240"><rect width="320" height="240" rx="24" fill="#0D1420"/><circle cx="160" cy="120" r="72" fill="none" stroke="#38BDF8" stroke-width="3"/><polygon points="160,58 214,152 106,152" fill="none" stroke="#F8FAFC" stroke-width="2.5"/></svg>'
        }
      ];
    }
    function readDocs() {
      try {
        const raw = localStorage.getItem(DOCS_KEY);
        return raw ? JSON.parse(raw) : defaultDocs();
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
        const defaults = { theme: 'system', viewMode: 'split', clipboardAutoDetect: true, alwaysOnTop: false, showGrid: false, systemDark: window.matchMedia('(prefers-color-scheme: dark)').matches };
        try { return Object.assign(defaults, JSON.parse(localStorage.getItem(PREFS_KEY) || '{}')); } catch (e) { return defaults; }
      },
      setPrefs: async (patch) => {
        const cur = await Bridge.getPrefs();
        const next = Object.assign(cur, patch || {});
        try { localStorage.setItem(PREFS_KEY, JSON.stringify(next)); } catch (e) {}
        return next;
      },
      openStorageDir: async () => {}
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
    alwaysOnTop: false,
    showGrid: false,
    systemDark: true
  };
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
  const codeEditor = document.getElementById('codeEditor');
  const lineGutter = document.getElementById('lineGutter');
  const codeStats = document.getElementById('codeStats');
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

  function applyThemeUi() {
    const dark = isDarkActive();
    document.documentElement.setAttribute('data-theme', dark ? 'dark' : 'light');
    document.querySelectorAll('[data-theme-mode]').forEach(btn => {
      btn.classList.toggle('active', btn.dataset.themeMode === prefs.theme);
    });
    document.getElementById('btnPinTop').classList.toggle('active', Boolean(prefs.alwaysOnTop));
    document.getElementById('btnToggleGrid').classList.toggle('active', Boolean(prefs.showGrid));
    previewCard.classList.toggle('grid-bg', Boolean(prefs.showGrid));
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
      item.innerHTML = `
        <div class="doc-thumb">${kind}</div>
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
  }

  function renderPreviewNow() {
    if (!currentDoc) return;
    const content = codeEditor.value || '';
    const kind = Core.detectRenderKind(currentDoc.title, content);
    currentKindBadge.textContent = kind;
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

  async function init() {
    prefs = await Bridge.getPrefs();
    allDocs = await Bridge.listDocuments();
    applyViewModeUi(prefs.viewMode || 'split');
    applyThemeUi();
    applyFilters();
    if (allDocs.length > 0) {
      selectDocument(allDocs[0]);
    }
    checkClipboardOffer();
  }

  // Event Listeners
  codeEditor.addEventListener('input', () => {
    updateGutterAndStats();
    scheduleSaveAndPreview();
  });

  codeEditor.addEventListener('scroll', () => {
    lineGutter.scrollTop = codeEditor.scrollTop;
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

  document.getElementById('btnToggleGrid').addEventListener('click', async () => {
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

  document.getElementById('symbolStrip').addEventListener('click', (e) => {
    const btn = e.target.closest('.sym-btn');
    if (!btn) return;
    const pair = btn.dataset.insert;
    const start = codeEditor.selectionStart;
    const end = codeEditor.selectionEnd;
    const val = codeEditor.value;
    codeEditor.value = val.slice(0, start) + pair + val.slice(end);
    const cursorOffset = pair.length === 2 && pair !== '  ' ? start + 1 : start + pair.length;
    codeEditor.focus();
    codeEditor.setSelectionRange(cursorOffset, cursorOffset);
    updateGutterAndStats();
    scheduleSaveAndPreview();
  });

  window.addEventListener('focus', checkClipboardOffer);

  window.addEventListener('keydown', (e) => {
    if ((e.ctrlKey || e.metaKey) && e.shiftKey && e.key.toLowerCase() === 'v') {
      e.preventDefault();
      quickPasteAndPreview();
    } else if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'b') {
      e.preventDefault();
      const collapsed = sidebar.classList.toggle('collapsed');
      btnExpandSidebar.classList.toggle('hidden', !collapsed);
    } else if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'r') {
      e.preventDefault();
      renderPreviewNow();
    }
  });

  init();
})();
