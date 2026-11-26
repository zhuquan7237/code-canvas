'use strict';

const strings = {
  zh: {
    skip:'跳到工作区',brand:'代码画布',brandTag:'本地优先的代码工作台',local:'仅在本机',offline:'当前离线',ready:'可离线使用',
    install:'安装应用',installHelp:'在 iPhone／iPad Safari 中点「分享 → 添加到主屏幕」。Android Chrome 可打开菜单选择「安装应用」或「添加到主屏幕」。',
    offlineHelp:'首次打开后等待「可离线使用」提示。在同一个浏览器或已安装的网页应用中使用。',
    library:'作品库',new:'新建',import:'导入文件',openDemo:'打开演示作品',shelfNote:'文件保存在这台设备的浏览器中。重要内容请导出备份。',
    saved:'已保存在本机',unsaved:'未能写入本机',rename:'重命名',export:'导出',remove:'删除作品',tabs:'工作区模式',preview:'预览',source:'源码',
    expanded:'展开画布',shrink:'还原画布',frame:'代码预览',sandbox:'隔离预览 · 禁用脚本与联网',textPreview:'纯文本预览 · 不执行代码',
    format:'格式',plain:'纯文本',paste:'粘贴',apply:'应用并预览',sourceEditor:'源码编辑器',sourceNote:'浏览器本地保存；不会上传。',
    webEdition:'网页预览版',close:'关闭',name:'文件名',cancel:'取消',confirm:'确定',newFile:'新建作品',untitled:'未命名',
    demoTitle:'轨道 · 光影练习',blank:'在源码页粘贴代码，或从作品库导入文件。',deleteConfirm:'确定删除《{name}》？这一操作无法撤销。',
    storedLimit:'最多保存 {number} 个作品。请先导出备份，再删除不需要的作品。',
    sizeLimit:'这个文件超过 512 KB，请先缩小文件大小。',fileType:'请导入 HTML、SVG 或文本文件。',importError:'未能读取文件。',
    pasteFallback:'请在源码编辑器中手动粘贴。',exported:'已发起下载。',imported:'已导入到本机。',storageError:'浏览器未能保存文件。请导出备份。',
    emptyList:'暂无作品',emptyTitle:'开始一个新作品',emptyText:'新建、导入文件，或者打开演示作品。',lines:'{number} 行',deleted:'已删除作品。',
    plainTag:'文本',motion:'播放／暂停',characterLimit:'最多支持 524,288 个字符，请缩短作品。'
  },
  en: {
    skip:'Skip to workspace',brand:'Code Canvas',brandTag:'A local-first code workspace',local:'On this device',offline:'Offline',ready:'Offline ready',
    install:'Install app',installHelp:'On iPhone or iPad, open Safari and choose Share → Add to Home Screen. On Android, open the Chrome menu and choose Install app or Add to Home screen.',
    offlineHelp:'Wait for the Offline ready indicator on your first visit. Reopen the same browser or installed web app.',
    library:'Library',new:'New',import:'Import file',openDemo:'Open a sample',shelfNote:'Files are stored in this browser on this device. Export important work to keep a backup.',
    saved:'Saved on device',unsaved:'Not saved on device',rename:'Rename',export:'Export',remove:'Delete document',tabs:'Workspace mode',preview:'Preview',source:'Source',
    expanded:'Expand canvas',shrink:'Restore canvas',frame:'Code preview',sandbox:'Isolated preview · Scripts and network disabled',textPreview:'Plain-text preview · Code is not executed',
    format:'Format',plain:'Plain text',paste:'Paste',apply:'Apply & preview',sourceEditor:'Source editor',sourceNote:'Saved locally in this browser; never uploaded.',
    webEdition:'Web preview edition',close:'Close',name:'File name',cancel:'Cancel',confirm:'Confirm',newFile:'New document',untitled:'Untitled',
    demoTitle:'Orbit · Study in light',blank:'Paste code in the Source tab, or import a file from the library.',deleteConfirm:'Delete “{name}”? This cannot be undone.',
    storedLimit:'You can save up to {number} documents. Export a backup and delete a document first.',
    sizeLimit:'This file exceeds 512 KB. Reduce its size and try again.',fileType:'Import an HTML, SVG, or text file.',importError:'This file could not be read.',
    pasteFallback:'Paste manually in the source editor.',exported:'Download started.',imported:'Imported to this device.',storageError:'The browser could not save this work. Export a backup.',
    emptyList:'No documents yet',emptyTitle:'Start a new document',emptyText:'Create or import a file, or open the sample.',lines:'{number} lines',deleted:'Document deleted.',
    plainTag:'Text',motion:'Play / pause',characterLimit:'The editor accepts up to 524,288 characters. Shorten the document and try again.'
  }
};

