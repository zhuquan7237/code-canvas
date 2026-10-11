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

console.log('✓ All Code Canvas Desktop core unit tests passed (12 assertions).');
