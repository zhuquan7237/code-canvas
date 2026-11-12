/*
 * Mermaid diagram preview.
 *
 * The diagram source is read from #cc-source (text/plain), so quotes and arrows in the diagram
 * never have to be escaped into a JavaScript literal. A syntax error is shown in the page and the
 * source stays editable — a broken diagram must not cost the user their file.
 */
(function () {
  'use strict';

  var sourceNode = document.getElementById('cc-source');
  var source = sourceNode ? sourceNode.textContent : '';
  var target = document.getElementById('cc-mermaid');
  var errBox = document.getElementById('cc-mermaid-err');

  function fail(message) {
    if (!errBox) return;
    errBox.textContent = message;
    errBox.style.display = 'block';
  }

  if (!window.mermaid) {
    fail('Mermaid 渲染库没有加载成功，可以切到源码查看内容。');
    return;
  }
  if (!source.trim()) {
    fail('图表内容为空。');
    return;
  }

  try {
    mermaid.initialize({
      startOnLoad: false,
      theme: document.documentElement.getAttribute('data-theme') === 'dark' ? 'dark' : 'default',
      securityLevel: 'strict',
      fontFamily: 'inherit',
      flowchart: { useMaxWidth: true, htmlLabels: true },
      sequence: { useMaxWidth: true }
    });
    // mermaid.run reads the diagram text from the element, so put the source in it first.
    target.textContent = source;
    mermaid.run({ nodes: [target] }).catch(function (e) {
      fail('Mermaid 语法错误：' + (e && e.message ? e.message : e));
    });
  } catch (e) {
    fail('Mermaid 语法错误：' + (e && e.message ? e.message : e));
  }
})();
