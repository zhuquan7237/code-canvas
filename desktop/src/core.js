/**
 * Shared pure logic for Code Canvas Desktop (Atelier v0.4.0).
 * Mirrors Android's RenderKindDetector, AiCodeExtractor, ClipboardCodeOffer, and PreviewHtml.
 */
(function (root, factory) {
  if (typeof module === 'object' && module.exports) {
    module.exports = factory();
  } else {
    root.CanvasCore = factory();
  }
})(typeof self !== 'undefined' ? self : this, function () {
  'use strict';

  const RenderKind = {
    HTML: 'HTML',
    SVG: 'SVG',
    XML: 'XML',
    RAW: 'RAW'
  };

  function stripLeadingCommentsAndDoctype(input) {
    let s = (input || '').replace(/^\uFEFF/, '').trimStart();
    while (true) {
      if (s.startsWith('<!--')) {
        const end = s.indexOf('-->', 4);
        if (end < 0) return '';
        s = s.slice(end + 3).trimStart();
      } else if (/^<\?xml\b/i.test(s)) {
        const end = s.indexOf('?>');
        if (end < 0) return '';
        s = s.slice(end + 2).trimStart();
      } else {
        break;
      }
    }
    return s;
  }

  function detectRenderKind(title, content) {
    const name = (title || '').trim().toLowerCase();
    const raw = (content || '').trim();
    const body = stripLeadingCommentsAndDoctype(raw);

    if (name.endsWith('.svg')) return RenderKind.SVG;
    if (name.endsWith('.html') || name.endsWith('.htm')) return RenderKind.HTML;
    if (name.endsWith('.xml')) {
      if (/^<svg[\s>]/i.test(body)) return RenderKind.SVG;
      return RenderKind.XML;
    }

    if (/^<svg[\s>]/i.test(body)) return RenderKind.SVG;
    if (/^<!doctype\s+html/i.test(body) || /^<(html|head|body|div|canvas|script|style|main|section|article|header|h[1-6]|p|table|form|button)\b/i.test(body)) {
      return RenderKind.HTML;
    }
    if (/^<\?xml\b/i.test(raw) || (/^<([a-zA-Z_][\w:.-]*)[\s>]/.test(body) && /<\/[a-zA-Z_][\w:.-]*>\s*$/.test(body))) {
      return RenderKind.XML;
    }
    return RenderKind.RAW;
  }

  function hasFencedBlock(rawText) {
    if (!rawText) return false;
    return /```[\s\S]*?```/.test(rawText);
  }

  function extractCodeBlocks(rawText) {
    if (!rawText || typeof rawText !== 'string') return [];
    const regex = /```([a-zA-Z0-9_+-]*)[ \t]*\r?\n([\s\S]*?)```/g;
    const blocks = [];
    let match;
    while ((match = regex.exec(rawText)) !== null) {
      const language = (match[1] || '').trim().toLowerCase();
      const code = (match[2] || '').trim();
      if (code.length > 0) {
        blocks.push({ language, code });
      }
    }
    return blocks;
  }

  function extractMarkupRegion(rawText) {
    if (!rawText || typeof rawText !== 'string') return null;
    const trimmed = rawText.trim();
    const firstLt = trimmed.indexOf('<');
    const lastGt = trimmed.lastIndexOf('>');
    if (firstLt > 0 && lastGt > firstLt) {
      const candidate = trimmed.slice(firstLt, lastGt + 1).trim();
      const kind = detectRenderKind('', candidate);
      if (kind !== RenderKind.RAW) return candidate;
    }
    return null;
  }

  function looksLikeClipboardCode(rawText) {
    if (!rawText || typeof rawText !== 'string') return false;
    const trimmed = rawText.trim();
    if (trimmed.length < 12 || trimmed.length > 2 * 1024 * 1024) return false;
    if (hasFencedBlock(trimmed)) {
      const blocks = extractCodeBlocks(trimmed);
      return blocks.length > 0;
    }
    return detectRenderKind('', trimmed) !== RenderKind.RAW;
  }

  function inferExtension(code, lang) {
    const l = (lang || '').trim().toLowerCase();
    if (l.includes('html') || l.includes('htm')) return '.html';
    if (l.includes('svg')) return '.svg';
    if (l.includes('xml')) return '.xml';
    const kind = detectRenderKind('', code);
    if (kind === RenderKind.SVG) return '.svg';
    if (kind === RenderKind.HTML) return '.html';
    if (kind === RenderKind.XML) return '.xml';
    return '.txt';
  }

  function escapeHtml(str) {
    return String(str || '')
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');
  }

  function formatBytes(bytes) {
    const n = Number(bytes) || 0;
    if (n < 1024) return n + ' B';
    if (n < 1024 * 1024) return (n / 1024).toFixed(1) + ' KB';
    return (n / (1024 * 1024)).toFixed(2) + ' MB';
  }

  function buildPreviewDocument(content, kind, options) {
    const opts = options || {};
    const isDark = Boolean(opts.isDark);
    const bg = isDark ? '#151E2C' : '#FFFFFF';
    const fg = isDark ? '#F2F6FB' : '#1A2433';
    const subtle = isDark ? '#1E2939' : '#EDF1F7';
    const border = isDark ? '#2E3D52' : '#DCE3EC';

    if (kind === RenderKind.SVG) {
      let svgMarkup = (content || '').trim();
      if (/^<svg\b/i.test(svgMarkup) && !/\bviewBox\s*=/i.test(svgMarkup)) {
        const wMatch = svgMarkup.match(/\bwidth\s*=\s*["']([0-9.]+)(?:px)?["']/i);
        const hMatch = svgMarkup.match(/\bheight\s*=\s*["']([0-9.]+)(?:px)?["']/i);
        if (wMatch && hMatch) {
          svgMarkup = svgMarkup.replace(/^<svg\b/i, `<svg viewBox="0 0 ${wMatch[1]} ${hMatch[1]}"`);
        }
      }
      return `<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<style>
  html, body {
    margin: 0; padding: 0; width: 100%; height: 100%;
    display: flex; align-items: center; justify-content: center;
    background: ${bg}; color: ${fg}; overflow: auto;
    box-sizing: border-box;
  }
  .svg-stage {
    width: 100%; height: 100%; padding: 16px;
    display: flex; align-items: center; justify-content: center;
    box-sizing: border-box;
  }
  .svg-stage > svg {
    max-width: 100%; max-height: 100%;
    width: auto; height: auto;
    display: block;
  }
</style>
</head>
<body><div class="svg-stage">${svgMarkup}</div></body>
</html>`;
    }

    if (kind === RenderKind.XML) {
      const hasEvilEntity = /<!ENTITY\b/i.test(content || '');
      const statusHtml = hasEvilEntity
        ? `<div class="xml-banner danger">⚠ 检测到外部实体声明 (DOCTYPE/ENTITY)，已由安全沙箱拦截</div>`
        : `<div class="xml-banner ok">✓ XML 结构视图（已启用防 XXE 安全沙箱）</div>`;
      return `<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<style>
  body {
    margin: 0; padding: 20px; background: ${bg}; color: ${fg};
    font-family: "JetBrains Mono", "Cascadia Code", Consolas, monospace;
    font-size: 13px; line-height: 1.55;
  }
  .xml-banner {
    padding: 10px 14px; border-radius: 10px; font-weight: 600;
    margin-bottom: 14px; font-family: system-ui, -apple-system, sans-serif;
  }
  .xml-banner.ok { background: ${isDark ? '#132A20' : '#E6F4EA'}; color: ${isDark ? '#6DD58C' : '#137333'}; border: 1px solid ${border}; }
  .xml-banner.danger { background: ${isDark ? '#3A1D1E' : '#FBE4E1'}; color: ${isDark ? '#FF9E9E' : '#B42318'}; }
  pre {
    margin: 0; padding: 16px; border-radius: 12px;
    background: ${subtle}; border: 1px solid ${border};
    white-space: pre-wrap; word-break: break-word;
  }
</style>
</head>
<body>${statusHtml}<pre>${escapeHtml(content)}</pre></body>
</html>`;
    }

    if (kind === RenderKind.RAW) {
      return `<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<style>
  body {
    margin: 0; padding: 20px; background: ${bg}; color: ${fg};
    font-family: "JetBrains Mono", "Cascadia Code", Consolas, monospace;
    font-size: 13px; line-height: 1.6;
  }
  pre { margin: 0; white-space: pre-wrap; word-break: break-word; }
</style>
</head>
<body><pre>${escapeHtml(content)}</pre></body>
</html>`;
    }

    // HTML
    const rawHtml = content || '';
    if (/<html[\s>]/i.test(rawHtml)) {
      return rawHtml;
    }
    return `<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<style>body { margin: 0; font-family: system-ui, -apple-system, "Segoe UI", sans-serif; }</style>
</head>
<body>${rawHtml}</body>
</html>`;
  }

  return {
    RenderKind,
    detectRenderKind,
    hasFencedBlock,
    extractCodeBlocks,
    extractMarkupRegion,
    looksLikeClipboardCode,
    inferExtension,
    formatBytes,
    buildPreviewDocument
  };
});
