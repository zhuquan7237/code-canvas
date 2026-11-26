package com.nous.codecanvas.util;

import com.nous.codecanvas.data.DocumentKind;
import com.nous.codecanvas.data.FileKindDetector;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import java.io.File;
import java.io.RandomAccessFile;
import static org.junit.Assert.*;

public class NotebookPreviewTest {
    private static final String PNG =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAAEElEQVR4AQEFAPr/AMhQQf8FlgJZf4NzlwAAAABJRU5ErkJggg==";

    private String page(JSONObject... cells) throws Exception {
        return NotebookPreview.render(new JSONObject().put("nbformat",4).put("nbformat_minor",0)
                .put("cells",new JSONArray(java.util.Arrays.asList(cells))).toString(),false);
    }

    @Test public void opensEarlyV4WithSplitSourceAndPreservesSeparators() throws Exception {
        String html = page(new JSONObject().put("cell_type","markdown")
                .put("source",new JSONArray().put("# 数据").put("分析\n").put("结论")),
                new JSONObject().put("cell_type","code").put("source","plot(data)"));
        assertTrue(html.contains("<h2>数据分析</h2>"));
        assertTrue(html.contains("<pre>plot(data)</pre>"));
        assertTrue(html.contains("没有已保存的输出"));
        assertEquals(DocumentKind.NOTEBOOK, FileKindDetector.detect("Data.IPYNB","application/octet-stream",null));
        assertEquals(DocumentKind.NOTEBOOK, FileKindDetector.detect("unknown","application/x-ipynb+json",null));
        assertFalse(DocumentKind.NOTEBOOK.hasSourceTab());
        assertTrue(new com.nous.codecanvas.model.CanvasDocument("id","notes.ipynb","draft",0L)
                .kind().hasSourceTab());
    }

    @Test public void rendersOnlyOneRepresentationAndNeverRendersImportedMarkup() throws Exception {
        JSONObject data = new JSONObject().put("text/plain","alternative marker").put("image/png",PNG)
                .put("text/html","<script>bad()</script>");
        JSONObject rich = new JSONObject().put("output_type","display_data").put("data",data);
        JSONObject htmlOnly = new JSONObject().put("output_type","execute_result")
                .put("data", new JSONObject().put("text/html","<video autoplay>"));
        JSONObject stream = new JSONObject().put("output_type","stream").put("text",
                new JSONArray().put("line 1\n").put("<script>fromStream()</script>"));
        String html = page(new JSONObject().put("cell_type","code").put("source","<iframe src='https://example.org'>")
                .put("outputs",new JSONArray().put(rich).put(stream).put(htmlOnly)));
        assertTrue(html.contains("src=\"data:image/png;base64,"+PNG));
        assertTrue(html.contains("line 1\n&lt;script&gt;fromStream()&lt;/script&gt;"));
        assertTrue(html.contains("&lt;iframe"));
        assertTrue(html.contains("此输出无法安全预览"));
        assertTrue(html.contains("default-src 'none'"));
        assertFalse(html.contains("alternative marker"));
        assertFalse(html.contains("bad()"));
        assertFalse(html.contains("<script"));
        assertFalse(html.contains("<video"));
    }

    @Test public void limitOversizedImagesAndDisplayOnlySafeFallback() throws Exception {
        byte[] bytes = java.util.Base64.getDecoder().decode(PNG);
        java.nio.ByteBuffer.wrap(bytes).putInt(16,50000);
        String html=page(new JSONObject().put("cell_type","code").put("source","plot()")
                .put("outputs",new JSONArray().put(new JSONObject().put("output_type","execute_result")
                        .put("data",new JSONObject().put("image/png",java.util.Base64.getEncoder().encodeToString(bytes))
                                .put("text/plain","Safe fallback")))));
        assertFalse(html.contains("<img"));
        assertTrue(html.contains("Safe fallback"));
    }

    @Test public void longOutputCanCollapseAndTruncationIsExplicit() throws Exception {
        String html = page(new JSONObject().put("cell_type","code").put("source","")
                .put("outputs",new JSONArray().put(new JSONObject().put("output_type","stream")
                        .put("text","x".repeat(35000)))));
        assertTrue(html.contains("<details>"));
        assertTrue(html.contains("其余文本未显示"));
        assertFalse(html.contains("x".repeat(32001)));
    }

    @Test public void malformedTooDeepUnsupportedAndOversizedDoNotOpen() throws Exception {
        assertTrue(NotebookPreview.render("{bad",false).contains("无法预览"));
        assertTrue(NotebookPreview.render("{\"nbformat\":4;\"cells\":[]}",false).contains("格式无效"));
        assertTrue(NotebookPreview.render("{\"nbformat\":3,\"cells\":[]}",false).contains("只支持"));
        assertTrue(NotebookPreview.render("{\"ignored\":"+"[".repeat(100)+"]".repeat(100)+"}",false)
                .contains("结构过深"));
        File file = File.createTempFile("notebook",".ipynb");
        try {
            try (RandomAccessFile output = new RandomAccessFile(file,"rw")) {
                output.setLength(NotebookPreview.MAX_FILE+1);
            }
            assertTrue(NotebookPreview.fromFile(file,true).contains("8 MB"));
        } finally {
            assertTrue(file.delete());
        }
    }
}
