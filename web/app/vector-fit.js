'use strict';

// Fit only geometry we can safely measure. This clone never receives user URLs,
// animation, arbitrary CSS or any elements able to load files. The actual
// preview always remains behind an origin-opaque sandbox.
const SVG_NS='http://www.w3.org/2000/svg';
const SHAPES=new Set(['g','path','rect','circle','ellipse','line','polyline','polygon']);
const SINGLETONS=new Set(['defs','title','desc','metadata']);
const GEOMETRY=new Set(['d','points','x','y','rx','ry','cx','cy','r','x1','x2','y1','y2','width','height','transform','stroke-width']);
const SAFE_GEOMETRY=/^[0-9eE+.,\s()a-zA-Z-]+$/;
const LIMIT_MEASURE=600;
const SIMPLE_VAL=/^(?:[+-]?(?:\d+(?:\.\d*)?|\.\d+)(?:e[+-]?\d+)?)(?:px)?$/i;
const DETAILED=new Set(['class','style','filter','mask','clip-path','marker-start','marker-mid','marker-end','visibility','display','vector-effect']);
function declaredFrame(svg) {
  const box=svg.getAttribute('viewBox')?.trim().split(/[\s,]+/).map(Number);
  if(box?.length===4 && box.every(Number.isFinite) && box[2]>0 && box[3]>0) return box;
  const width=svg.getAttribute('width');
  const height=svg.getAttribute('height');
  const w=SIMPLE_VAL.test(width||'')?parseFloat(width):300;
  const h=SIMPLE_VAL.test(height||'')?parseFloat(height):150;
  return [0,0,w>0?w:300,h>0?h:150];
}
function contentFrame(svg,frame) {
  if(svg.querySelector('style')||[...svg.attributes].some(a=>DETAILED.has(a.localName))) return null;
  const root=document.createElementNS(SVG_NS,'svg');
  root.classList.add('measure');
  root.setAttribute('width','1');root.setAttribute('height','1');
  const group=document.createElementNS(SVG_NS,'g');root.append(group);
  let count=0, stroke=0, found=false, unavailable=false;
  function copy(parent,node,depth=0,inheritedStroke=0,inheritedScale=1) {
    if(SINGLETONS.has(node.localName))return;
    if(++count>LIMIT_MEASURE||depth>40||node.namespaceURI!==SVG_NS||!SHAPES.has(node.localName)||[...node.attributes].some(a=>DETAILED.has(a.localName))){
      unavailable=true;return;
    }
    const clone=document.createElementNS(SVG_NS,node.localName);
    const declaredStroke=node.getAttribute('stroke');
    const stated=node.getAttribute('stroke-width');
    const weight=declaredStroke==='none'?0:stated&&SIMPLE_VAL.test(stated)?Math.max(0,parseFloat(stated)):declaredStroke&&inheritedStroke===0?1:inheritedStroke;
    // With thick custom/rotated strokes, getBBox() does not reliably include
    // the paint on all browsers. Use the authored frame instead of clipping.
    const customTransform=node.getAttribute('transform');
    if((weight>0&&customTransform)||(node.getAttribute('stroke-linejoin')==='miter'&&weight>0)){unavailable=true;return;}
    for (const a of node.attributes) {
      if(!GEOMETRY.has(a.localName)||a.namespaceURI)continue;
      if(!a.value||!SAFE_GEOMETRY.test(a.value)){unavailable=true;return;}
      clone.setAttribute(a.localName,a.value);
    }
    if(node.localName!=='g'){found=true;stroke=Math.max(stroke,weight*inheritedScale);}
    parent.append(clone);
    for(const child of node.children)if(!unavailable)copy(clone,child,depth+1,weight,inheritedScale);
  }
  const rootWeight=svg.getAttribute('stroke')&&svg.getAttribute('stroke')!=='none' ? parseFloat(svg.getAttribute('stroke-width'))||1 : 0;
  if(svg.hasAttribute('transform')) return null;
  for(const child of svg.children)if(!unavailable)copy(group,child,0,rootWeight);
  if(unavailable||!found) return null;
  document.body.append(root);
  let measured;
  try{measured=group.getBBox();}finally{root.remove();}
  if(!measured)return null;
  const {x,y,width:w,height:h}=measured;
  if(![x,y,w,h].every(Number.isFinite)||Math.max(w,h)<=0)return null;
  const pad=Math.max(stroke*2,Math.max(w,h)*.025, .00001);
  // Keep the intended SVG frame as a clip so fit does not reveal off-viewBox art.
  const left=Math.max(x-pad,frame[0]), top=Math.max(y-pad,frame[1]);
  const right=Math.min(x+w+pad,frame[0]+frame[2]), bottom=Math.min(y+h+pad,frame[1]+frame[3]);
  if(right<=left||bottom<=top)return null;
  return [left,top,right-left,bottom-top];
}
