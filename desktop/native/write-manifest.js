const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

const setupExe = path.resolve(__dirname, '../dist/CodeCanvas-Setup-0.5.0.exe');
const manifestPath = path.resolve(__dirname, '../../docs/update/desktop-latest.json');

const buf = fs.readFileSync(setupExe);
const sha256 = crypto.createHash('sha256').update(buf).digest('hex');

const manifest = {
  versionCode: 50,
  versionName: '0.5.0',
  setupUrl: 'https://relay.zhuquan.xyz/dl/CodeCanvas-Setup-0.5.0.exe',
  githubUrl: 'https://github.com/zhuquan7237/code-canvas/releases/download/v0.5.0/CodeCanvas-Setup-0.5.0.exe',
  sha256,
  fileSizeBytes: buf.length,
  publishedAt: '2026-10-11T20:30:00+08:00',
  changelog: [
    '1. 全新 Windows 极速原生安装包 (体积从 81.5MB 缩减至 792KB，毫秒级秒开，自动创建桌面快捷方式)',
    '2. 新增 3 合 1 零摩擦唤起：复制 AI 代码右下角弹出【灵动预览浮窗】+ 全局快捷键 Alt+V 一键唤醒 + 系统托盘常驻',
    '3. 全新原创【折光画架】专属图标与设置内一键同步更新功能'
  ].join('\n')
};

fs.mkdirSync(path.dirname(manifestPath), { recursive: true });
fs.writeFileSync(manifestPath, JSON.stringify(manifest, null, 2) + '\n', 'utf8');
console.log('✓ Manifest written:', manifestPath, 'SHA256:', sha256, 'Size:', buf.length);
