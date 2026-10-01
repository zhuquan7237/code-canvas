package com.nous.codecanvas;
import android.content.Intent;import android.test.InstrumentationTestCase;import android.view.View;import android.widget.TextView;
import com.nous.codecanvas.data.DocumentRepository;import com.nous.codecanvas.model.CanvasDocument;import com.nous.codecanvas.ui.EditorActivity;
/** An XML document must show the structure view immediately, without waiting on WebView rendering. */
public class XmlPreviewNoWaitTest extends InstrumentationTestCase {
 private EditorActivity editor;private String docId;
 @Override protected void setUp() throws Exception {super.setUp();
  android.content.Context c=getInstrumentation().getTargetContext();
  docId="xmlnowait-"+System.nanoTime();
  new DocumentRepository(c).saveDocument(new CanvasDocument(docId,"结构文件.xml","<config><item>1</item></config>",System.currentTimeMillis()));
  editor=(EditorActivity)getInstrumentation().startActivitySync(new Intent(c,EditorActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(EditorActivity.EXTRA_DOC_ID,docId).putExtra(EditorActivity.EXTRA_START_PREVIEW,true));
  getInstrumentation().waitForIdleSync();
 }
 @Override protected void tearDown() throws Exception {
  new DocumentRepository(getInstrumentation().getTargetContext()).deleteDocument(docId);
  getInstrumentation().runOnMainSync(()->{if(!editor.isFinishing())editor.finish();});getInstrumentation().waitForIdleSync();super.tearDown();
 }
 public void testXmlSuffixShowsStructureViewWithoutWebViewWait() throws Throwable {
  runTestOnUiThread(()->{
   assertEquals("XML 后缀必须走结构视图",View.VISIBLE,editor.findViewById(R.id.layout_xml_view).getVisibility());
   assertEquals("结构视图不得显示加载遮罩",View.GONE,editor.findViewById(R.id.preview_loading).getVisibility());
   assertEquals("结构视图不需要 WebView",View.GONE,editor.findViewById(R.id.web_preview).getVisibility());
   assertEquals("XML 文档不得占用 WebView 渲染",0,editor.getPreviewLoadCountForTest());
   TextView content=editor.findViewById(R.id.txt_xml_content);
   assertTrue("必须展示格式化结构内容",content.getText().toString().contains("config"));
   TextView status=editor.findViewById(R.id.txt_xml_status);
   assertTrue("必须给出真实校验结论",status.getText().toString().contains("XML"));
  });
 }
}