const LIMIT = 524288;
const MAX = 8;
const STORAGE = 'code-canvas-web:library:v1';
const TYPES = new Set(['html','svg','text']);
const htmlLike = /^\s*(?:<!doctype\s+html\b|<!--|<\/?(?:html|body|head|style|script|div|main|article|section|h[1-6]|p|span|pre|button|input|img|video|canvas|table|form)\b)/i;
const svgLike = /^\s*(?:<\?xml[^>]*\?>\s*)?(?:<!--[\s\S]*?-->\s*)*<svg(?:\s|>)/i;
const knownExt = /\.(html?|svg|txt|md|css|js|json|xml)$/i;
const $ = id => document.getElementById(id);
const refs = Object.fromEntries(['workspace','document-title','documents','document-count','mobile-count','library-toggle','kind-badge','save-state','tab-preview','tab-code','preview-panel','code-panel','preview-wrap','preview','plain-preview','preview-format','toggle-fit','editor','format-select','source-count','connection','connection-label','status','language-button','file-picker','new-button','import-button','demo-button','remove-button','rename-button','export-button','install-button','install-dialog','name-dialog','name-title','name-input','name-format','name-form','delete-dialog','delete-message','delete-confirm'].map(id => [id,$(id)]));
const get = id => refs[id];
function prefsGet(key) { try { return localStorage.getItem(key); } catch { return null; } }
function prefsSet(key,value) { try { localStorage.setItem(key,value); return true; } catch { return false; } }
const requested = new URLSearchParams(location.search).get('lang');
let lang = requested === 'en' || requested === 'zh' ? requested : prefsGet('code-canvas-web:lang') === 'en' ? 'en' : 'zh';
let tab = 'preview';
let saveOK = true;
let toastTimer, persistTimer;
let installPrompt;
let naming;
let pendingDelete = null;
let renderedKey;
let offlineReady = false;
let previousNameFocus;
let previousDeleteFocus;
const translate = (key, args = {}) => (strings[lang][key] || key).replace(/\{(\w+)\}/g, (_, name) => args[name] ?? '');
const typeTag = format => format === 'text' ? translate('plainTag') : format.toUpperCase();
const makeId = () => globalThis.crypto?.randomUUID?.() || 'id-' + Date.now().toString(36) + '-' + Math.random().toString(36).slice(2);
const validName = value => (typeof value === 'string' ? value.replace(/[\u0000-\u001f\u007f/\\:*?"<>|]/g, ' ').trim().slice(0,80) : '') || translate('untitled');

function sampleSource() {
  return `<!doctype html>
<html>
<head>
<style>
  * { box-sizing: border-box; }
  body { margin:0; color:#fff1e7; font-family:system-ui,sans-serif; background:#20192e; }
  .art { min-height:100vh; padding:clamp(12px,4vw,38px); display:flex;
    flex-direction:column; justify-content:center; align-items:center; gap:12px; background:
    radial-gradient(ellipse at 22% 18%,#612e46 0%,transparent 48%),#20192e; }
  svg { display:block; width:min(100%,820px); height:auto; max-height:calc(100vh - 92px); border-radius:12px; }
  .orbit { transform-origin:510px 300px; animation:turn 24s linear infinite; }
  #motion-switch { position:absolute; opacity:0; width:1px; height:1px; }
  #motion-switch:not(:checked) ~ svg .orbit { animation-play-state:paused; }
  label { border:1px solid #b17f9f; border-radius:999px; padding:10px 16px;
    cursor:pointer; font-size:14px; color:#ffe5dc; }
  #motion-switch:focus-visible + label { outline:2px solid #ffbdac; outline-offset:3px; }
  @keyframes turn { to { transform:rotate(360deg); } }
  @media(prefers-reduced-motion:reduce) { .orbit { animation:none; } }
</style>
</head>
<body>
<main class="art">
  <input id="motion-switch" type="checkbox" checked>
  <label for="motion-switch">${translate("motion")} · CSS</label>
  <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 920 480" role="img" aria-labelledby="art-title">
    <title id="art-title">Coastline orbital study</title>
    <defs>
      <linearGradient id="sky" x1="0" y1="0" x2="1" y2="1">
        <stop stop-color="#562e57"/><stop offset=".58" stop-color="#362647"/><stop offset="1" stop-color="#251f40"/>
      </linearGradient>
      <linearGradient id="planet" x1="0" y1="0" x2="1" y2="1">
        <stop stop-color="#fff0ca"/><stop offset=".43" stop-color="#ffb291"/><stop offset="1" stop-color="#e4667d"/>
      </linearGradient>
      <linearGradient id="ridge" x1="0" y1="0" x2="1" y2="1">
        <stop stop-color="#9f609b"/><stop offset="1" stop-color="#5a395f"/>
      </linearGradient>
      <pattern id="grid" width="40" height="40" patternUnits="userSpaceOnUse" x="1" y="1"><path d="M40 0H0v40" fill="none" stroke="#e8bdd7" stroke-opacity=".14"/></pattern>
    </defs>
    <rect width="920" height="480" fill="url(#sky)"/>
    <rect width="920" height="480" fill="url(#grid)"/>
    <rect x="23" y="23" width="874" height="434" rx="15" stroke="#ebbdce" stroke-opacity=".35" fill="none"/>
    <circle cx="510" cy="265" r="107" fill="url(#planet)"/>
    <ellipse cx="510" cy="265" rx="290" ry="74" fill="none" stroke="#f9c3dd" stroke-width="2" opacity=".75" transform="rotate(-19 510 265)"/>
    <ellipse cx="510" cy="265" rx="244" ry="102" fill="none" stroke="#e8acd2" stroke-width="1.5" opacity=".55" transform="rotate(24 510 265)"/>
    <g class="orbit"><path d="M510 169a131 131 0 1 1 0 262 131 131 0 1 1 0-262Z" fill="none" stroke="#fce4ef" opacity=".32" stroke-dasharray="3 8"/><circle cx="510" cy="169" r="8" fill="#fff1cd"/><circle cx="510" cy="169" r="18" fill="none" stroke="#f9d3e6" opacity=".55"/></g>
    <path d="M0 410Q95 355 202 399T425 388Q546 339 671 391T920 350V480H0Z" fill="url(#ridge)" fill-opacity=".72"/>
    <path d="M0 418Q95 364 202 407T425 396Q546 348 671 400T920 359" fill="none" stroke="#f2aebf" stroke-width="2" stroke-opacity=".75"/>
    <path d="M0 450Q109 419 217 442T460 424Q585 376 710 416T920 393" fill="none" stroke="#ad72ae" stroke-width="2"/>
    <g fill="#fff0e8" font-family="system-ui,sans-serif">
      <text x="54" y="80" font-size="22" letter-spacing="5" fill="#f2b4ca">C A N V A S  /  0 1</text>
      <text id="demo-caption" x="51" y="153" font-size="57" font-weight="750">COASTLINE</text>
      <text x="55" y="192" font-size="22" letter-spacing="2" fill="#eec4dd">ORBITAL STUDY</text>
      <text x="56" y="434" font-size="18" letter-spacing="3">HTML + SVG</text>
      <text x="863" y="435" text-anchor="end" font-size="18" letter-spacing="3" fill="#f2bfd2">LOCAL / 01</text>
    </g>
    <circle cx="803" cy="105" r="4" fill="#fff1db"/><path d="M803 94v22m-11-11h22" stroke="#fff1db" stroke-width="1.5"/>
    <circle cx="742" cy="146" r="3" fill="#eec2e4"/><circle cx="181" cy="275" r="5" fill="#f5cde4"/>
  </svg>
</main>
</body>
</html>`;
}

function makeSample() { return {id:makeId(), name:translate('demoTitle'), format:'html', source:sampleSource(), sample:true}; }
function stateFrom(raw) {
  if (!raw) { const doc = makeSample(); return {docs:[doc], current:doc.id}; }
  try {
    const data = JSON.parse(raw);
    if (!Array.isArray(data.docs)) throw new Error('invalid');
    const seen = new Set();
    const docs = data.docs.filter(d => d && typeof d.id === 'string' && d.id.length <= 100 && !seen.has(d.id) && seen.add(d.id)
      && TYPES.has(d.format) && typeof d.source === 'string' && d.source.length <= LIMIT)
      .slice(0,MAX).map(d => ({id:d.id, name:validName(d.name), format:d.format, source:d.source, sample:d.sample === true}));
    return {docs,current:docs.some(d => d.id === data.current) ? data.current : docs[0]?.id ?? null};
  } catch { const doc = makeSample(); return {docs:[doc], current:doc.id}; }
}
const state = stateFrom(prefsGet(STORAGE));
const current = () => state.docs.find(d => d.id === state.current);
const mobile = matchMedia('(max-width:795px)');
function toast(key,args) {
  get('status').textContent = translate(key,args);
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => { get('status').textContent = ''; },4200);
}
function setSaveState() { get('save-state').textContent = translate(saveOK ? 'saved' : 'unsaved'); }
function save() {
  try { saveOK = prefsSet(STORAGE,JSON.stringify(state)); } catch { saveOK = false; }
  setSaveState();
  return saveOK;
}
function queueSave() { clearTimeout(persistTimer); persistTimer = setTimeout(persist,180); }
function persist() { clearTimeout(persistTimer); persistTimer = undefined; return save(); }
window.addEventListener('pagehide', () => { if (persistTimer !== undefined) persist(); });
function setLibrary(open) {
  get('workspace').dataset.library = open ? 'open' : 'closed';
  get('library-toggle').setAttribute('aria-expanded',open ? 'true' : 'false');
}
function closeMobileLibrary() { if (mobile.matches) setLibrary(false); }
function updateConnection() {
  get('connection').dataset.state = navigator.onLine ? 'online' : 'offline';
  get('connection-label').textContent = translate(navigator.onLine ? offlineReady ? 'ready' : 'local' : 'offline');
}
function l10n() {
  document.documentElement.lang = lang === 'zh' ? 'zh-CN' : 'en';
  document.title = lang === 'zh' ? '代码画布 · Code Canvas' : 'Code Canvas · Local code workspace';
  document.querySelectorAll('[data-text]').forEach(n => { n.textContent = translate(n.dataset.text); });
  document.querySelectorAll('[data-label]').forEach(n => { n.setAttribute('aria-label',translate(n.dataset.label)); });
  document.querySelectorAll('[data-title]').forEach(n => { n.title = translate(n.dataset.title); });
  get('language-button').lang = lang === 'en' ? 'zh-CN' : 'en';
  get('language-button').textContent = lang === 'en' ? '中文' : 'English';
  get('toggle-fit').querySelector('[data-text]').textContent = translate(get('toggle-fit').getAttribute('aria-pressed') === 'true' ? 'shrink' : 'expanded');
  setSaveState(); updateConnection();
}

const BLOCK = new Set(['script','noscript','link','meta','base','iframe','frame','frameset','object','embed','portal','fencedframe','foreignobject','template','handler','discard']);
const URL_ATTR = new Set(['href','src','srcset','imagesrcset','srcdoc','action','formaction','data','poster','background','ping','cite','manifest','longdesc','codebase','classid','profile','archive','target','download','attributionsrc','itemid','itemtype','itemprop','lowsrc','dynsrc','urn']);
const LOCAL_FRAGMENT = /^#[^\s"'<>]*$/;
const INLINE_MEDIA = /^data:(?:image\/(?:png|jpe?g|gif|webp|avif|bmp|x-icon)|audio\/(?:ogg|wav|mp3|mpeg|webm)|video\/(?:mp4|webm|ogg));base64,[a-z0-9+/=\s]+$/i;
const SAFE_ANIMATION = /^(?:[dxy]|opacity|stroke(?:-width|-dashoffset|-dasharray|-opacity)?|fill(?:-opacity)?|(?:cx|cy|r|rx|ry)|(?:x|y)[12]|width|height|viewbox|offset|stop-color|stop-opacity|transform)$/i;
const PAGE_CSP = "default-src 'none'; style-src 'unsafe-inline'; img-src data:; media-src data:; font-src data:; base-uri 'none'; form-action 'none'; object-src 'none'; connect-src 'none'; frame-src 'none'; script-src 'none'";
function isolatedMarkup(source,kind) {
  // Template content is inert while parsing; user markup is never attached to the app DOM.
  const template = document.createElement('template');
  template.innerHTML = source;
  for (const node of template.content.querySelectorAll('*')) {
    const tag = node.localName.toLowerCase();
    if (BLOCK.has(tag) || (['animate','set','animatetransform','animatecolor'].includes(tag) && !SAFE_ANIMATION.test(node.getAttribute('attributeName') || ''))) {
      node.remove();
      continue;
    }
    for (const attr of [...node.attributes]) {
      const k = attr.localName.toLowerCase();
      const val = attr.value.trim();
      if (k.startsWith('on') || k === 'autofocus' || k === 'is' || k === 'xmlns') {
        node.removeAttributeNode(attr);
      } else if (URL_ATTR.has(k)) {
        const isSvgReference = k === 'href' && node.namespaceURI === 'http://www.w3.org/2000/svg'
          && tag !== 'a' && LOCAL_FRAGMENT.test(val);
        const isInlineMedia = ['img','image','audio','video','source'].includes(tag) && ['src','href','poster'].includes(k) && INLINE_MEDIA.test(val);
        if (!isSvgReference && !isInlineMedia) node.removeAttributeNode(attr);
      }
    }
  }
  const markup = template.innerHTML;
  const css = "html{min-height:100%;color-scheme:light dark}body{margin:0;overflow-wrap:anywhere}img,video{max-width:100%}pre{overflow:auto}svg{max-width:100%;height:auto}body.canvas-vector{min-height:100vh;display:grid;place-items:center;padding:10px;box-sizing:border-box}body.canvas-vector>svg{display:block;width:auto;max-width:100%;max-height:calc(100vh - 20px)}";
  return '<!doctype html><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><meta http-equiv="Content-Security-Policy" content="' + PAGE_CSP + '"><style>' + css + '</style><body' + (kind === 'svg' ? ' class="canvas-vector"' : '') + '>' + markup + '</body>';
}

function renderPreview(force=false) {
  const doc = current();
  const key = doc ? doc.format + '\0' + doc.source : 'empty';
  if (!force && renderedKey === key) return;
  renderedKey = key;
  const readable = !doc || doc.format === 'text' || !doc.source.trim();
  get('preview').hidden = readable;
  get('plain-preview').hidden = !readable;
  if (readable) {
    get('preview').removeAttribute('srcdoc');
    get('plain-preview').textContent = doc?.source || (doc ? translate('blank') : translate('emptyText'));
  } else {
    get('plain-preview').textContent = '';
    get('preview').srcdoc = isolatedMarkup(doc.source,doc.format);
  }
  get('preview-panel').querySelector('.stage-foot [data-text]').textContent = translate(readable ? 'textPreview' : 'sandbox');
  get('preview-format').textContent = doc ? typeTag(doc.format) : '';
}
function selectTab(next,moveFocus=false) {
  tab = next;
  for (const name of ['preview','code']) {
    const yes = next === name;
    get('tab-' + name).setAttribute('aria-selected',String(yes));
    get('tab-' + name).tabIndex = yes ? 0 : -1;
    get(name + '-panel').hidden = !yes;
  }
  get('toggle-fit').hidden = next !== 'preview';
  if (next === 'preview') renderPreview();
  if (moveFocus) get('tab-' + next).focus();
}
function syncSourceCount() {
  const n = get('editor').value.split('\n').length;
  get('source-count').textContent = translate('lines',{number:n.toLocaleString(lang)});
}
function renderShelf() {
  const list = get('documents');
  list.replaceChildren();
  get('document-count').textContent = state.docs.length;
  get('mobile-count').textContent = state.docs.length;
  for (const doc of state.docs) {
    const li=document.createElement('li'), button=document.createElement('button'), kind=document.createElement('span'), name=document.createElement('span');
    button.className='doc'; button.type='button';
    button.dataset.docId=doc.id;
    if (doc.id === state.current) button.setAttribute('aria-current','true');
    kind.className='doc-kind'; kind.textContent=typeTag(doc.format);
    name.className='doc-name'; name.textContent=doc.name;
    button.append(kind,name); li.append(button); list.append(li);
  }
}
function renderWorkspace() {
  const doc = current();
  get('document-title').textContent=doc?.name || translate('emptyTitle');
  get('kind-badge').textContent=doc ? typeTag(doc.format) : '—';
  get('editor').value=doc?.source || '';
  get('format-select').value=doc?.format || 'text';
  for (const id of ['editor','format-select','export-button','rename-button','remove-button']) get(id).disabled=!doc;
  for (const id of ['paste-button','apply-button']) $(id).disabled=!doc;
  get('tab-code').disabled=!doc;
  get('toggle-fit').disabled=!doc;
  syncSourceCount(); renderPreview(true); setSaveState();
}
function addDoc(name,format,source='',sample=false,mode='preview') {
  if (state.docs.length >= MAX) { toast('storedLimit',{number:MAX}); return; }
  const doc={id:makeId(),name:validName(name),format,source,sample};
  state.docs.unshift(doc); state.current=doc.id;
  persist(); renderShelf(); renderWorkspace(); selectTab(mode); closeMobileLibrary();
  return doc;
}
function guessFormat(text,name='') {
  if (/\.svg$/i.test(name)) return 'svg';
  if (/\.html?$/i.test(name)) return 'html';
  if (name && knownExt.test(name)) return 'text';
  return svgLike.test(text.slice(0,4096)) ? 'svg' : htmlLike.test(text.slice(0,4096)) ? 'html' : 'text';
}
function updateEditor() {
  const doc=current();
  if (!doc) return;
  const source=get('editor').value;
  if (source.length > LIMIT) { get('editor').value=source.slice(0,LIMIT); toast('characterLimit'); }
  doc.source=get('editor').value; doc.sample=false;
  syncSourceCount(); queueSave();
}

get('library-toggle').addEventListener('click',() => setLibrary(get('workspace').dataset.library !== 'open'));
get('language-button').addEventListener('click',() => {
  lang = lang === 'zh' ? 'en' : 'zh';
  prefsSet('code-canvas-web:lang',lang);
  const url=new URL(location.href); url.searchParams.set('lang',lang);
  try { history.replaceState(null,'',url); } catch {}
  for (const doc of state.docs) if (doc.sample) { doc.name=translate('demoTitle'); doc.source=sampleSource(); }
  persist(); l10n(); renderShelf(); renderWorkspace();
});
get('documents').addEventListener('click',event => {
  const button=event.target.closest('button[data-doc-id]'); if (!button) return;
  state.current=button.dataset.docId;
  persist(); renderShelf(); renderWorkspace(); selectTab('preview'); closeMobileLibrary();
  get('tab-preview').focus();
});
get('demo-button').addEventListener('click',() => {
  const demo=state.docs.find(doc => doc.sample);
  if (demo) {
    state.current=demo.id; persist(); renderShelf(); renderWorkspace(); selectTab('preview'); closeMobileLibrary();
  } else {
    addDoc(translate('demoTitle'),'html',sampleSource(),true);
  }
  get('tab-preview').focus();
});
get('tab-preview').addEventListener('click',() => selectTab('preview'));
get('tab-code').addEventListener('click',() => selectTab('code'));
document.querySelector('[role=tablist]').addEventListener('keydown',event => {
  if (!current() || !['ArrowLeft','ArrowRight','Home','End'].includes(event.key)) return;
  event.preventDefault();
  selectTab(event.key === 'Home' ? 'preview' : event.key === 'End' ? 'code' : tab === 'code' ? 'preview' : 'code',true);
});
get('toggle-fit').addEventListener('click',() => {
  const pressed=get('toggle-fit').getAttribute('aria-pressed') === 'true';
  get('toggle-fit').setAttribute('aria-pressed',String(!pressed));
  get('preview-wrap').dataset.expanded=String(!pressed);
  get('toggle-fit').querySelector('[data-text]').textContent=translate(pressed ? 'expanded' : 'shrink');
});
get('editor').addEventListener('input',updateEditor);
$('apply-button').addEventListener('click',() => { if (!persist()) toast('storageError'); selectTab('preview',true); });
get('format-select').addEventListener('change',() => {
  const doc=current(); if (!doc || !TYPES.has(get('format-select').value)) return;
  doc.format=get('format-select').value; doc.sample=false; persist(); renderShelf(); get('kind-badge').textContent=typeTag(doc.format);
});
function insertPaste(text) {
  const doc=current(), editor=get('editor');
  if (!doc || typeof text !== 'string') return;
  const selectionLength=editor.selectionEnd-editor.selectionStart;
  if (editor.value.length + text.length - selectionLength > LIMIT) { toast('characterLimit'); return; }
  const fresh=!editor.value.trim();
  editor.setRangeText(text,editor.selectionStart,editor.selectionEnd,'end');
  if (fresh && text.trim()) {
    doc.format=guessFormat(text);
    get('format-select').value=doc.format;
    get('kind-badge').textContent=typeTag(doc.format);
    renderShelf();
  }
  updateEditor();
}
$('paste-button').addEventListener('click',async () => {
  const editor=get('editor');
  try { insertPaste(await navigator.clipboard.readText()); } catch { toast('pasteFallback'); }
  editor.focus();
});
get('editor').addEventListener('paste',event => {
  if (!event.clipboardData) return;
  event.preventDefault(); insertPaste(event.clipboardData.getData('text/plain'));
});
get('import-button').addEventListener('click',() => get('file-picker').click());
get('file-picker').addEventListener('change',async () => {
  const picker=get('file-picker'), file=picker.files[0];
  picker.value='';
  if (!file) return;
  if (file.size > LIMIT) { toast('sizeLimit'); return; }
  if (!knownExt.test(file.name) && !/^(?:text\/|image\/svg\+xml$)/i.test(file.type)) { toast('fileType'); return; }
  try {
    const text=await file.text();
    if (/\u0000/.test(text)) { toast('fileType'); return; }
    if (addDoc(file.name,guessFormat(text,file.name),text)) toast(saveOK ? 'imported' : 'storageError');
  } catch { toast('importError'); }
});
function showName(mode) {
  const doc=current();
  if (mode === 'new' && state.docs.length >= MAX) { toast('storedLimit',{number:MAX}); return; }
  naming=mode; previousNameFocus=document.activeElement;
  get('name-title').textContent=translate(mode === 'new' ? 'newFile' : 'rename');
  get('name-input').value=mode === 'new' ? '' : doc?.name ?? '';
  get('name-input').placeholder=translate('untitled');
  get('name-format').value=mode === 'new' ? 'html' : doc?.format ?? 'text';
  get('name-format').disabled=mode !== 'new';
  get('name-dialog').showModal(); get('name-input').focus();
}
get('new-button').addEventListener('click',() => showName('new'));
get('rename-button').addEventListener('click',() => showName('rename'));
get('name-form').addEventListener('submit',event => {
  event.preventDefault();
  const name=validName(get('name-input').value);
  if (!name) return;
  get('name-dialog').close('confirmed');
  if (naming === 'new') {
    if (addDoc(name,get('name-format').value,'',false,'code')) get('editor').focus();
  } else {
    const doc=current(); if (!doc) return;
    doc.name=name; doc.sample=false; persist(); renderShelf(); get('document-title').textContent=doc.name;
    get('rename-button').focus();
  }
});
get('name-dialog').addEventListener('close',() => { if (get('name-dialog').returnValue !== 'confirmed') previousNameFocus?.focus(); get('name-dialog').returnValue=''; });
get('remove-button').addEventListener('click',() => {
  const doc=current(); if (!doc) return;
  pendingDelete=doc.id; previousDeleteFocus=document.activeElement;
  get('delete-message').textContent=translate('deleteConfirm',{name:doc.name});
  get('delete-dialog').showModal();
});
get('delete-confirm').addEventListener('click',() => {
  const index=state.docs.findIndex(doc => doc.id === pendingDelete);
  if (index < 0) { get('delete-dialog').close(); return; }
  state.docs.splice(index,1); state.current=state.docs[Math.min(index,state.docs.length-1)]?.id ?? null;
  get('delete-dialog').close('confirmed');
  persist(); renderShelf(); renderWorkspace(); selectTab('preview');
  get('tab-preview').focus(); toast('deleted');
});
get('delete-dialog').addEventListener('close',() => { if (get('delete-dialog').returnValue !== 'confirmed') previousDeleteFocus?.focus(); get('delete-dialog').returnValue=''; });
document.querySelectorAll('[data-close]').forEach(button => button.addEventListener('click',() => get(button.dataset.close).close()));
get('export-button').addEventListener('click',() => {
  const doc=current(); if (!doc) return;
  const extension=doc.format === 'text' ? 'txt' : doc.format;
  const mime=doc.format === 'html' ? 'text/html' : doc.format === 'svg' ? 'image/svg+xml' : 'text/plain';
  const name=knownExt.test(doc.name) ? doc.name : doc.name+'.'+extension;
  const a=document.createElement('a'), url=URL.createObjectURL(new Blob([doc.source],{type:mime+';charset=utf-8'}));
  a.href=url; a.download=name; a.hidden=true; document.body.append(a); a.click(); a.remove();
  setTimeout(() => URL.revokeObjectURL(url),10000);
  toast('exported');
});
window.addEventListener('beforeinstallprompt',event => { event.preventDefault(); installPrompt=event; });
get('install-button').addEventListener('click',async () => {
  if (installPrompt) {
    const p=installPrompt; installPrompt=null;
    try { await p.prompt(); } catch { get('install-dialog').showModal(); }
  } else get('install-dialog').showModal();
});
window.addEventListener('online',updateConnection);
window.addEventListener('offline',updateConnection);
if ('serviceWorker' in navigator && location.protocol !== 'file:') {
  navigator.serviceWorker.register('./sw.js').then(() => navigator.serviceWorker.ready).then(() => { offlineReady=true; updateConnection(); }).catch(() => {});
}
for (const doc of state.docs) if (doc.sample) { doc.name=translate('demoTitle'); doc.source=sampleSource(); }
l10n(); persist(); renderShelf(); renderWorkspace(); selectTab('preview');
