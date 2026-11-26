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

Object.assign(strings.zh, {
  about:'关于', actions:'作品操作', back:'返回作品库', findStudy:'搜索作品',
  designLab:'设计实验室', closerLook:'走近画布', studyIntro:'用三个本地作品，探索光线、色彩与空间。',
  aboutPort:'以 Apple 版设计实验室为原型，在浏览器里重现三个习作和画布操作。你也可以导入、编辑和导出自己的作品。',
  noResults:'没有匹配的作品', localDrafts:'{number} 个本机作品', canvasControls:'画布控制',
  zoomOut:'缩小', zoomIn:'放大', fitCanvas:'适合画布', alignment:'对齐网格', editCopy:'复制并编辑', sample:'内置习作'
});
Object.assign(strings.en, {
  about:'About', actions:'Artwork actions', back:'Back to library', findStudy:'Find a study',
  designLab:'Design Lab', closerLook:'A closer look', studyIntro:'Explore three local studies in light, color, and space.',
  aboutPort:'A browser port of the Apple Design Lab’s three studies and canvas controls. You can also import, edit and export your own work.',
  noResults:'No matching artwork', localDrafts:'{number} local documents', canvasControls:'Canvas controls',
  zoomOut:'Zoom out', zoomIn:'Zoom in', fitCanvas:'Fit artwork', alignment:'Alignment grid', editCopy:'Copy and edit', sample:'Built-in study'
});

const LIMIT = 524288;
const MAX = 8;
const STORAGE = 'code-canvas-web:library:v1';
const TYPES = new Set(['html','svg','text']);
const htmlLike = /^\s*(?:<!doctype\s+html\b|<!--|<\/?(?:html|body|head|style|script|div|main|article|section|h[1-6]|p|span|pre|button|input|img|video|canvas|table|form)\b)/i;
const svgLike = /^\s*(?:<\?xml[^>]*\?>\s*)?(?:<!--[\s\S]*?-->\s*)*<svg(?:\s|>)/i;
const knownExt = /\.(html?|svg|txt|md|css|js|json|xml)$/i;
const $ = id => document.getElementById(id);
const refs = Object.fromEntries(['workspace','document-title','documents','document-count','library-toggle','kind-badge','save-state','tab-preview','tab-code','preview-panel','code-panel','preview-wrap','preview','plain-preview','preview-format','toggle-fit','editor','format-select','source-count','connection','connection-label','status','language-button','file-picker','new-button','import-button','remove-button','rename-button','export-button','install-button','install-dialog','name-dialog','name-title','name-input','name-format','name-form','delete-dialog','delete-message','delete-confirm'].map(id => [id,$(id)]));
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

function stateFrom(raw) {
  if (!raw) return {docs:[], current:null};
  try {
    const data = JSON.parse(raw);
    if (!Array.isArray(data.docs)) throw new Error('invalid');
    const seen = new Set();
    const docs = data.docs.filter(d => d && typeof d.id === 'string' && d.id.length <= 100 && !seen.has(d.id) && seen.add(d.id)
      && TYPES.has(d.format) && typeof d.source === 'string' && d.source.length <= LIMIT)
      .slice(0,MAX).map(d => ({id:d.id, name:validName(d.name), format:d.format, source:d.source, sample:d.sample === true}));
    return {docs,current:docs.some(d => d.id === data.current) ? data.current : docs[0]?.id ?? null};
  } catch { return {docs:[], current:null}; }
}
const state = stateFrom(prefsGet(STORAGE));
let activeStudy = null;
let zoom = 1;
let libraryReturnFocus = null;
let libraryScroll = 0;
const current = () => activeStudy
  ? {name:activeStudy[lang][0],format:'svg',source:studySVG(activeStudy,lang)}
  : state.docs.find(d => d.id === state.current);
