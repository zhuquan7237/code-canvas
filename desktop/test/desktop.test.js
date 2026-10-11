const assert = require('assert');
const Core = require('../src/core.js');

// 1. RenderKind Detection
assert.strictEqual(Core.detectRenderKind('demo.html', '<h1>Hi</h1>'), Core.RenderKind.HTML);
assert.strictEqual(Core.detectRenderKind('icon.svg', '<circle r="5"/>'), Core.RenderKind.SVG);
assert.strictEqual(Core.detectRenderKind('untitled.txt', '<svg viewBox="0 0 10 10"></svg>'), Core.RenderKind.SVG);
assert.strictEqual(Core.detectRenderKind('config.xml', '<root><item/></root>'), Core.RenderKind.XML);
assert.strictEqual(Core.detectRenderKind('notes.txt', 'Just plain chat prose here'), Core.RenderKind.RAW);

// 2. AI Fenced Code Extraction & Clipboard Code Offer
const chatProse = '这是一段普通的聊天记录，没有任何代码，不应该触发剪贴板横幅。';
assert.strictEqual(Core.looksLikeClipboardCode(chatProse), false);

const aiResponse = '好的，这是为你生成的 SVG 图标：\n```svg\n<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="10"/></svg>\n```\n可以直接使用。';
assert.strictEqual(Core.looksLikeClipboardCode(aiResponse), true);
const blocks = Core.extractCodeBlocks(aiResponse);
assert.strictEqual(blocks.length, 1);
assert.strictEqual(blocks[0].language, 'svg');
assert.strictEqual(Core.inferExtension(blocks[0].code, blocks[0].language), '.svg');

// 3. Responsive SVG Preview Wrapping & XXE Guard
const svgDoc = Core.buildPreviewDocument('<svg width="200" height="100"><rect width="200" height="100"/></svg>', Core.RenderKind.SVG, { isDark: true });
assert.ok(svgDoc.includes('viewBox="0 0 200 100"'), 'SVG without viewBox must synthesize viewBox from width/height');

const xmlEvil = Core.buildPreviewDocument('<!DOCTYPE foo [<!ENTITY xxe SYSTEM "file:///etc/passwd">]><foo>&xxe;</foo>', Core.RenderKind.XML, { isDark: false });
assert.ok(xmlEvil.includes('已由安全沙箱拦截'), 'XML with ENTITY declaration must trigger XXE warning banner');

// 4. XML Visual Tree Inspector & Color Swatch Rendering (not duplicate raw text)
const sampleXml = `<?xml version="1.0" encoding="UTF-8"?>
<atelierDesignSystem version="0.4.0">
  <palette mode="DualTheme">
    <token name="canvas_bg" day="#F5F7FA" night="#0D1420" />
  </palette>
  <radii unit="dp">
    <thumbnail>12</thumbnail>
  </radii>
</atelierDesignSystem>`;
const xmlInspector = Core.buildPreviewDocument(sampleXml, Core.RenderKind.XML, { isDark: false });
assert.ok(xmlInspector.includes('xml-tree-node'), 'XML preview must render structured collapsible tree nodes instead of plain duplicate <pre>');
assert.ok(xmlInspector.includes('xml-color-swatch'), 'XML preview must render visual color swatches for hex attributes like #F5F7FA');
assert.ok(!xmlInspector.includes('font-family: SimSun'), 'XML preview must use modern font stack and never SimSun');

// 5. Code Formatter & Syntax Highlighter
assert.strictEqual(typeof Core.formatMarkup, 'function', 'Core.formatMarkup must exist');
const messyXml = '<root><child a="1"><item>hello</item></child></root>';
const formatted = Core.formatMarkup(messyXml, Core.RenderKind.XML);
assert.ok(formatted.includes('\n  <child a="1">'), 'formatMarkup must indent nested elements');

assert.strictEqual(typeof Core.highlightMarkup, 'function', 'Core.highlightMarkup must exist');
const highlighted = Core.highlightMarkup('<div class="card">Hello</div>');
assert.ok(highlighted.includes('hl-tag') && highlighted.includes('hl-attr') && highlighted.includes('hl-str'), 'highlightMarkup must wrap tags, attrs, and strings in syntax spans');

// 6. Visual Thumbnail Generator for Sidebar Items
assert.strictEqual(typeof Core.buildThumbnailMarkup, 'function', 'Core.buildThumbnailMarkup must exist');
const svgThumb = Core.buildThumbnailMarkup({ title: 'icon.svg', content: '<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="10"/></svg>' }, Core.RenderKind.SVG);
assert.ok(svgThumb.includes('<svg'), 'SVG thumbnail must embed miniature SVG artwork');

// 7. Smart Clipboard Summary for Floating Quick-Preview Pill
assert.strictEqual(typeof Core.detectClipboardSummary, 'function', 'Core.detectClipboardSummary must exist');
assert.strictEqual(Core.detectClipboardSummary('普通微信聊天文字，不含代码').isCode, false);
const svgSummary = Core.detectClipboardSummary('```svg\n<svg viewBox="0 0 100 100"><circle cx="50" cy="50" r="40"/></svg>\n```');
assert.strictEqual(svgSummary.isCode, true);
assert.strictEqual(svgSummary.kind, Core.RenderKind.SVG);
assert.ok(svgSummary.label.includes('SVG'));

// 8. Desktop Update Manifest Validation & Trusted Host Check
assert.strictEqual(typeof Core.parseDesktopUpdateManifest, 'function', 'Core.parseDesktopUpdateManifest must exist');
const validManifest = Core.parseDesktopUpdateManifest(JSON.stringify({
  schemaVersion: 1,
  versionName: '0.5.0',
  versionCode: 50,
  setupUrl: 'https://relay.zhuquan.xyz/dl/CodeCanvas-Setup-0.5.0.exe',
  fallbackSetupUrl: 'https://github.com/zhuquan7237/code-canvas/releases/download/v0.5.0/CodeCanvas-Setup-0.5.0.exe',
  sha256: 'a'.repeat(64),
  size: 512000,
  notes: 'v0.5.0 轻量原生版'
}));
assert.strictEqual(validManifest.versionCode, 50);
assert.strictEqual(validManifest.downloadUrls.length, 2);
assert.throws(() => {
  Core.parseDesktopUpdateManifest(JSON.stringify({
    schemaVersion: 1,
    versionName: '0.5.0',
    versionCode: 50,
    setupUrl: 'https://evil.example.com/malware.exe',
    sha256: 'a'.repeat(64),
    size: 512000
  }));
}, /不受信任/);

console.log('✓ All Code Canvas Desktop core unit tests passed (28 assertions).');
