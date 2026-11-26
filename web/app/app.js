'use strict';
const strings={
 zh:{
  skip:'跳到工作区',brand:'代码画布',local:'仅在本机',offline:'当前离线',ready:'可离线使用',install:'安装应用',
  installHelp:'在 iPhone／iPad Safari 中点「分享 → 添加到主屏幕」。Android Chrome 可打开菜单选择「安装应用」或「添加到主屏幕」。',
  offlineHelp:'首次打开后等待「可离线使用」。请在相同的浏览器或已安装的网页应用中使用。',
  new:'新建',import:'导入文件',saved:'已保存在本机',unsaved:'未能写入本机',rename:'重命名',export:'导出',remove:'删除文件',
  tabs:'工作区模式',preview:'预览',source:'代码',frame:'代码预览',sandbox:'隔离预览 · 禁用脚本与联网',textPreview:'纯文本预览 · 不执行代码',
  format:'格式',plain:'纯文本',paste:'粘贴',apply:'应用并预览',sourceEditor:'代码编辑器',close:'关闭',name:'文件名',cancel:'取消',
  confirm:'确定',newFile:'新建文件',untitled:'未命名',blank:'在代码页输入或粘贴内容。',deleteConfirm:'确定删除《{name}》？这一操作无法撤销。',
  storedLimit:'最多保存 {number} 个文件。请先导出备份，再删除不需要的文件。',sizeLimit:'文件超过 512 KB，请先缩小文件大小。',
  fileType:'请导入 HTML、SVG 或文本文件。',importError:'未能读取文件。',pasteFallback:'请在下方手动粘贴。',pasteEditor:'请在代码编辑器中手动粘贴。',
  exported:'已发起下载。',imported:'已导入到本机。',storageError:'浏览器未能保存文件，请导出备份。',
  emptyTitle:'从一段代码开始',emptyText:'粘贴一段代码、导入本机文件，或新建一个空白草稿。',lines:'{number} 行',deleted:'已删除文件。',
  plainTag:'文本',characterLimit:'最多支持 524,288 个字符，请缩短内容。',
  settings:'设置',actions:'文件操作',back:'返回文件库',findStudy:'搜索文件或代码',aboutPort:'网页编辑器。文件只存在当前浏览器，不会同步。请导出重要文件。',
  noResults:'没有匹配的文件',noResultsHelp:'换一个文件名或代码关键词试试。',localDrafts:'我的文件 · {number}',canvasControls:'画布控制',
  zoomOut:'缩小',zoomIn:'放大',fitWindow:'适应窗口',fitContent:'适应内容',fitModes:'适应方式',zoomControls:'手动缩放',canvasViewport:'可滚动的画布',
  fullExit:'退出全屏',fullscreen:'全屏',refresh:'刷新',refreshed:'已刷新预览。',viewMode:'文件显示方式',listView:'列表视图',galleryView:'网格视图',
  openActions:'{name} 的文件操作',fitUnavailable:'这个 SVG 的绘制边界无法可靠测量，已保留原画框。',createFromPaste:'创建文件',noPreview:'暂无预览'
 },
 en:{
  skip:'Skip to workspace',brand:'Code Canvas',local:'On this device',offline:'Offline',ready:'Offline ready',install:'Install app',
  installHelp:'On iPhone or iPad, open Safari and choose Share → Add to Home Screen. On Android Chrome, choose Install app or Add to Home screen.',
  offlineHelp:'Wait for the Offline ready indicator on the first visit. Reopen the same browser or installed web app.',
  new:'New',import:'Import file',saved:'Saved on device',unsaved:'Not saved on device',rename:'Rename',export:'Export',remove:'Delete file',
  tabs:'Workspace mode',preview:'Preview',source:'Code',frame:'Code preview',sandbox:'Isolated preview · Scripts and network disabled',textPreview:'Plain text · Code is not executed',
  format:'Format',plain:'Plain text',paste:'Paste',apply:'Apply & preview',sourceEditor:'Code editor',close:'Close',name:'File name',cancel:'Cancel',
  confirm:'Confirm',newFile:'New file',untitled:'Untitled',blank:'Type or paste content in the Code tab.',deleteConfirm:'Delete “{name}”? This cannot be undone.',
  storedLimit:'You can save up to {number} files. Export a backup and delete a file first.',sizeLimit:'This file exceeds 512 KB. Reduce its size and try again.',
  fileType:'Import an HTML, SVG, or text file.',importError:'This file could not be read.',pasteFallback:'Paste manually below.',pasteEditor:'Paste manually in the code editor.',
  exported:'Download started.',imported:'Imported to this device.',storageError:'The browser could not save this file. Export a backup.',
  emptyTitle:'Start with a snippet',emptyText:'Paste a snippet, import a file from your device, or create a blank draft.',lines:'{number} lines',deleted:'File deleted.',
  plainTag:'Text',characterLimit:'The editor accepts up to 524,288 characters. Shorten the file and try again.',
  settings:'Settings',actions:'File actions',back:'Back to files',findStudy:'Search files or code',aboutPort:'Files stay in this browser and do not sync. Export important files to keep a backup.',
  noResults:'No matching files',noResultsHelp:'Try a different file name or a code keyword.',localDrafts:'My files · {number}',canvasControls:'Canvas controls',
  zoomOut:'Zoom out',zoomIn:'Zoom in',fitWindow:'Fit window',fitContent:'Fit content',fitModes:'Fit modes',zoomControls:'Manual zoom',canvasViewport:'Scrollable canvas',
  fullExit:'Exit full screen',fullscreen:'Full screen',refresh:'Refresh',refreshed:'Preview refreshed.',viewMode:'File layout',listView:'List view',galleryView:'Grid view',
  openActions:'File actions for {name}',fitUnavailable:'The artwork bounds could not be measured reliably. Showing the original SVG frame.',createFromPaste:'Create file',noPreview:'No preview yet'
 }
};
const LIMIT=524288, MAX=8;
const STORAGE='code-canvas-web:library:v1', PREF='code-canvas-web:';
const TYPES=new Set(['html','svg','text']);
const htmlLike=/^\s*(?:<!doctype\s+html\b|<!--|<\/?(?:html|body|head|style|script|div|main|article|section|h[1-6]|p|span|pre|button|input|img|video|canvas|table|form)\b)/i;
const svgLike=/^\s*(?:<\?xml[^>]*\?>\s*)?(?:<!--[\s\S]*?-->\s*)*<svg(?:\s|>)/i;
const knownExt=/\.(html?|svg|txt|md|css|js|json|xml)$/i;
const $=id=>document.getElementById(id);
function prefsGet(key){try{return localStorage.getItem(key);}catch{return null;}}
function prefsSet(key,value){try{localStorage.setItem(key,value);return true;}catch{return false;}}
const requested=new URLSearchParams(location.search).get('lang');
let lang=requested==='en'||requested==='zh'?requested:prefsGet(PREF+'lang')==='en'?'en':'zh';
let layout=prefsGet(PREF+'layout')==='list'?'list':'grid';
let saveOK=true, tab='preview', fit='window', zoom=1, baseFit='window', fullscreen=false, offlineReady=false;
let toastTimer,persistTimer,installPrompt,naming,pendingDelete,renderedKey,menuOpenedBy,libraryFocus,previousFocus,prepared,libraryScroll=0;
const t=(key,args={})=>(strings[lang][key]||key).replace(/\{(\w+)\}/g,(_,name)=>args[name]??'');
const tag=format=>format==='text'?t('plainTag'):format.toUpperCase();
const makeId=()=>globalThis.crypto?.randomUUID?.()||'id-'+Date.now().toString(36)+'-'+Math.random().toString(36).slice(2);
const validName=value=>(typeof value==='string'?value.replace(/[\u0000-\u001f\u007f/\\:*?"<>|]/g,' ').trim().slice(0,80):'')||t('untitled');
function readState(raw){
 try{
  const data=JSON.parse(raw);
  if(!Array.isArray(data?.docs))return {docs:[],current:null};
  const seen=new Set();
  const docs=data.docs.filter(d=>d&&d.sample!==true&&typeof d.id==='string'&&d.id.length<=100&&!seen.has(d.id)&&seen.add(d.id)&&TYPES.has(d.format)&&typeof d.source==='string'&&d.source.length<=LIMIT)
   .slice(0,MAX).map(d=>({id:d.id,name:validName(d.name),format:d.format,source:d.source}));
  return {docs,current:docs.some(d=>d.id===data.current)?data.current:null};
 }catch{return {docs:[],current:null};}
}
const state=readState(prefsGet(STORAGE));
const current=()=>state.docs.find(d=>d.id===state.current);
function toast(key,args){
 $('status').textContent=t(key,args);
 clearTimeout(toastTimer);toastTimer=setTimeout(()=>$('status').textContent='',4000);
}
function persist(){
 clearTimeout(persistTimer);persistTimer=undefined;
 saveOK=prefsSet(STORAGE,JSON.stringify(state));$('save-state').textContent=t(saveOK?'saved':'unsaved');
 return saveOK;
}
function queueSave(){clearTimeout(persistTimer);persistTimer=setTimeout(persist,180);}
window.addEventListener('pagehide',()=>{if(persistTimer!==undefined)persist();});
function updateConnection(){$('connection').dataset.state=navigator.onLine?'online':'offline';$('connection-label').textContent=t(navigator.onLine?offlineReady?'ready':'local':'offline');}
function localize(){
 document.documentElement.lang=lang==='zh'?'zh-CN':'en';
 document.title=t('brand')+(lang==='zh'?' · Code Canvas':' · Web workspace');
 for(const n of document.querySelectorAll('[data-text]'))n.textContent=t(n.dataset.text);
 for(const n of document.querySelectorAll('[data-label]'))n.setAttribute('aria-label',t(n.dataset.label));
 for(const n of document.querySelectorAll('[data-title]'))n.title=t(n.dataset.title);
 $('language-button').lang=lang==='en'?'zh-CN':'en';$('language-button').textContent=lang==='en'?'中文':'English';
 $('search').placeholder=t('findStudy');$('save-state').textContent=t(saveOK?'saved':'unsaved');
 updateConnection();updateViewportLabels();
}
const BLOCK = new Set(['script','noscript','link','meta','base','iframe','frame','frameset','object','embed','portal','fencedframe','foreignobject','template','handler','discard']);
const URL_ATTR = new Set(['href','src','srcset','imagesrcset','srcdoc','action','formaction','data','poster','background','ping','cite','manifest','longdesc','codebase','classid','profile','archive','target','download','attributionsrc','itemid','itemtype','itemprop','lowsrc','dynsrc','urn']);
const LOCAL_FRAGMENT = /^#[^\s"'<>]*$/;
const INLINE_MEDIA = /^data:(?:image\/(?:png|jpe?g|gif|webp|avif|bmp|x-icon)|audio\/(?:ogg|wav|mp3|mpeg|webm)|video\/(?:mp4|webm|ogg));base64,[a-z0-9+/=\s]+$/i;
const SAFE_ANIMATION = /^(?:[dxy]|opacity|stroke(?:-width|-dashoffset|-dasharray|-opacity)?|fill(?:-opacity)?|(?:cx|cy|r|rx|ry)|(?:x|y)[12]|width|height|viewbox|offset|stop-color|stop-opacity|transform)$/i;
const PAGE_CSP = "default-src 'none'; style-src 'unsafe-inline'; img-src data:; media-src data:; font-src data:; base-uri 'none'; form-action 'none'; object-src 'none'; connect-src 'none'; frame-src 'none'; script-src 'none'";
function isolatedTree(source) {
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
  return template;
}
const SVG_SHELL='html,body{margin:0;width:100%;height:100%;overflow:hidden}body>svg:first-of-type{display:block;width:100%!important;height:100%!important}';
const HTML_SHELL='html{min-height:100%;color-scheme:light dark}body{margin:0;overflow-wrap:anywhere}img,video,Svg{max-width:100%}svg{height:auto}pre{max-width:100%;overflow:auto}';
function prepareSvg(source){
 const template=isolatedTree(source);
 const root=template.content.querySelector('svg');
 if(!root)return {template,root:null,bounds:null};
 const frame=declaredFrame(root);
 if(!root.hasAttribute('viewBox'))root.setAttribute('viewBox',frame.join(' '));
 root.setAttribute('xmlns',SVG_NS);
 return {template,root,frame,bounds:contentFrame(root,frame)};
}
function markupFor(source,format,scaleFit){
 let template;
 if(format==='svg'){
  if(prepared?.source!==source)prepared={source,...prepareSvg(source)};
  ({template}=prepared);
  if(prepared.root)prepared.root.setAttribute('viewBox',(scaleFit==='content'&&prepared.bounds?prepared.bounds:prepared.frame).join(' '));
 }else template=isolatedTree(source);
 return '<!doctype html><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><meta http-equiv="Content-Security-Policy" content="'+PAGE_CSP+'"><style>'+(format==='svg'?HTML_SHELL+SVG_SHELL:HTML_SHELL)+'</style><body>'+template.innerHTML+'</body>';
}
function renderPreview(force=false){
 const doc=current();
 const format=doc?.format||'text', source=doc?.source||'';
 if(format==='svg'&&baseFit==='content'&&prepared?.source!==source){
  prepared={source,...prepareSvg(source)};
  if(!prepared.bounds){baseFit='window';if(fit==='content')fit='window';}
 }
 const key=format+'\0'+source+'\0'+(format==='svg'?baseFit:'window');
 const text=!source.trim()||format==='text';
 if(force||key!==renderedKey){
  renderedKey=key;
  $('preview').hidden=text;$('plain-preview').hidden=!text;
  if(text){$('preview').removeAttribute('srcdoc');$('plain-preview').textContent=source||t('blank');}
  else{$('plain-preview').textContent='';$('preview').srcdoc=markupFor(source,format,baseFit);}
 }
 $('preview-wrap').dataset.format=format;
 $('preview-description').textContent=doc?tag(format):'';
 $('preview-security').textContent=t(text?'textPreview':'sandbox');
 $('fit-content').hidden=format!=='svg'||text;
 updateViewportLabels();
}
function updateViewportLabels(){
 $('fit-window').setAttribute('aria-pressed',String(fit==='window'));
 $('fit-content').setAttribute('aria-pressed',String(fit==='content'));
 $('fullscreen-button').setAttribute('aria-pressed',String(fullscreen));
 $('fullscreen-button').querySelector('[data-text]').textContent=t(fullscreen?'fullExit':'fullscreen');
 $('zoom-in').disabled=zoom>=3;
 $('zoom-out').disabled=zoom<=.5;
 $('zoom-value').textContent=Math.round(zoom*100)+'%';
}
function paintViewport(recenter=false){
 const view=$('preview-wrap'), spacer=$('preview-spacer'), content=$('preview-content');
 const w=view.clientWidth,h=view.clientHeight;
 if(!w||!h)return;
 const x=(view.scrollLeft+w/2)/Math.max(w,view.scrollWidth), y=(view.scrollTop+h/2)/Math.max(h,view.scrollHeight);
 content.style.width=w+'px';content.style.height=h+'px';content.style.transform='scale('+zoom+')';
 spacer.style.width=Math.max(1,Math.round(w*zoom))+'px';spacer.style.height=Math.max(1,Math.round(h*zoom))+'px';
 spacer.style.marginInlineStart=zoom<1?Math.round((w*(1-zoom))/2)+'px':'0';
 spacer.style.marginBlockStart=zoom<1?Math.round((h*(1-zoom))/2)+'px':'0';
 if(zoom<=1){view.scrollLeft=0;view.scrollTop=0;}
 else if(recenter){
  view.scrollLeft=Math.round(view.scrollWidth*x-w/2);view.scrollTop=Math.round(view.scrollHeight*y-h/2);
 }
}
new ResizeObserver(()=>paintViewport()).observe($('preview-wrap'));
function setFit(mode,announce=true){
 if(mode==='content'){
  const doc=current();
  if(doc?.format!=='svg'||!doc.source.trim())return;
  if(prepared?.source!==doc.source)prepared={source:doc.source,...prepareSvg(doc.source)};
  if(!prepared.bounds){if(announce)toast('fitUnavailable');return;}
 }
 fit=baseFit=mode;zoom=1;renderPreview();paintViewport();
}
function manualZoom(step){
 zoom=Math.max(.5,Math.min(3,zoom+step));fit='manual';updateViewportLabels();paintViewport(true);
}
function setFullscreen(on){
 if(fullscreen===on)return;
 fullscreen=on;$('preview-panel').classList.toggle('fullscreen',on);
 document.body.classList.toggle('immersive',on);
 document.querySelector('.nav').inert=on;document.querySelector('.mode-toggle').inert=on;
 updateViewportLabels();paintViewport();
 $('fullscreen-button').focus({preventScroll:true});
}
window.addEventListener('keydown',event=>{if(event.key==='Escape'&&fullscreen){event.preventDefault();setFullscreen(false);}});
function selectTab(next,focus=false){
 const old=tab;
 if(next!=='preview'&&fullscreen)setFullscreen(false);
 tab=next;
 for(const name of ['preview','code']){
  const yes=next===name;
  $('tab-'+name).setAttribute('aria-selected',String(yes));$('tab-'+name).tabIndex=yes?0:-1;
  $(name+'-panel').hidden=!yes;
 }
 if(old!==next)animate($(next+'-panel'),'tab-enter');
 if(next==='preview'){renderPreview();paintViewport();}
 if(focus)$('tab-'+next).focus();
}
function animate(el,klass){
 el.classList.remove(klass);void el.offsetWidth;el.classList.add(klass);
}
function route(detail,moveFocus=true){
 if(!detail&&fullscreen)setFullscreen(false);
 $('workspace').dataset.screen=detail?'detail':'library';
 for(const id of ['library-toggle','document-title','document-tools','studio'])$(id).hidden=!detail;
 for(const id of ['library-title','library-tools','library'])$(id).hidden=detail;
 animate($(detail?'studio':'library'),detail?'screen-enter':'library-enter');
 if(detail)paintViewport();
 window.scrollTo(0,detail?0:libraryScroll);
 if(moveFocus)(detail?$('document-title'):libraryFocus?.isConnected?libraryFocus:$('new-button')).focus({preventScroll:true});
}
function openDetail(doc,mode='preview'){
 if($('workspace').dataset.screen!=='detail'){
  libraryScroll=window.scrollY;libraryFocus=document.activeElement;
  if(history.state?.codeCanvasDetail!==true)history.pushState({codeCanvasDetail:true},'','#file');
 }
 state.current=doc.id;fit=baseFit='window';zoom=1;prepared=null;renderedKey=undefined;
 $('preview').removeAttribute('srcdoc');
 $('document-title').textContent=doc.name;
 $('editor').value=doc.source;$('format-select').value=doc.format;
 syncCount();persist();selectTab(mode);route(true);
}
function leaveDetail(){
 renderLibrary();route(false);
 if(history.state?.codeCanvasDetail===true)history.back();
}
window.addEventListener('popstate',event=>{
 if(event.state?.codeCanvasDetail&&current())route(true);
 else{renderLibrary();route(false);}
});
if(history.state?.codeCanvasDetail||location.hash==='#file')history.replaceState(null,'',location.pathname+location.search);
function formatBytes(value){
 const bytes=new TextEncoder().encode(value).length;
 return bytes<1024?bytes+' B':(bytes/1024).toLocaleString(lang,{maximumFractionDigits:1})+' KB';
}
const thumbCache=new Map();
function thumbFor(doc){
 if(doc.format!=='svg'||!doc.source.trim()||doc.source.length>131072)return null;
 const previous=thumbCache.get(doc.id);
 if(previous?.source===doc.source)return previous.url;
 if(previous)URL.revokeObjectURL(previous.url);
 const svg=isolatedTree(doc.source).content.querySelector('svg');
 if(!svg)return null;
 svg.setAttribute('xmlns',SVG_NS);
 const url=URL.createObjectURL(new Blob([svg.outerHTML],{type:'image/svg+xml'}));
 thumbCache.set(doc.id,{source:doc.source,url});
 return url;
}
function renderLibrary(){
 const term=$('search').value.trim().toLocaleLowerCase(lang);
 const all=state.docs, matching=term?all.filter(d=>(d.name+' '+tag(d.format)+' '+d.source).toLocaleLowerCase(lang).includes(term)):all;
 for(const [id,thumb] of thumbCache)if(!all.some(d=>d.id===id)){URL.revokeObjectURL(thumb.url);thumbCache.delete(id);}
 $('document-count').textContent=t('localDrafts',{number:all.length});
 const list=$('documents');list.dataset.view=layout;
 $('view-list').setAttribute('aria-pressed',String(layout==='list'));$('view-grid').setAttribute('aria-pressed',String(layout==='grid'));
 list.replaceChildren();
 for(const doc of matching){
  const li=document.createElement('li');li.className='document';
  const open=document.createElement('button');open.className='doc-open';open.type='button';open.dataset.docId=doc.id;
  const preview=document.createElement('span');preview.className='thumbnail';preview.dataset.kind=doc.format;preview.setAttribute('aria-hidden','true');
  const url=layout==='grid'?thumbFor(doc):null;
  if(url){const image=document.createElement('img');image.src=url;image.alt='';preview.append(image);}
  else preview.textContent=layout==='list'?tag(doc.format):doc.source.trim().split('\n').slice(0,4).join('\n').slice(0,175)||t('noPreview');
  const labels=document.createElement('span');labels.className='doc-labels';
  const title=document.createElement('strong');title.textContent=doc.name;
  const meta=document.createElement('span');meta.className='doc-meta';
  const badge=document.createElement('span');badge.className='badge';badge.dataset.kind=doc.format;badge.textContent=tag(doc.format);
  meta.append(badge,document.createTextNode(' '+formatBytes(doc.source)));labels.append(title,meta);
  open.append(preview,labels);
  const more=document.createElement('button');more.type='button';more.className='control icon doc-menu';more.dataset.moreId=doc.id;
  more.setAttribute('aria-label',t('openActions',{name:doc.name}));more.textContent='⋮';
  li.append(open,more);list.append(li);
 }
 $('empty').hidden=matching.length>0;
 $('empty-title').textContent=t(term?'noResults':'emptyTitle');$('empty-text').textContent=t(term?'noResultsHelp':'emptyText');$('empty-new').hidden=Boolean(term);
}
function guessFormat(text,name=''){
 if(/\.svg$/i.test(name))return 'svg';if(/\.html?$/i.test(name))return 'html';if(name&&knownExt.test(name))return 'text';
 return svgLike.test(text.slice(0,4096))?'svg':htmlLike.test(text.slice(0,4096))?'html':'text';
}
function addDoc(name,format,source='',mode='preview'){
 if(state.docs.length>=MAX){toast('storedLimit',{number:MAX});return null;}
 const doc={id:makeId(),name:validName(name),format,source};
 state.docs.unshift(doc);openDetail(doc,mode);if(!saveOK)toast('storageError');return doc;
}
function syncCount(){$('source-count').textContent=t('lines',{number:$('editor').value.split('\n').length.toLocaleString(lang)});}
function updateEditor(){
 const doc=current();if(!doc)return;
 const source=$('editor').value;
 if(source.length>LIMIT){$('editor').value=source.slice(0,LIMIT);toast('characterLimit');}
 doc.source=$('editor').value;syncCount();queueSave();
}
function insertPaste(text){
 const doc=current(), editor=$('editor');if(!doc||typeof text!=='string')return;
 if(editor.value.length+text.length-(editor.selectionEnd-editor.selectionStart)>LIMIT){toast('characterLimit');return;}
 if(!editor.value.trim()&&text.trim()){doc.format=guessFormat(text);$('format-select').value=doc.format;}
 editor.setRangeText(text,editor.selectionStart,editor.selectionEnd,'end');updateEditor();
}
function createFromPaste(text){
 if(!text.trim())return;
 if(text.length>LIMIT||new TextEncoder().encode(text).length>LIMIT){toast('sizeLimit');return;}
 addDoc(t('untitled'),guessFormat(text),text);
}
function openMenu(from,doc){
 if(doc)state.current=doc.id;
 if(!current())return;
 menuOpenedBy=from;$('document-menu').showModal();
}
function showName(mode){
 if(mode==='new'&&state.docs.length>=MAX){toast('storedLimit',{number:MAX});return;}
 const doc=current();
 naming=mode;previousFocus=document.activeElement;
 $('name-title').textContent=t(mode==='new'?'newFile':'rename');
 $('name-input').value=mode==='new'?'':doc?.name||'';
 $('name-input').placeholder=t('untitled');$('name-format').value=mode==='new'?'html':doc?.format||'text';
 $('name-format').disabled=mode!=='new';$('name-dialog').showModal();$('name-input').focus();
}
$('library-toggle').addEventListener('click',leaveDetail);
$('search').addEventListener('input',renderLibrary);
$('about-button').addEventListener('click',()=>$('about-dialog').showModal());
$('new-button').addEventListener('click',()=>showName('new'));
$('empty-new').addEventListener('click',()=>showName('new'));
$('document-menu-button').addEventListener('click',event=>openMenu(event.currentTarget));
$('library-paste').addEventListener('click',async()=>{
 if(state.docs.length>=MAX){toast('storedLimit',{number:MAX});return;}
 try{const text=await navigator.clipboard.readText();if(text.trim()){createFromPaste(text);return;}}catch{}
 $('paste-input').value='';$('paste-dialog').showModal();$('paste-input').focus();
});
$('paste-form').addEventListener('submit',event=>{
 event.preventDefault();const text=$('paste-input').value;
 if(!text.trim())return;$('paste-dialog').close();createFromPaste(text);
});
$('language-button').addEventListener('click',()=>{
 lang=lang==='zh'?'en':'zh';prefsSet(PREF+'lang',lang);
 const url=new URL(location.href);url.searchParams.set('lang',lang);
 try{history.replaceState(history.state,'',url);}catch{}
 localize();renderLibrary();syncCount();if(current())renderPreview(true);
});
for(const mode of ['list','grid'])$('view-'+mode).addEventListener('click',()=>{layout=mode;prefsSet(PREF+'layout',mode);renderLibrary();});
$('documents').addEventListener('click',event=>{
 const open=event.target.closest('[data-doc-id]'), more=event.target.closest('[data-more-id]');
 if(open){const doc=state.docs.find(d=>d.id===open.dataset.docId);if(doc)openDetail(doc);}
 if(more){const doc=state.docs.find(d=>d.id===more.dataset.moreId);if(doc)openMenu(more,doc);}
});
$('document-menu').addEventListener('close',()=>menuOpenedBy?.focus({preventScroll:true}));
$('tab-preview').addEventListener('click',()=>selectTab('preview'));
$('tab-code').addEventListener('click',()=>selectTab('code'));
document.querySelector('[role=tablist]').addEventListener('keydown',event=>{
 if(!['ArrowLeft','ArrowRight','Home','End'].includes(event.key))return;
 event.preventDefault();selectTab(event.key==='Home'?'code':event.key==='End'?'preview':tab==='code'?'preview':'code',true);
});
$('fit-window').addEventListener('click',()=>setFit('window'));
$('fit-content').addEventListener('click',()=>setFit('content'));
$('zoom-out').addEventListener('click',()=>manualZoom(-.25));
$('zoom-in').addEventListener('click',()=>manualZoom(.25));
$('fullscreen-button').addEventListener('click',()=>setFullscreen(!fullscreen));
$('refresh-button').addEventListener('click',()=>{renderPreview(true);toast('refreshed');});
$('editor').addEventListener('input',updateEditor);
$('apply-button').addEventListener('click',()=>{if(!persist())toast('storageError');selectTab('preview',true);});
$('format-select').addEventListener('change',()=>{
 const doc=current(),format=$('format-select').value;
 if(!doc||!TYPES.has(format))return;
 doc.format=format;fit=baseFit='window';zoom=1;persist();
});
$('paste-button').addEventListener('click',async()=>{
 try{insertPaste(await navigator.clipboard.readText());}catch{toast('pasteEditor');}
 $('editor').focus();
});
$('editor').addEventListener('paste',event=>{
 if(!event.clipboardData)return;event.preventDefault();insertPaste(event.clipboardData.getData('text/plain'));
});
$('import-button').addEventListener('click',()=>$('file-picker').click());
$('file-picker').addEventListener('change',async()=>{
 const picker=$('file-picker'),file=picker.files[0];picker.value='';if(!file)return;
 if(file.size>LIMIT){toast('sizeLimit');return;}
 if(!knownExt.test(file.name)&&!/^(?:text\/|image\/svg\+xml$)/i.test(file.type)){toast('fileType');return;}
 try{
  const text=await file.text();
  if(/\u0000/.test(text)){toast('fileType');return;}
  addDoc(file.name,guessFormat(text,file.name),text);
 }catch{toast('importError');}
});
$('rename-button').addEventListener('click',()=>showName('rename'));
$('name-form').addEventListener('submit',event=>{
 event.preventDefault();const name=validName($('name-input').value);
 $('name-dialog').close('confirmed');
 if(naming==='new'){if(addDoc(name,$('name-format').value,'','code'))$('editor').focus();}
 else if(current()){
  current().name=name;persist();$('document-title').textContent=name;renderLibrary();$('document-menu').close();
 }
});
$('name-dialog').addEventListener('close',()=>{if($('name-dialog').returnValue!=='confirmed')previousFocus?.focus();$('name-dialog').returnValue='';});
$('remove-button').addEventListener('click',()=>{
 const doc=current();if(!doc)return;
 pendingDelete=doc.id;$('delete-message').textContent=t('deleteConfirm',{name:doc.name});$('delete-dialog').showModal();
});
$('delete-confirm').addEventListener('click',()=>{
 const index=state.docs.findIndex(doc=>doc.id===pendingDelete);if(index<0)return;
 state.docs.splice(index,1);state.current=null;
 $('delete-dialog').close();$('document-menu').close();persist();renderLibrary();
 if($('workspace').dataset.screen==='detail')leaveDetail();else $('new-button').focus();
 toast('deleted');
});
for(const button of document.querySelectorAll('[data-close]'))button.addEventListener('click',()=>$(button.dataset.close).close());
$('export-button').addEventListener('click',()=>{
 const doc=current();if(!doc)return;
 const extension=doc.format==='text'?'txt':doc.format;
 const mime=doc.format==='html'?'text/html':doc.format==='svg'?'image/svg+xml':'text/plain';
 const name=knownExt.test(doc.name)?doc.name:doc.name+'.'+extension;
 const anchor=document.createElement('a'),url=URL.createObjectURL(new Blob([doc.source],{type:mime+';charset=utf-8'}));
 anchor.href=url;anchor.download=name;anchor.hidden=true;document.body.append(anchor);anchor.click();anchor.remove();
 setTimeout(()=>URL.revokeObjectURL(url),10000);toast('exported');
});
window.addEventListener('beforeinstallprompt',event=>{event.preventDefault();installPrompt=event;});
$('install-button').addEventListener('click',async()=>{
 if(installPrompt){const prompt=installPrompt;installPrompt=null;try{await prompt.prompt();return;}catch{}}
 $('install-dialog').showModal();
});
window.addEventListener('online',updateConnection);window.addEventListener('offline',updateConnection);
if('serviceWorker'in navigator&&location.protocol!=='file:'){
 navigator.serviceWorker.register('./sw.js').then(registration=>{
  if(navigator.onLine)registration.update().catch(()=>{});
  return navigator.serviceWorker.ready;
 }).then(()=>{offlineReady=true;updateConnection();}).catch(()=>{});
}
localize();renderLibrary();persist();
