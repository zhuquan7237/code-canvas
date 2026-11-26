package com.nous.codecanvas;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.test.InstrumentationTestCase;
import android.view.MotionEvent;
import android.webkit.WebView;

import com.nous.codecanvas.data.DocumentRepository;
import com.nous.codecanvas.model.CanvasDocument;
import com.nous.codecanvas.ui.EditorActivity;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class JsonPathDeviceTest extends InstrumentationTestCase {
    private EditorActivity editor;
    private CanvasDocument doc;
    private WebView web;

    private Context context() { return getInstrumentation().getTargetContext(); }

    @Override protected void tearDown() throws Exception {
        if (editor != null) getInstrumentation().runOnMainSync(editor::finish);
        if (doc != null) new DocumentRepository(context()).deleteDocument(doc.getId());
        super.tearDown();
    }

    private void open(String source) throws Exception {
        doc = new CanvasDocument("json-path-test-" + System.nanoTime(), "assistant.json", source,
                System.currentTimeMillis());
        new DocumentRepository(context()).saveDocument(doc);
        editor = (EditorActivity) getInstrumentation().startActivitySync(new Intent(context(), EditorActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(EditorActivity.EXTRA_DOC_ID, doc.getId())
                .putExtra(EditorActivity.EXTRA_START_PREVIEW, true));
        getInstrumentation().waitForIdleSync();
        web = editor.findViewById(R.id.web_preview);
        waitFor("!!document.querySelector('.cc-tree')");
    }

    private String eval(String js) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> answer = new AtomicReference<>();
        getInstrumentation().runOnMainSync(() -> web.evaluateJavascript(js, value -> {
            answer.set(value);
            latch.countDown();
        }));
        return latch.await(4, TimeUnit.SECONDS) ? answer.get() : null;
    }

    private void waitFor(String js) throws Exception {
        long deadline = SystemClock.uptimeMillis() + 15_000;
        do {
            if ("true".equals(eval(js))) return;
            Thread.sleep(140);
        } while (SystemClock.uptimeMillis() < deadline);
        fail("Missing WebView state: " + js);
    }

    private String entry(String path) {
        return "Array.from(document.querySelectorAll('.cc-path')).find(b=>b.dataset.path==="
                + JSONObject.quote(path) + ")";
    }

    private void tapPath(String path) throws Exception {
        String lookup = entry(path);
        assertEquals("true", eval("(()=>{const b=" + lookup
                + ";if(!b)return false;for(let p=b.parentElement;p;p=p.parentElement)"
                + "if(p.tagName==='DETAILS')p.open=true;b.scrollIntoView({block:'center'});return true;})()"));
        Thread.sleep(200);
        JSONArray rect = new JSONArray(eval("(()=>{const r=(" + lookup + ").getBoundingClientRect();"
                + "return [r.left+r.width/2,r.top+r.height/2,innerWidth,r.left,r.right,r.height];})()"));
        assertTrue(rect.getDouble(3) >= 0);
        assertTrue(rect.getDouble(4) <= rect.getDouble(2) + 1);
        assertTrue(rect.getDouble(5) >= 44);
        int[] width = new int[1];
        getInstrumentation().runOnMainSync(() -> width[0] = web.getWidth());
        float scale = (float) (width[0] / rect.getDouble(2));
        float x = (float) rect.getDouble(0) * scale;
        float y = (float) rect.getDouble(1) * scale;
        // Send a real tap through Android's WebView input path without targeting an
        // unrelated system overlay window (e.g. the OS clipboard confirmation chip).
        getInstrumentation().runOnMainSync(() -> {
            assertTrue("WebView must have a visible gesture target", y >= 0 && y < web.getHeight());
            long t = SystemClock.uptimeMillis();
            for (int action : new int[]{MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP}) {
                MotionEvent event = MotionEvent.obtain(t, SystemClock.uptimeMillis(), action, x, y, 0);
                try { web.dispatchTouchEvent(event); }
                finally { event.recycle(); }
            }
        });
    }

    private String clipboard() {
        AtomicReference<String> current = new AtomicReference<>();
        getInstrumentation().runOnMainSync(() -> {
            ClipboardManager clipboard = (ClipboardManager) context().getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData data = clipboard.getPrimaryClip();
            if (data != null && data.getItemCount() > 0 && data.getItemAt(0).getText() != null) {
                current.set(data.getItemAt(0).getText().toString());
            }
        });
        return current.get();
    }

    public void testActualGestureCopiesBothExactPathsAndKeepsAllOriginalTokens() throws Exception {
        String source = "{\"choices\":[{\"arguments\":{\"a.b\":9007199254740993,\"a\":{\"b\":1e-9},"
                + "\"\\u0078\":null}},{\"arguments\":[]}],\"status\":\"完成\"}\r\n";
        open(source);
        waitFor("!!document.querySelector('.cc-path')");
        String first = "$['choices'][0]['arguments']['a.b']";
        String second = "$['choices'][0]['arguments']['a']['b']";
        assertEquals("true", eval("document.body.textContent.includes('9007199254740993')"
                + "&& document.body.textContent.includes('1e-9') && document.body.scrollWidth<=innerWidth"));
        tapPath(first);
        waitFor("document.getElementById('cc-copied').textContent==='已复制'");
        assertEquals(first, clipboard());
        assertEquals("true", eval("(" + entry(first) + ").textContent===" + JSONObject.quote(first)));
        tapPath(second);
        waitFor("document.getElementById('cc-copied').textContent==='已复制'");
        assertEquals(second, clipboard());
        assertEquals("true", eval("(" + entry(second) + ").textContent===" + JSONObject.quote(second)));
        assertEquals(source, new DocumentRepository(context()).findById(doc.getId()).getContent());
        assertEquals("true", eval("(()=>{const f=" + entry(first)
                + ";document.execCommand=()=>false;f.click();"
                + "return document.getElementById('cc-copied').textContent==='未能复制'"
                + "&&f.textContent===f.dataset.path;})()"));
        assertEquals(second, clipboard());
    }

    public void testCrOnlyErrorJumpsToExactCharacter() throws Exception {
        open("{\r  \"response\":\r    ]}");
        assertEquals("true", eval("document.querySelector('.cc-err').textContent.includes('第 3 行第 5 列')"
                + "&&document.querySelector('.cc-badline').contains(document.querySelector('[aria-label=错误位置]'))"));
        eval("document.querySelector('.cc-err a').click();true");
        android.widget.EditText code = editor.findViewById(R.id.edit_code);
        AtomicReference<Integer> selection = new AtomicReference<>(-1);
        long deadline = SystemClock.uptimeMillis() + 8_000;
        do {
            getInstrumentation().runOnMainSync(() -> selection.set(code.getSelectionStart()));
            if (selection.get() == doc.getContent().indexOf(']')) break;
            Thread.sleep(100);
        } while (SystemClock.uptimeMillis() < deadline);
        assertEquals(doc.getContent().indexOf(']'), selection.get().intValue());
    }

    public void testEscapedDuplicateLeavesBothValuesButNotMisleadingPaths() throws Exception {
        open("{\"x\":{\"inner\":1},\"\\u0078\":2,\"ok\":{\"x\":3}}");
        waitFor("document.body.textContent.includes('重复键及其子项没有唯一路径')"
                + "&& Array.from(document.querySelectorAll('.cc-row code')).some(x=>x.textContent.includes('\"inner\": 1'))"
                + "&& Array.from(document.querySelectorAll('.cc-row code')).some(x=>x.textContent.includes('\\\\u0078'))"
                + "&& !Array.from(document.querySelectorAll('.cc-path')).some(x=>x.dataset.path.startsWith(\"$['x']\"))");
        String expected = "$['ok']['x']";
        tapPath(expected);
        waitFor("document.getElementById('cc-copied').textContent==='已复制'");
        assertEquals(expected, clipboard());
    }
}
