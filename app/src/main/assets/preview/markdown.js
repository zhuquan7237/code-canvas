/*
 * Markdown preview renderer.
 *
 * Runs entirely from bundled assets: marked, KaTeX and Mermaid all come from the APK, so this
 * works with the phone in flight mode. The document source arrives in #cc-source as text/plain,
 * which keeps arbitrary content (quotes, backslashes, </script>) out of the script itself.
 *
 * Every stage reports its own failure into the page and leaves the rest working: a broken diagram
 * must not take the document's text down with it, and the source stays editable either way.
 */
(function () {
  'use strict';

  var sourceNode = document.getElementById('cc-source');
  var source = sourceNode ? sourceNode.textContent : '';
  var host = document.getElementById('cc-md');
  var errBox = document.getElementById('cc-md-err');

  function fail(message) {
    if (!errBox) return;
    errBox.textContent = message;
    errBox.style.display = 'block';
  }

  // --- 1. Markdown -> HTML ---------------------------------------------------
  try {
    if (!window.marked) {
      fail('Markdown 渲染库没有加载成功，可以切到源码查看内容。');
      return;
    }
    marked.setOptions({ gfm: true, breaks: false, headerIds: false, mangle: false });
    host.innerHTML = marked.parse(source);
  } catch (e) {
    fail('Markdown 解析失败：' + (e && e.message ? e.message : e));
    return;
  }

  // --- 2. Task lists --------------------------------------------------------
  // GFM leaves "- [x] item" as literal text in some marked versions; turn it into a real checkbox.
  try {
    var items = host.querySelectorAll('li');
    for (var i = 0; i < items.length; i++) {
      var li = items[i];
      var match = /^\s*\[([ xX])\]\s+/.exec(li.textContent || '');
      if (!match) continue;
      li.classList.add('task-list-item');
      li.textContent = li.textContent.replace(/^\s*\[[ xX]\]\s+/, '');
      var box = document.createElement('input');
      box.type = 'checkbox';
      box.disabled = true;
      box.checked = match[1].toLowerCase() === 'x';
      li.insertBefore(box, li.firstChild);
    }
  } catch (ignored) {}

  // --- 3. Formulas ----------------------------------------------------------
  // $$...$$ is display math, $...$ is inline. Both go through the bundled KaTeX.
  //
  // Two sources matter, and missing either one is why formulas silently stayed as literal text:
  // a fence written as ```math becomes <pre><code class="language-math">, while $$...$$ on its own
  // line becomes a plain <p> because it is not markdown syntax at all.
  try {
    if (window.katex) {
      renderMathInCodeBlocks();
      renderMathInParagraphs();
    }
  } catch (ignored) {}

  function renderMathInCodeBlocks() {
    var nodes = host.querySelectorAll('code');
    for (var j = 0; j < nodes.length; j++) {
      var node = nodes[j];
      var text = (node.textContent || '').trim();
      var isMathFence = /(^|\s)(language|lang)-math(\s|$)/.test(node.className || '');
      var display = isMathFence
        || (text.length > 4 && text.indexOf('$$') === 0 && text.lastIndexOf('$$') === text.length - 2);
      var inline = !display && text.length > 2 && text.charAt(0) === '$' && text.charAt(text.length - 1) === '$';
      if (!display && !inline) continue;
      var tex = isMathFence ? text : (display ? text.slice(2, -2) : text.slice(1, -1));
      replaceWithMath(node.parentNode && node.parentNode.tagName === 'PRE' ? node.parentNode : node, tex, display);
    }
  }

  function renderMathInParagraphs() {
    var blocks = host.querySelectorAll('p');
    for (var i = 0; i < blocks.length; i++) {
      var p = blocks[i];
      var raw = (p.textContent || '').trim();
      // A whole paragraph that is one formula: $$...$$ or \[...\].
      var whole = /^\$\$([\s\S]+)\$\$$/.exec(raw) || /^\\\[([\s\S]+)\\\]$/.exec(raw);
      if (whole) {
        replaceWithMath(p, whole[1], true);
        continue;
      }
      // Inline $...$ inside a sentence.
      if (raw.indexOf('$') < 0) continue;
      var html = p.innerHTML.replace(/\$([^$\n]+?)\$/g, function (match, tex) {
        try {
          return katex.renderToString(tex, { displayMode: false, throwOnError: false });
        } catch (e) {
          return match;
        }
      });
      if (html !== p.innerHTML) p.innerHTML = html;
    }
  }

  function replaceWithMath(node, tex, display) {
    if (!node || !node.parentNode) return;
    try {
      var holder = document.createElement(display ? 'div' : 'span');
      holder.innerHTML = katex.renderToString(tex, { displayMode: display, throwOnError: false });
      node.parentNode.replaceChild(holder, node);
    } catch (mathError) {
      // Leave the literal text in place: showing "$x^2$" is better than showing nothing.
    }
  }

  // --- 4. Mermaid fences ----------------------------------------------------
  try {
    var fences = host.querySelectorAll('pre > code.language-mermaid, pre > code.lang-mermaid');
    if (fences.length > 0 && window.mermaid) {
      mermaid.initialize({
        startOnLoad: false,
        theme: document.documentElement.getAttribute('data-theme') === 'dark' ? 'dark' : 'default',
        securityLevel: 'strict',
        fontFamily: 'inherit'
      });
      var holders = [];
      for (var k = 0; k < fences.length; k++) {
        var code = fences[k];
        var diagram = document.createElement('div');
        diagram.className = 'mermaid';
        diagram.textContent = code.textContent;
        code.parentNode.parentNode.replaceChild(diagram, code.parentNode);
        holders.push(diagram);
      }
      mermaid.run({ nodes: holders }).catch(function (diagramError) {
        var box = document.createElement('div');
        box.className = 'cc-err';
        box.textContent = 'Mermaid 图表渲染失败：' + (diagramError && diagramError.message ? diagramError.message : diagramError);
        host.appendChild(box);
      });
    }
  } catch (ignored) {}
})();
