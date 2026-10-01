package com.nous.codecanvas;
import android.content.Intent;import android.test.InstrumentationTestCase;import android.view.View;import android.view.ViewGroup;import android.widget.*;import com.nous.codecanvas.data.DocumentRepository;import com.nous.codecanvas.model.CanvasDocument;import com.nous.codecanvas.ui.EditorActivity;
/** Real editor: preview lifecycle across tab switches, plus the practical toolbar. */
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
 public void testFindReplaceAndSymbolInsertAreRealAndUndoable() throws Throwable {
  runTestOnUiThread(()->{((EditText)editor.findViewById(R.id.edit_code)).setText("alpha beta alpha");editor.findViewById(R.id.tab_code).performClick();});
  getInstrumentation().waitForIdleSync();
  final Button find=findHelper("查找/替换");
  assertNotNull("工具栏必须包含查找替换入口",find);
  runTestOnUiThread(find::performClick);getInstrumentation().waitForIdleSync();
  final android.app.AlertDialog dialog=editor.getActiveDialogForTest();
  assertNotNull("必须弹出查找替换对话框",dialog);
  runTestOnUiThread(()->{
   ViewGroup custom=(ViewGroup)dialog.findViewById(android.R.id.custom);
   ViewGroup box=(ViewGroup)custom.getChildAt(0);
   EditText query=(EditText)findByDescription(box,"查找内容"),replacement=(EditText)findByDescription(box,"替换为");
   assertNotNull("查找框必须真实存在",query);assertNotNull("替换框必须真实存在",replacement);
   query.setText("alpha");replacement.setText("OMEGA");
   dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick();
  });
  getInstrumentation().waitForIdleSync();
  runTestOnUiThread(()->assertEquals("全部替换必须真实生效","OMEGA beta OMEGA",((EditText)editor.findViewById(R.id.edit_code)).getText().toString()));
  runTestOnUiThread(()->{editor.findViewById(R.id.tab_code).performClick();editor.findViewById(R.id.btn_undo).performClick();});
  getInstrumentation().waitForIdleSync();
  runTestOnUiThread(()->assertEquals("替换必须可撤销","alpha beta alpha",((EditText)editor.findViewById(R.id.edit_code)).getText().toString()));
  final Button braces=findHelper("{}");
  assertNotNull("工具栏必须包含常用符号",braces);
  runTestOnUiThread(()->{EditText code=editor.findViewById(R.id.edit_code);code.setSelection(0);braces.performClick();});
  getInstrumentation().waitForIdleSync();
  runTestOnUiThread(()->{EditText code=editor.findViewById(R.id.edit_code);assertEquals("配对符号必须真实插入到代码中","{}alpha beta alpha",code.getText().toString());assertEquals("配对符号必须把光标留在中间",1,code.getSelectionStart());});
 }
 private static View findByDescription(ViewGroup g,String desc){for(int i=0;i<g.getChildCount();i++){View v=g.getChildAt(i);if(desc.equals(String.valueOf(v.getContentDescription())))return v;if(v instanceof ViewGroup){View nested=findByDescription((ViewGroup)v,desc);if(nested!=null)return nested;}}return null;}
 private Button findHelper(String label){LinearLayout row=editor.findViewById(R.id.layout_editor_helpers);for(int i=0;i<row.getChildCount();i++){View v=row.getChildAt(i);if(v instanceof Button&&label.equals(((Button)v).getText().toString()))return (Button)v;}return null;}
}