function toast(key,args) {
  get('status').textContent = translate(key,args);
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => { get('status').textContent = ''; },4200);
}
function setSaveState() { get('save-state').textContent = translate(activeStudy ? 'sample' : saveOK ? 'saved' : 'unsaved'); }
function save() {
  try { saveOK = prefsSet(STORAGE,JSON.stringify(state)); } catch { saveOK = false; }
  setSaveState();
  return saveOK;
}
function queueSave() { clearTimeout(persistTimer); persistTimer = setTimeout(persist,180); }
function persist() { clearTimeout(persistTimer); persistTimer = undefined; return save(); }
window.addEventListener('pagehide', () => { if (persistTimer !== undefined) persist(); });
function route(screen) {
  const detail=screen === 'detail';
  get('workspace').dataset.screen=screen;
  for (const id of ['library-toggle','document-title','document-menu-button','study-screen']) $(id).hidden=!detail;
  for (const id of ['library-title','about-button','library']) $(id).hidden=detail;
  window.scrollTo(0,detail ? 0 : libraryScroll);
  (detail ? get('document-title') : libraryReturnFocus?.isConnected ? libraryReturnFocus : $('search')).focus({preventScroll:true});
}
function enterDetail() {
  if (get('workspace').dataset.screen !== 'detail') {
    libraryScroll=window.scrollY;
    libraryReturnFocus=document.activeElement;
    if (history.state?.codeCanvasDetail !== true) history.pushState({codeCanvasDetail:true},'');
  }
  resetCanvas();
  route('detail');
}
function leaveDetail() {
  if (history.state?.codeCanvasDetail === true) history.back();
  else route('library');
}
window.addEventListener('popstate',event => {
  if (event.state?.codeCanvasDetail && current()) route('detail');
  else route('library');
});
if (history.state?.codeCanvasDetail) history.replaceState(null,'');
function resetCanvas() {
  setZoom(1);
  $('toggle-grid').setAttribute('aria-pressed','false');
  $('alignment').hidden=true;
}
function setZoom(value) {
  zoom=Math.max(1,Math.min(2,value));
  const view=get('preview-wrap');
  const centerX=(view.scrollLeft+view.clientWidth/2)/Math.max(view.clientWidth,view.scrollWidth,1);
  const centerY=(view.scrollTop+view.clientHeight/2)/Math.max(view.clientHeight,view.scrollHeight,1);
  $('preview-content').style.width=zoom*100+'%';
  const percent=Math.round(zoom*100)+'%';
  get('toggle-fit').textContent=percent;
  get('toggle-fit').setAttribute('aria-label',translate('fitCanvas')+' · '+percent);
  $('zoom-out').disabled=zoom===1; $('zoom-in').disabled=zoom===2;
  if (zoom===1) {view.scrollLeft=0;view.scrollTop=0;}
  else requestAnimationFrame(() => {
    view.scrollLeft=view.scrollWidth*centerX-view.clientWidth/2;
    view.scrollTop=view.scrollHeight*centerY-view.clientHeight/2;
  });
}
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
  $('search').placeholder = translate('findStudy');
  get('toggle-fit').setAttribute('aria-label',translate('fitCanvas')+' · '+get('toggle-fit').textContent);
  setSaveState(); updateConnection();
}

