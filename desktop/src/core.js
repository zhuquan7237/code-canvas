/**
 * Shared pure logic for Code Canvas Desktop (Atelier v0.4.0).
 * Mirrors Android's RenderKindDetector, AiCodeExtractor, ClipboardCodeOffer, and PreviewHtml,
 * plus desktop-grade XML tree inspector, syntax highlighter, code formatter, and visual thumbnails.
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

  /**
   * Syntax highlighter for HTML / SVG / XML / CSS / JS in the desktop code editor.
   */
  function highlightMarkup(code) {
    const src = String(code || '');
    if (!src) return '';
    const tokenRe = /(<!--[\s\S]*?-->)|(<\?[\s\S]*?\?>)|(<!DOCTYPE[\s\S]*?>)|(<\/?[a-zA-Z_][\w:.-]*(?:\s+[^<>]*?)?>)/gi;
    let out = '';
    let lastIdx = 0;
    let match;

    while ((match = tokenRe.exec(src)) !== null) {
      if (match.index > lastIdx) {
        out += escapeHtml(src.slice(lastIdx, match.index));
      }
      const [full, comment, pi, doctype, tag] = match;
      if (comment) {
        out += `<span class="hl-comment">${escapeHtml(comment)}</span>`;
      } else if (pi || doctype) {
        out += `<span class="hl-decl">${escapeHtml(pi || doctype)}</span>`;
      } else if (tag) {
        out += highlightTagToken(tag);
      } else {
        out += escapeHtml(full);
      }
      lastIdx = tokenRe.lastIndex;
    }
    if (lastIdx < src.length) {
      out += escapeHtml(src.slice(lastIdx));
    }
    return out;
  }

  function highlightTagToken(tagText) {
    const m = tagText.match(/^(<\/?)([a-zA-Z_][\w:.-]*)([\s\S]*?)(\/?>)$/);
    if (!m) return escapeHtml(tagText);
    const [, openBracket, tagName, attrRegion, closeBracket] = m;
    const attrHighlighted = attrRegion.replace(
      /([a-zA-Z_:][\w:.-]*)(\s*=\s*)("[^"]*"|'[^']*'|[^\s"'=<>`]+)/g,
      (_all, attrName, eq, attrVal) =>
        `<span class="hl-attr">${escapeHtml(attrName)}</span><span class="hl-punct">${escapeHtml(eq)}</span><span class="hl-str">${escapeHtml(attrVal)}</span>`
    );
    return `<span class="hl-punct">${escapeHtml(openBracket)}</span><span class="hl-tag">${escapeHtml(tagName)}</span>${attrHighlighted}<span class="hl-punct">${escapeHtml(closeBracket)}</span>`;
  }

  /**
   * Pretty-print and indent XML / SVG / HTML markup cleanly.
   */
  function formatMarkup(code, kind) {
    let text = String(code || '').trim();
    if (!text) return '';
    // Repair accidental space after '<' such as '< radii unit="dp">'
    text = text.replace(/<\s+([a-zA-Z_][\w:.-]*)/g, '<$1').replace(/<\/\s+([a-zA-Z_][\w:.-]*)/g, '</$1');
    if (kind === RenderKind.RAW) return text;

    // Preserve leaf elements with short inline text on a single line
    const tokens = text
      .replace(/>\s*</g, '>\n<')
      .split('\n')
      .map(l => l.trim())
      .filter(Boolean);

    const voidTags = new Set(['area', 'base', 'br', 'col', 'embed', 'hr', 'img', 'input', 'link', 'meta', 'param', 'source', 'track', 'wbr']);
    let indent = 0;
    const lines = [];

    for (const token of tokens) {
      const isClosing = /^<\/[a-zA-Z_][\w:.-]*\s*>$/.test(token);
      const isDeclaration = /^<\?|^<!/.test(token);
      const isSelfClosing = /\/>$/.test(token);
      const isInlinePair = /^<([a-zA-Z_][\w:.-]*)(\s[^>]*)?>[^<]*<\/\1\s*>$/.test(token);
      const openMatch = token.match(/^<([a-zA-Z_][\w:.-]*)\b/);
      const tagName = openMatch ? openMatch[1].toLowerCase() : '';
      const isVoid = kind === RenderKind.HTML && voidTags.has(tagName);

      if (isClosing) {
        indent = Math.max(0, indent - 1);
      }
      lines.push('  '.repeat(indent) + token);
      if (!isClosing && !isDeclaration && !isSelfClosing && !isInlinePair && !isVoid && openMatch) {
        indent += 1;
      }
    }
    return lines.join('\n');
  }

  /**
   * Safe lightweight XML parser that produces a structured AST or diagnostic error.
   */
  function parseXmlStructure(xmlText) {
    const cleaned = String(xmlText || '').trim();
    if (!cleaned) {
      return { ok: false, error: 'XML 内容为空', nodes: [], totalElements: 0 };
    }
    // Check for accidental '< tag' syntax error
    const badBracketMatch = cleaned.match(/<\s+([a-zA-Z_][\w:.-]*)/);
    if (badBracketMatch) {
      return {
        ok: false,
        error: `标签 "< ${badBracketMatch[1]}>" 左尖括号后存在多余空格，标准 XML 不允许 "<" 与标签名之间有空格（点击左栏顶部「格式化」可一键自动修复）`,
        nodes: [],
        totalElements: 0
      };
    }

    const body = stripLeadingCommentsAndDoctype(cleaned);
    const tokenRegex = /<!--([\s\S]*?)-->|<(\/?)([a-zA-Z_][\w:.-]*)([^<>]*?)(\/?)>|([^<]+)/g;
    const rootList = [];
    const stack = [{ children: rootList }];
    let totalElements = 0;
    let match;

    while ((match = tokenRegex.exec(body)) !== null) {
      const [, commentText, slash, tagName, attrRaw, selfSlash, textNode] = match;
      if (commentText !== undefined) {
        continue;
      }
      if (textNode !== undefined) {
        const t = textNode.trim();
        if (t && stack.length > 1) {
          stack[stack.length - 1].text = (stack[stack.length - 1].text ? stack[stack.length - 1].text + ' ' : '') + t;
        }
        continue;
      }
      if (slash === '/') {
        if (stack.length <= 1 || stack[stack.length - 1].tag !== tagName) {
          const expected = stack.length > 1 ? `</${stack[stack.length - 1].tag}>` : '无对应开始标签';
          return {
            ok: false,
            error: `闭合标签 </${tagName}> 不匹配（期望 ${expected}）`,
            nodes: rootList,
            totalElements
          };
        }
        stack.pop();
      } else {
        const attrs = [];
        const attrRe = /([a-zA-Z_:][\w:.-]*)\s*=\s*(?:"([^"]*)"|'([^']*)')/g;
        let am;
        while ((am = attrRe.exec(attrRaw || '')) !== null) {
          attrs.push({ name: am[1], value: am[2] !== undefined ? am[2] : am[3] });
        }
        const node = {
          tag: tagName,
          attrs,
          text: '',
          children: []
        };
        totalElements += 1;
        stack[stack.length - 1].children.push(node);
        if (selfSlash !== '/') {
          stack.push(node);
        }
      }
    }

    if (stack.length > 1) {
      return {
        ok: false,
        error: `标签 <${stack[stack.length - 1].tag}> 未闭合`,
        nodes: rootList,
        totalElements
      };
    }
    if (rootList.length === 0) {
      return {
        ok: false,
        error: '未识别到有效的 XML 根元素',
        nodes: [],
        totalElements: 0
      };
    }
    return {
      ok: true,
      error: null,
      rootTag: rootList[0].tag,
      nodes: rootList,
      totalElements
    };
  }

  function isHexColor(val) {
    return /^#([0-9a-fA-F]{3}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})$/.test(String(val || '').trim());
  }

  function renderXmlNodeHtml(node, depth) {
    const attrsHtml = node.attrs.map(a => {
      const swatch = isHexColor(a.value)
        ? `<span class="xml-color-swatch" style="background:${escapeHtml(a.value)}" title="颜色预览 ${escapeHtml(a.value)}"></span>`
        : '';
      return `<span class="xml-attr-pill"><span class="xml-attr-key">${escapeHtml(a.name)}</span><span class="xml-attr-eq">=</span>${swatch}<span class="xml-attr-val">"${escapeHtml(a.value)}"</span></span>`;
    }).join('');

    const hasChildren = node.children && node.children.length > 0;
    const textSwatch = isHexColor(node.text)
      ? `<span class="xml-color-swatch" style="background:${escapeHtml(node.text)}"></span>`
      : '';
    const textHtml = node.text
      ? `<span class="xml-node-value">${textSwatch}${escapeHtml(node.text)}</span>`
      : '';

    if (!hasChildren) {
      return `<div class="xml-tree-node leaf" style="--depth:${depth}">
        <div class="xml-node-header">
          <span class="xml-leaf-dot"></span>
          <span class="xml-tag-badge">&lt;${escapeHtml(node.tag)}&gt;</span>
          <div class="xml-attr-group">${attrsHtml}</div>
          ${textHtml}
        </div>
      </div>`;
    }

    const childrenHtml = node.children.map(ch => renderXmlNodeHtml(ch, depth + 1)).join('');
    return `<details class="xml-tree-node branch" open style="--depth:${depth}">
      <summary class="xml-node-header">
        <span class="xml-caret">▾</span>
        <span class="xml-tag-badge branch-tag">&lt;${escapeHtml(node.tag)}&gt;</span>
        <div class="xml-attr-group">${attrsHtml}</div>
        <span class="xml-child-count">${node.children.length} 项</span>
      </summary>
      <div class="xml-node-children">${childrenHtml}</div>
    </details>`;
  }

  function buildXmlInspectorHtml(content, opts) {
    const isDark = Boolean(opts && opts.isDark);
    const bg = isDark ? '#151E2C' : '#FFFFFF';
    const fg = isDark ? '#F2F6FB' : '#1A2433';
    const subtle = isDark ? '#1E2939' : '#F4F7FB';
    const cardBg = isDark ? '#192436' : '#F9FBFE';
    const border = isDark ? '#2E3D52' : '#DCE3EC';
    const primary = isDark ? '#8EB4FF' : '#2456C6';
    const attrKeyColor = isDark ? '#7DD3FC' : '#0369A1';
    const attrValColor = isDark ? '#FCD34D' : '#B45309';

    const hasEvilEntity = /<!ENTITY\b/i.test(content || '');
    const parsed = parseXmlStructure(content);

    let statusBanner = '';
    if (hasEvilEntity) {
      statusBanner = `<div class="xml-banner danger">⚠ 检测到外部实体声明 (DOCTYPE/ENTITY)，已由安全沙箱拦截</div>`;
    } else if (!parsed.ok) {
      statusBanner = `<div class="xml-banner warn">⚠ XML 语法提示：${escapeHtml(parsed.error)}</div>`;
    } else {
      statusBanner = `<div class="xml-banner ok">
        <div class="xml-banner-left">
          <span>✓ XML 结构树视图</span>
          <span class="xml-meta-chip">共 ${parsed.totalElements} 个节点</span>
        </div>
        <div class="xml-banner-actions">
          <button class="xml-action-btn" onclick="document.querySelectorAll('details.xml-tree-node').forEach(d=>d.open=true)">全部展开</button>
          <button class="xml-action-btn" onclick="document.querySelectorAll('details.xml-tree-node').forEach((d,i)=>{if(i>0)d.open=false})">折叠子级</button>
        </div>
      </div>`;
    }

    const treeBody = parsed.nodes && parsed.nodes.length > 0
      ? `<div class="xml-tree-root">${parsed.nodes.map(n => renderXmlNodeHtml(n, 0)).join('')}</div>`
      : `<pre class="xml-fallback-pre">${escapeHtml(content)}</pre>`;

    return `<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<style>
  * { box-sizing: border-box; }
  ::-webkit-scrollbar { width: 6px; height: 6px; }
  ::-webkit-scrollbar-track { background: transparent; }
  ::-webkit-scrollbar-thumb { background: ${border}; border-radius: 999px; }
  body {
    margin: 0; padding: 14px 16px; background: ${bg}; color: ${fg};
    font-family: "Segoe UI", "PingFang SC", "Microsoft YaHei UI", "Microsoft YaHei", system-ui, sans-serif;
    font-size: 12.5px; line-height: 1.45;
  }
  .xml-banner {
    padding: 8px 12px; border-radius: 10px; font-weight: 600; font-size: 12px;
    margin-bottom: 10px; display: flex; align-items: center; justify-content: space-between;
    gap: 8px;
  }
  .xml-banner-left { display: flex; align-items: center; gap: 6px; min-width: 0; flex-wrap: wrap; }
  .xml-banner-actions { display: flex; gap: 5px; flex-shrink: 0; }
  .xml-action-btn {
    border: 1px solid ${border}; background: ${bg}; color: ${fg};
    border-radius: 7px; padding: 2px 8px; font-size: 11px; font-weight: 600; cursor: pointer;
    white-space: nowrap;
  }
  .xml-action-btn:hover { border-color: ${primary}; color: ${primary}; }
  .xml-meta-chip {
    font-size: 11px; padding: 1px 7px; border-radius: 999px;
    background: ${bg}; border: 1px solid ${border}; color: ${fg};
    font-family: "Cascadia Code", Consolas, "Microsoft YaHei UI", monospace;
    white-space: nowrap;
  }
  .xml-banner.ok { background: ${isDark ? '#132A20' : '#EBF7EE'}; color: ${isDark ? '#6DD58C' : '#137333'}; border: 1px solid ${isDark ? '#214A37' : '#BDE3C7'}; }
  .xml-banner.warn { background: ${isDark ? '#352714' : '#FEF3C7'}; color: ${isDark ? '#FCD34D' : '#92400E'}; border: 1px solid ${isDark ? '#5C421E' : '#FDE68A'}; }
  .xml-banner.danger { background: ${isDark ? '#3A1D1E' : '#FBE4E1'}; color: ${isDark ? '#FF9E9E' : '#B42318'}; border: 1px solid ${border}; }
  .xml-tree-root { display: flex; flex-direction: column; gap: 6px; }
  .xml-tree-node {
    border-radius: 10px; border: 1px solid ${border}; background: ${cardBg};
    overflow: hidden;
  }
  .xml-tree-node.leaf { background: ${subtle}; }
  .xml-node-header {
    padding: 5px 8px; display: flex; align-items: center; gap: 5px; flex-wrap: wrap;
    cursor: pointer; list-style: none; user-select: none;
  }
  .xml-node-header::-webkit-details-marker { display: none; }
  .xml-caret {
    font-size: 11px; color: ${primary}; width: 12px; text-align: center;
    transition: transform 0.15s ease;
  }
  details:not([open]) > summary .xml-caret { transform: rotate(-90deg); }
  .xml-leaf-dot {
    width: 5px; height: 5px; border-radius: 99px; background: ${primary};
    margin: 0 2px; opacity: 0.75; flex-shrink: 0;
  }
  .xml-tag-badge {
    font-family: "Cascadia Code", Consolas, "Microsoft YaHei UI", monospace;
    font-size: 11.5px; font-weight: 700; color: ${primary};
    background: ${isDark ? 'rgba(142, 180, 255, 0.12)' : 'rgba(36, 86, 198, 0.09)'};
    padding: 1px 6px; border-radius: 5px; white-space: nowrap;
  }
  .xml-attr-group { display: flex; align-items: center; gap: 4px; flex-wrap: wrap; }
  .xml-attr-pill {
    display: inline-flex; align-items: center; gap: 2px;
    padding: 1px 6px; border-radius: 5px; background: ${bg}; border: 1px solid ${border};
    font-family: "Cascadia Code", Consolas, "Microsoft YaHei UI", monospace;
    font-size: 11px; white-space: nowrap;
  }
  .xml-attr-key { color: ${attrKeyColor}; font-weight: 600; }
  .xml-attr-eq { opacity: 0.55; }
  .xml-attr-val { color: ${attrValColor}; }
  .xml-color-swatch {
    width: 11px; height: 11px; border-radius: 3px;
    border: 1px solid rgba(128, 128, 128, 0.45);
    display: inline-block; margin: 0 3px; flex-shrink: 0;
  }
  .xml-node-value {
    margin-left: auto; padding: 1px 9px; border-radius: 999px;
    background: ${bg}; border: 1px solid ${border};
    font-family: "Cascadia Code", Consolas, "Microsoft YaHei UI", monospace;
    font-weight: 700; font-size: 11.5px; color: ${fg};
    display: inline-flex; align-items: center; gap: 4px;
  }
  .xml-child-count {
    margin-left: auto; font-size: 11px; opacity: 0.65; white-space: nowrap;
  }
  .xml-node-children {
    padding: 5px 8px 8px 16px; border-top: 1px solid ${border};
    display: flex; flex-direction: column; gap: 5px;
  }
  .xml-fallback-pre {
    margin: 0; padding: 14px; border-radius: 10px; background: ${subtle}; border: 1px solid ${border};
    font-family: "Cascadia Code", Consolas, "Microsoft YaHei UI", monospace;
    font-size: 12.5px; line-height: 1.6; white-space: pre-wrap; word-break: break-word;
  }
</style>
</head>
<body>${statusBanner}${treeBody}</body>
</html>`;
  }

  /**
   * Build visual miniature thumbnail HTML for sidebar document items.
   */
  function buildThumbnailMarkup(doc, kind) {
    const content = String((doc && doc.content) || '').trim();
    if (kind === RenderKind.SVG && /^<svg\b/i.test(stripLeadingCommentsAndDoctype(content))) {
      let svg = stripLeadingCommentsAndDoctype(content);
      svg = svg.replace(/<script[\s\S]*?<\/script>/gi, '');
      if (!/\bviewBox\s*=/i.test(svg)) {
        const wMatch = svg.match(/\bwidth\s*=\s*["']([0-9.]+)(?:px)?["']/i);
        const hMatch = svg.match(/\bheight\s*=\s*["']([0-9.]+)(?:px)?["']/i);
        if (wMatch && hMatch) {
          svg = svg.replace(/^<svg\b/i, `<svg viewBox="0 0 ${wMatch[1]} ${hMatch[1]}"`);
        }
      }
      return `<div class="thumb-visual thumb-svg">${svg}</div>`;
    }

    if (kind === RenderKind.HTML) {
      const colors = (content.match(/#[0-9a-fA-F]{6}\b/g) || []).slice(0, 3);
      const bg = colors[0] || '#1E293B';
      const accent = colors[1] || '#3B82F6';
      return `<div class="thumb-visual thumb-html" style="background:${escapeHtml(bg)}">
        <span class="thumb-html-bar" style="background:${escapeHtml(accent)}"></span>
        <span class="thumb-html-line"></span>
        <span class="thumb-html-line short"></span>
      </div>`;
    }

    if (kind === RenderKind.XML) {
      const colors = (content.match(/#[0-9a-fA-F]{6}\b/g) || []).slice(0, 4);
      if (colors.length >= 2) {
        const dots = colors.map(c => `<span class="thumb-swatch-dot" style="background:${escapeHtml(c)}"></span>`).join('');
        return `<div class="thumb-visual thumb-xml-swatches">${dots}</div>`;
      }
      return `<div class="thumb-visual thumb-xml-tree">
        <span class="thumb-tree-branch"></span>
        <span class="thumb-tree-leaf"></span>
        <span class="thumb-tree-leaf"></span>
      </div>`;
    }

    return `<div class="thumb-visual thumb-raw"><span>TXT</span></div>`;
  }

  function buildPreviewDocument(content, kind, options) {
    const opts = options || {};
    const isDark = Boolean(opts.isDark);
    const bg = isDark ? '#151E2C' : '#FFFFFF';
    const fg = isDark ? '#F2F6FB' : '#1A2433';

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
    background: transparent; color: ${fg}; overflow: auto;
    box-sizing: border-box;
  }
  .svg-stage {
    width: 100%; height: 100%; padding: 20px;
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
      return buildXmlInspectorHtml(content, opts);
    }

    if (kind === RenderKind.RAW) {
      return `<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<style>
  body {
    margin: 0; padding: 20px; background: ${bg}; color: ${fg};
    font-family: "Cascadia Code", Consolas, "Microsoft YaHei UI", monospace;
    font-size: 13px; line-height: 1.6;
  }
  pre {
    margin: 0; white-space: pre-wrap; word-break: break-word;
    font-family: inherit;
  }
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
<style>body { margin: 0; font-family: system-ui, -apple-system, "Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif; }</style>
</head>
<body>${rawHtml}</body>
</html>`;
  }

  function detectClipboardSummary(rawText) {
    if (!looksLikeClipboardCode(rawText)) {
      return { isCode: false, kind: RenderKind.RAW, label: '', ext: '.txt', code: '' };
    }
    const blocks = extractCodeBlocks(rawText);
    const code = blocks.length > 0 ? blocks[0].code : (extractMarkupRegion(rawText) || String(rawText).trim());
    const lang = blocks.length > 0 ? blocks[0].language : '';
    const ext = inferExtension(code, lang);
    const kind = detectRenderKind('clip' + ext, code);
    const labelMap = {
      [RenderKind.SVG]: 'SVG 矢量图形',
      [RenderKind.HTML]: 'HTML 交互画布',
      [RenderKind.XML]: 'XML 结构配置',
      [RenderKind.RAW]: '代码片段'
    };
    return {
      isCode: true,
      kind,
      label: labelMap[kind] || '代码片段',
      ext,
      code
    };
  }

  function trustedDesktopSetupUrl(urlStr) {
    try {
      const u = new URL(String(urlStr || '').trim());
      if (u.protocol !== 'https:' || u.username || u.password || u.port) return false;
      const host = u.hostname.toLowerCase();
      const pathname = u.pathname;
      if (host === 'relay.zhuquan.xyz') {
        return pathname.startsWith('/dl/') && pathname.toLowerCase().endsWith('.exe');
      }
      if (host === 'github.com') {
        return pathname.startsWith('/zhuquan7237/code-canvas/releases/download/') && pathname.toLowerCase().endsWith('.exe');
      }
      return false;
    } catch (e) {
      return false;
    }
  }

  function parseDesktopUpdateManifest(jsonStr) {
    const obj = typeof jsonStr === 'string' ? JSON.parse(jsonStr) : jsonStr;
    if (!obj || obj.schemaVersion !== 1) {
      throw new Error('更新清单版本不支持');
    }
    const versionCode = Number(obj.versionCode);
    const versionName = String(obj.versionName || '').trim();
    const setupUrl = String(obj.setupUrl || obj.apkUrl || '').trim();
    const fallbackRaw = String(obj.fallbackSetupUrl || obj.fallbackApkUrl || '').trim();
    const sha256 = String(obj.sha256 || '').trim().toLowerCase();
    const size = Number(obj.size);
    const notes = String(obj.notes || '');

    if (!versionCode || versionCode <= 0 || !/^\d+\.\d+\.\d+$/.test(versionName) || !/^[0-9a-f]{64}$/.test(sha256) || !size || size <= 0) {
      throw new Error('更新清单字段无效');
    }
    if (!trustedDesktopSetupUrl(setupUrl)) {
      throw new Error('不受信任的更新地址');
    }
    const downloadUrls = [setupUrl];
    if (fallbackRaw && trustedDesktopSetupUrl(fallbackRaw) && fallbackRaw !== setupUrl) {
      downloadUrls.push(fallbackRaw);
    }
    return {
      schemaVersion: 1,
      versionCode,
      versionName,
      setupUrl,
      fallbackSetupUrl: downloadUrls[1] || null,
      downloadUrls,
      sha256,
      size,
      notes
    };
  }

  const CURRENT_VERSION = '0.5.0';
  const CURRENT_VERSION_CODE = 50;

  return {
    CURRENT_VERSION,
    CURRENT_VERSION_CODE,
    RenderKind,
    detectRenderKind,
    hasFencedBlock,
    extractCodeBlocks,
    extractMarkupRegion,
    looksLikeClipboardCode,
    inferExtension,
    formatBytes,
    highlightMarkup,
    formatMarkup,
    parseXmlStructure,
    buildXmlInspectorHtml,
    buildThumbnailMarkup,
    buildPreviewDocument,
    detectClipboardSummary,
    trustedDesktopSetupUrl,
    parseDesktopUpdateManifest
  };
});
