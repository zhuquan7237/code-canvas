'use strict';

// Geometry and lighting transferred from the native Design Lab's Artwork Canvas.
const studi = [
  { id:'coral', color:'#1a1f30', ink:'#e05240', light:'#ffcca1',
    en:['Coral study','Color and contrast','A coral ribbon curves across dark paper.'],
    zh:['珊瑚习作','色彩与对比','一条珊瑚色缎带从深色纸面掠过。'] },
  { id:'orbit', color:'#142440', ink:'#4d75f0', light:'#ffffff',
    en:['Quiet orbit','Shape and scale','A pale orbit circles a cobalt center.'],
    zh:['静谧轨道','形状与尺度','淡色轨道围绕钴蓝色中心。'] },
  { id:'folded', color:'#3d2b36', ink:'#c46385', light:'#ffd6ba',
    en:['Folded light','Layers and alignment','Three folded sheets overlap under a warm light.'],
    zh:['折叠之光','层叠与对齐','三张折叠的纸在暖光下交错。'] }
];
function studySVG(study, language) {
  const w=960, h=600, pt=(x,y)=>`${(x*w).toFixed(2)} ${(y*h).toFixed(2)}`;
  const {id,color,ink,light}=study;
  let body='', defs=`<radialGradient id="ground" gradientUnits="userSpaceOnUse" cx="259.2" cy="96" r="748.8"><stop stop-color="${ink}" stop-opacity=".56"/><stop offset="1" stop-color="${color}"/></radialGradient>`;
  if (id==='coral') {
    defs+=`<linearGradient id="ribbon" gradientUnits="userSpaceOnUse" x1="144" y1="36" x2="825.6" y2="492"><stop stop-color="${light}"/><stop offset=".5" stop-color="${ink}"/><stop offset="1" stop-color="${light}" stop-opacity=".65"/></linearGradient>`;
    body='<g fill="none" stroke="url(#ribbon)" stroke-width="12.48" stroke-linecap="round">';
    for (let i=0;i<14;i++) {
      const f=i/13;
      body+=`<path d="M${pt(.17+f*.24,.77+f*.10)} C${pt(.08+f*.37,.10+f*.60)},${pt(.77+f*.14,.81-f*.53)},${pt(.83-f*.20,.24-f*.04)}"/>`;
    }
    body+='</g>';
  } else if (id==='orbit') {
    defs+=`<radialGradient id="planet" gradientUnits="userSpaceOnUse" cx="441.6" cy="228" r="220.8"><stop stop-color="${light}"/><stop offset=".5" stop-color="${ink}"/><stop offset="1" stop-color="${color}"/></radialGradient>`;
    body='<circle cx="480" cy="300" r="138" fill="url(#planet)"/>';
    for(let i=1;i<=4;i++) body+=`<ellipse cx="480" cy="300" rx="${(w*(.48+i*.044)/2).toFixed(2)}" ry="${(h*(.22+i*.056)/2).toFixed(2)}" fill="none" stroke="#fff" stroke-opacity="${(.70-i*.09).toFixed(2)}" stroke-width="1.92"/>`;
  } else {
    for(let i=0;i<3;i++) {
      const f=i*.12;
      defs+=`<linearGradient id="fold${i}" gradientUnits="userSpaceOnUse" x1="${(.29+f)*w}" y1="84" x2="${(.75+f)*w}" y2="540"><stop stop-color="${light}"/><stop offset=".5" stop-color="${ink}"/><stop offset="1" stop-color="${color}"/></linearGradient>`;
      body+=`<path d="M${pt(.18+f,.67+f*.66)} L${pt(.35+f,.22+f*.29)} L${pt(.60+f,.24+f*.72)} L${pt(.54+f,.58+f*.85)}Z" fill="url(#fold${i})" stroke="${light}" stroke-opacity=".48" stroke-width="1.92"/>`;
    }
  }
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${w} ${h}" role="img" aria-labelledby="art-title art-description"><title id="art-title">${study[language][0]}</title><desc id="art-description">${study[language][2]}</desc><defs>${defs}</defs><path d="M0 0H960V600H0Z" fill="${color}"/><path d="M0 0H960V600H0Z" fill="url(#ground)"/>${body}</svg>`;
}