const BLOCK = new Set(['script','noscript','link','meta','base','iframe','frame','frameset','object','embed','portal','fencedframe','foreignobject','template','handler','discard']);
const URL_ATTR = new Set(['href','src','srcset','imagesrcset','srcdoc','action','formaction','data','poster','background','ping','cite','manifest','longdesc','codebase','classid','profile','archive','target','download','attributionsrc','itemid','itemtype','itemprop','lowsrc','dynsrc','urn']);
const LOCAL_FRAGMENT = /^#[^\s"'<>]*$/;
const INLINE_MEDIA = /^data:(?:image\/(?:png|jpe?g|gif|webp|avif|bmp|x-icon)|audio\/(?:ogg|wav|mp3|mpeg|webm)|video\/(?:mp4|webm|ogg));base64,[a-z0-9+/=\s]+$/i;
const SAFE_ANIMATION = /^(?:[dxy]|opacity|stroke(?:-width|-dashoffset|-dasharray|-opacity)?|fill(?:-opacity)?|(?:cx|cy|r|rx|ry)|(?:x|y)[12]|width|height|viewbox|offset|stop-color|stop-opacity|transform)$/i;
const PAGE_CSP = "default-src 'none'; style-src 'unsafe-inline'; img-src data:; media-src data:; font-src data:; base-uri 'none'; form-action 'none'; object-src 'none'; connect-src 'none'; frame-src 'none'; script-src 'none'";
function isolatedMarkup(source,kind,builtIn=false) {
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
  const css = "html{min-height:100%;color-scheme:light dark}body{margin:0;overflow-wrap:anywhere}img,video{max-width:100%}pre{overflow:auto}svg{max-width:100%;height:auto}body.canvas-vector{min-height:100vh;display:grid;place-items:center;padding:10px;box-sizing:border-box}body.canvas-vector>svg{display:block;width:auto;max-width:100%;max-height:calc(100vh - 20px)}" + (builtIn ? 'body.canvas-vector{padding:0;display:block}body.canvas-vector>svg{width:100%;max-height:none}' : '');
  return '<!doctype html><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><meta http-equiv="Content-Security-Policy" content="' + PAGE_CSP + '"><style>' + css + '</style><body' + (kind === 'svg' ? ' class="canvas-vector"' : '') + '>' + markup + '</body>';
}

