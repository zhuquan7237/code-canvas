package com.nous.codecanvas;
import android.content.Intent;
import android.test.InstrumentationTestCase;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import com.nous.codecanvas.data.DocumentRepository;
import com.nous.codecanvas.model.CanvasDocument;
import com.nous.codecanvas.ui.EditorActivity;

/** Real editor: preview lifecycle across tab switches, the search bar, and the symbol strip. */
public class PreviewTransitionAndToolbarTest extends InstrumentationTestCase {
 private EditorActivity editor;private String docId;
 @Override protected void setUp() throws Exception {super.setUp();
  android.content.Context c=getInstrumentation().getTargetContext();
  docId="transition-"+System.nanoTime();
  new DocumentRepository(c).saveDocument(new CanvasDocument(docId,"过渡.html","<html><body style='background:#ffffff'><h1>过渡测试</h1></body></html>",System.currentTimeMillis()));
  Intent i=new Intent(c,EditorActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(EditorActivity.EXTRA_DOC_ID,docId).putExtra(EditorActivity.EXTRA_START_PREVIEW,true);
  editor=(EditorActivity)getInstrumentation().startActivitySync(i);getInstrumentation().waitForIdleSync();
 }
 @Override protected void tearDown() throws Exception {
  new DocumentRepository(getInstrumentation().getTargetContext()).deleteDocument(docId);
  getInstrumentation().runOnMainSync(()->{if(!editor.isFinishing())editor.finish();});getInstrumentation().waitForIdleSync();super.tearDown();
 }
 private void toggleTabs(int times) throws Throwable {for(int i=0;i<times;i++){final boolean preview=i%2==0;runTestOnUiThread(()->{if(preview)editor.findViewById(R.id.tab_preview).performClick();else editor.findViewById(R.id.tab_code).performClick();});getInstrumentation().waitForIdleSync();}}
 public void testUnchangedPreviewIsNotReloadedButEditsDoReload() throws Throwable {
  final int[] first={0};
  runTestOnUiThread(()->{first[0]=editor.getPreviewLoadCountForTest();assertTrue("首次进入预览必须渲染一次",first[0]>=1);});
  toggleTabs(20);
  runTestOnUiThread(()->assertEquals("代码未变化时反复切换不得重载预览",first[0],editor.getPreviewLoadCountForTest()));
  runTestOnUiThread(()->((EditText)editor.findViewById(R.id.edit_code)).setText("<html><body><h1>改了内容</h1></body></html>"));
  getInstrumentation().waitForIdleSync();
  runTestOnUiThread(()->editor.findViewById(R.id.tab_preview).performClick());getInstrumentation().waitForIdleSync();
  runTestOnUiThread(()->assertTrue("内容变化后必须重新渲染",editor.getPreviewLoadCountForTest()>first[0]));
  runTestOnUiThread(()->assertEquals("预览页必须实际显示",1,((android.widget.ViewFlipper)editor.findViewById(R.id.view_flipper)).getDisplayedChild()));
  final View loading=editor.findViewById(R.id.preview_loading);
  long deadline=System.currentTimeMillis()+12000;
  while(System.currentTimeMillis()<deadline){final boolean[] visible={false};runTestOnUiThread(()->visible[0]=loading.getVisibility()==View.VISIBLE);if(!visible[0])break;Thread.sleep(200);}
  runTestOnUiThread(()->assertEquals("渲染完成后加载遮罩必须消失，不能留下空白页",View.GONE,loading.getVisibility()));
  runTestOnUiThread(()->assertNotNull("预览必须真的装载了内容",((android.webkit.WebView)editor.findViewById(R.id.web_preview)).getUrl()));
 }

 /** The search bar counts, jumps, wraps, and respects the case switch. */
 public void testSearchCountsJumpsAndWraps() throws Throwable {
  runTestOnUiThread(()->{((EditText)editor.findViewById(R.id.edit_code)).setText("alpha beta alpha gamma alpha");editor.findViewById(R.id.tab_code).performClick();});
  getInstrumentation().waitForIdleSync();
  runTestOnUiThread(()->editor.findViewById(R.id.btn_search).performClick());
  getInstrumentation().waitForIdleSync();
  final View bar=editor.findViewById(R.id.search_bar);
  runTestOnUiThread(()->assertEquals("搜索栏必须出现",View.VISIBLE,bar.getVisibility()));
  final EditText query=editor.findViewById(R.id.edit_search_query);
  final TextView counter=editor.findViewById(R.id.txt_search_counter);
  runTestOnUiThread(()->query.setText("alpha"));
  getInstrumentation().waitForIdleSync();
  runTestOnUiThread(()->{
   assertEquals("必须统计全部匹配",3,editor.getSearchResultForTest().count());
   assertEquals("计数必须是一基的当前/总数","1 / 3",counter.getText().toString());
  });
  runTestOnUiThread(()->editor.findViewById(R.id.btn_search_next).performClick());
  getInstrumentation().waitForIdleSync();
  runTestOnUiThread(()->assertEquals("下一处必须推进","2 / 3",counter.getText().toString()));
  runTestOnUiThread(()->editor.findViewById(R.id.btn_search_prev).performClick());
  runTestOnUiThread(()->editor.findViewById(R.id.btn_search_prev).performClick());
  getInstrumentation().waitForIdleSync();
  runTestOnUiThread(()->assertEquals("上一处必须回绕到最后一处","3 / 3",counter.getText().toString()));
  runTestOnUiThread(()->editor.findViewById(R.id.btn_search_next).performClick());
  getInstrumentation().waitForIdleSync();
  runTestOnUiThread(()->assertEquals("最后一处再下一处必须回到第一处","1 / 3",counter.getText().toString()));

  // Case sensitivity: "Alpha" is a separate match only when the switch is on.
  runTestOnUiThread(()->{EditText code=editor.findViewById(R.id.edit_code);code.setText("Alpha alpha");});
  getInstrumentation().waitForIdleSync();
  runTestOnUiThread(()->query.setText("alpha"));
  getInstrumentation().waitForIdleSync();
  runTestOnUiThread(()->assertEquals("默认不区分大小写",2,editor.getSearchResultForTest().count()));
  runTestOnUiThread(()->editor.findViewById(R.id.btn_search_case).performClick());
  getInstrumentation().waitForIdleSync();
  runTestOnUiThread(()->assertEquals("开启后只匹配大小写一致的一处",1,editor.getSearchResultForTest().count()));

  // No results must be explicit, not a silent zero.
  runTestOnUiThread(()->query.setText("zzz"));
  getInstrumentation().waitForIdleSync();
  runTestOnUiThread(()->assertEquals("0 / 0",counter.getText().toString()));

  // Closing the bar hides it and clears the matches.
  runTestOnUiThread(()->editor.findViewById(R.id.btn_search_close).performClick());
  getInstrumentation().waitForIdleSync();
  runTestOnUiThread(()->{
   assertEquals("关闭后搜索栏必须消失",View.GONE,bar.getVisibility());
   assertEquals("关闭后不得残留匹配",0,editor.getSearchResultForTest().count());
  });
 }

 /** Search must follow the text: editing while searching updates the count. */
 public void testSearchFollowsEdits() throws Throwable {
  runTestOnUiThread(()->{((EditText)editor.findViewById(R.id.edit_code)).setText("one");editor.findViewById(R.id.tab_code).performClick();editor.findViewById(R.id.btn_search).performClick();});
  getInstrumentation().waitForIdleSync();
  final EditText query=editor.findViewById(R.id.edit_search_query);
  runTestOnUiThread(()->query.setText("one"));
  getInstrumentation().waitForIdleSync();
  runTestOnUiThread(()->assertEquals(1,editor.getSearchResultForTest().count()));
  runTestOnUiThread(()->((EditText)editor.findViewById(R.id.edit_code)).setText("one two one"));
  getInstrumentation().waitForIdleSync();
  runTestOnUiThread(()->assertEquals("编辑后计数必须更新",2,editor.getSearchResultForTest().count()));
 }

 /** The symbol strip still inserts paired characters and leaves the caret between them. */
 public void testSymbolInsertIsRealAndUndoable() throws Throwable {
  runTestOnUiThread(()->{((EditText)editor.findViewById(R.id.edit_code)).setText("alpha");editor.findViewById(R.id.tab_code).performClick();});
  getInstrumentation().waitForIdleSync();
  final Button braces=findHelper("{}");
  assertNotNull("工具栏必须包含常用符号",braces);
  runTestOnUiThread(()->{EditText code=editor.findViewById(R.id.edit_code);code.setSelection(0);braces.performClick();});
  getInstrumentation().waitForIdleSync();
  runTestOnUiThread(()->{EditText code=editor.findViewById(R.id.edit_code);assertEquals("配对符号必须真实插入到代码中","{}alpha",code.getText().toString());assertEquals("配对符号必须把光标留在中间",1,code.getSelectionStart());});
  runTestOnUiThread(()->editor.findViewById(R.id.btn_undo).performClick());
  getInstrumentation().waitForIdleSync();
  runTestOnUiThread(()->assertEquals("插入必须可撤销","alpha",((EditText)editor.findViewById(R.id.edit_code)).getText().toString()));
 }

 private Button findHelper(String label){LinearLayout row=editor.findViewById(R.id.layout_editor_helpers);for(int i=0;i<row.getChildCount();i++){View v=row.getChildAt(i);if(v instanceof Button&&label.equals(((Button)v).getText().toString()))return (Button)v;}return null;}
}