function renderPreview(force=false) {
  const doc = current();
  const key = doc ? doc.format + '\0' + doc.source : 'empty';
  get('preview-wrap').dataset.study = activeStudy ? 'true' : 'false';
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
    get('preview').srcdoc = isolatedMarkup(doc.source,doc.format,Boolean(activeStudy));
  }
  $('study-subtitle').textContent = activeStudy ? activeStudy[lang][1] : translate(readable ? 'textPreview' : 'sandbox');
  get('preview-format').textContent = doc && !activeStudy ? typeTag(doc.format) : '';
}
function selectTab(next,moveFocus=false) {
  tab = next;
  for (const name of ['preview','code']) {
    const yes = next === name;
    get('tab-' + name).setAttribute('aria-selected',String(yes));
    get('tab-' + name).tabIndex = yes ? 0 : -1;
    get(name + '-panel').hidden = !yes;
  }
  $('canvas-controls').hidden = next !== 'preview';
  if (next === 'preview') renderPreview();
  if (moveFocus) get('tab-' + next).focus();
}
function syncSourceCount() {
  const n = get('editor').value.split('\n').length;
  get('source-count').textContent = translate('lines',{number:n.toLocaleString(lang)});
}
function renderShelf() {
  const query=$('search').value.trim().toLocaleLowerCase(lang);
  const matches=value=>value.toLocaleLowerCase(lang).includes(query);
  const studies=$('studies');
  studies.replaceChildren();
  for(const study of studi) {
    if (!matches(study[lang].slice(0,2).join(' '))) continue;
    const li=document.createElement('li'), button=document.createElement('button'), artwork=document.createElement('img');
    const labels=document.createElement('span'), name=document.createElement('strong'), subtitle=document.createElement('span'), chevron=document.createElement('span');
    button.className='study-card'; button.type='button'; button.dataset.study=study.id;
    artwork.src='data:image/svg+xml,'+encodeURIComponent(studySVG(study,lang));
    artwork.width=960; artwork.height=600; artwork.alt='';
    labels.className='card-labels'; name.textContent=study[lang][0]; subtitle.textContent=study[lang][1];
    chevron.className='chevron'; chevron.setAttribute('aria-hidden','true'); chevron.textContent='›';
    labels.append(name,subtitle,chevron); button.append(artwork,labels); li.append(button); studies.append(li);
  }
  const list=get('documents');
  list.replaceChildren();
  get('document-count').textContent=translate('localDrafts',{number:state.docs.length});
  for (const doc of state.docs) {
    if (!matches(doc.name+' '+typeTag(doc.format))) continue;
    const li=document.createElement('li'), button=document.createElement('button'), kind=document.createElement('span'), name=document.createElement('span');
    button.className='doc'; button.type='button';
    button.dataset.docId=doc.id;
    kind.className='doc-kind'; kind.textContent=typeTag(doc.format);
    name.className='doc-name'; name.textContent=doc.name;
    button.append(kind,name); li.append(button); list.append(li);
  }
  $('no-docs').hidden=list.children.length>0;
  $('no-docs').textContent=translate(query ? 'noResults' : 'emptyList');
}
function renderWorkspace() {
  const doc = current();
  get('document-title').textContent=doc?.name || translate('emptyTitle');
  get('kind-badge').textContent=doc ? typeTag(doc.format) : '—';
  get('editor').value=doc?.source || '';
  get('format-select').value=doc?.format || 'text';
  for (const id of ['editor','format-select','export-button']) get(id).disabled=!doc;
  for (const id of ['rename-button','remove-button']) get(id).hidden=Boolean(activeStudy);
  get('tab-code').textContent=translate(activeStudy ? 'editCopy' : 'source');
  for (const id of ['paste-button','apply-button']) $(id).disabled=!doc;
  get('tab-code').disabled=!doc;
  get('toggle-fit').disabled=!doc;
  syncSourceCount(); renderPreview(true); setSaveState();
}
function addDoc(name,format,source='',sample=false,mode='preview') {
  if (state.docs.length >= MAX) { toast('storedLimit',{number:MAX}); return; }
  const doc={id:makeId(),name:validName(name),format,source,sample};
  activeStudy=null; state.docs.unshift(doc); state.current=doc.id;
  persist(); renderShelf(); renderWorkspace(); selectTab(mode); enterDetail();
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

get('library-toggle').addEventListener('click',leaveDetail);
$('search').addEventListener('input',renderShelf);
$('about-button').addEventListener('click',() => $('about-dialog').showModal());
$('document-menu-button').addEventListener('click',() => $('document-menu').showModal());
get('language-button').addEventListener('click',() => {
  lang=lang === 'zh' ? 'en' : 'zh';
  prefsSet('code-canvas-web:lang',lang);
  const url=new URL(location.href); url.searchParams.set('lang',lang);
  try { history.replaceState(history.state,'',url); } catch {}
  l10n(); renderShelf(); renderWorkspace();
});
$('studies').addEventListener('click',event => {
  const button=event.target.closest('button[data-study]'); if (!button) return;
  activeStudy=studi.find(study=>study.id===button.dataset.study);
  renderWorkspace(); selectTab('preview'); enterDetail();
});
get('documents').addEventListener('click',event => {
  const button=event.target.closest('button[data-doc-id]'); if (!button) return;
  activeStudy=null; state.current=button.dataset.docId;
  persist(); renderWorkspace(); selectTab('preview'); enterDetail();
});
function chooseTab(next) {
  $('document-menu').close();
  if(next==='code' && activeStudy) {
    const doc=current();
    if(addDoc(doc.name,'svg',doc.source,false,'code')) get('editor').focus();
    return;
  }
  selectTab(next);
  (next==='code' ? get('editor') : get('document-title')).focus();
}
get('tab-preview').addEventListener('click',() => chooseTab('preview'));
get('tab-code').addEventListener('click',() => chooseTab('code'));
document.querySelector('[role=tablist]').addEventListener('keydown',event => {
  if (!current() || !['ArrowLeft','ArrowRight','Home','End'].includes(event.key)) return;
  event.preventDefault();
  get(event.key==='Home' || event.key==='ArrowLeft' ? 'tab-preview' : 'tab-code').focus();
});
get('toggle-fit').addEventListener('click',() => setZoom(1));
$('zoom-out').addEventListener('click',() => setZoom(zoom-.25));
$('zoom-in').addEventListener('click',() => setZoom(zoom+.25));
$('toggle-grid').addEventListener('click',() => {
  const show=$('alignment').hidden;
  $('alignment').hidden=!show;
  $('toggle-grid').setAttribute('aria-pressed',String(show));
});
get('editor').addEventListener('input',updateEditor);
$('apply-button').addEventListener('click',() => { if (!persist()) toast('storageError'); selectTab('preview'); get('document-title').focus(); });
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
  $('document-menu').close(); leaveDetail(); toast('deleted');
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
l10n(); persist(); renderShelf(); renderWorkspace(); selectTab('preview');
