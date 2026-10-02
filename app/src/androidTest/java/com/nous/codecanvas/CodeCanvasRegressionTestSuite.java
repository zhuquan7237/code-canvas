package com.nous.codecanvas;
import android.app.AlertDialog;
import android.app.Instrumentation;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.test.InstrumentationTestCase;
import android.view.View;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ViewFlipper;
import com.nous.codecanvas.ui.MainActivity;
import com.nous.codecanvas.ui.EditorActivity;
import com.nous.codecanvas.data.DocumentRepository;
import com.nous.codecanvas.model.CanvasDocument;
import java.util.ArrayList;
import java.util.List;
/** Real controls and dialogs; never replicate production logic in a test. */
public class CodeCanvasRegressionTestSuite extends InstrumentationTestCase {
 private MainActivity main;
 private final List<EditorActivity> editors=new ArrayList<>();
 private Instrumentation.ActivityMonitor monitor;
 @Override protected void setUp() throws Exception {
  super.setUp(); TestAppearance.resetToSystem(getInstrumentation().getTargetContext()); main=(MainActivity)getInstrumentation().startActivitySync(new Intent(getInstrumentation().getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
  getInstrumentation().waitForIdleSync();
 }
 @Override protected void tearDown() throws Exception {
  if(monitor!=null)getInstrumentation().removeMonitor(monitor);
  getInstrumentation().runOnMainSync(() -> {for(EditorActivity e:editors)if(!e.isFinishing())e.finish(); main.finish();});
  getInstrumentation().waitForIdleSync(); super.tearDown();
 }
 private void ui(Runnable r) throws Throwable {runTestOnUiThread(r);getInstrumentation().waitForIdleSync();}
 private void clip(String s){((ClipboardManager)main.getSystemService(MainActivity.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("fixture",s));}
 private void watch(){if(monitor!=null)getInstrumentation().removeMonitor(monitor);monitor=getInstrumentation().addMonitor(EditorActivity.class.getName(),null,false);}
 private EditorActivity await(){EditorActivity e=(EditorActivity)monitor.waitForActivityWithTimeout(30000);assertNotNull("Real click must launch editor",e);editors.add(e);getInstrumentation().waitForIdleSync();return e;}
 public void testQuickpasteFencedHtmlViaPrimaryButton() throws Throwable {
  watch(); ui(() -> {clip("AI 回复\n```html\n<h1>真实快捷预览</h1>\n```\n结束");main.findViewById(R.id.btn_quick_paste_preview).performClick();});
  EditorActivity e=await(); ui(() -> {assertEquals("<h1>真实快捷预览</h1>",((EditText)e.findViewById(R.id.edit_code)).getText().toString());assertEquals(1,((ViewFlipper)e.findViewById(R.id.view_flipper)).getDisplayedChild());});
 }
 public void testMultiBlockSvgRealSelectionAppendReplaceUndoAndSelection() throws Throwable {
  String svg="<svg viewBox='0 0 40 40'><circle cx='20' cy='20' r='10'/></svg>";
  watch(); ui(() -> {clip("```html\n<h1>不要拼接</h1>\n```\n```svg\n"+svg+"\n```");main.findViewById(R.id.btn_quick_paste_preview).performClick();AlertDialog d=main.getActiveDialogForTest();assertTrue(d.isShowing());ListView l=d.getListView();assertEquals(3,l.getAdapter().getCount());l.performItemClick(l.getAdapter().getView(1,null,l),1,1);});
  EditorActivity e=await(); EditText code=e.findViewById(R.id.edit_code);
  ui(() -> {
   assertEquals(svg,code.getText().toString());assertTrue(e.getCurrentDocumentForTest().getTitle().endsWith(".svg"));e.findViewById(R.id.tab_code).performClick();
   code.setText("AAAABBBB");code.setSelection(4);clip("APPENDED");e.findViewById(R.id.btn_paste).performClick();
   AlertDialog d=e.getActiveDialogForTest();assertTrue(d.isShowing());d.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
  });
  ui(() -> {
   assertEquals("AAAABBBB\n\nAPPENDED",code.getText().toString());assertEquals(code.length(),code.getSelectionStart());
   clip("NEW_CONTENT");code.setSelection(2);e.findViewById(R.id.btn_paste).performClick();e.getActiveDialogForTest().getButton(AlertDialog.BUTTON_NEUTRAL).performClick();
  });
  ui(() -> {
   assertEquals("NEW_CONTENT",code.getText().toString());assertTrue(e.findViewById(R.id.btn_undo).isEnabled());e.findViewById(R.id.btn_undo).performClick();assertEquals("AAAABBBB\n\nAPPENDED",code.getText().toString());
   code.setText("<h1>selection</h1>");code.setSelection(4,9);
  });
  Thread.sleep(600);ui(() -> {assertEquals(4,code.getSelectionStart());assertEquals(9,code.getSelectionEnd());});
 }
 public void testRealCardTapAndRealNewNamingDefaultTabs() throws Throwable {
  CanvasDocument d=new CanvasDocument("actual-card-"+System.nanoTime(),"实际卡片_"+System.nanoTime()+".html","<h1>card</h1>",System.currentTimeMillis());new DocumentRepository(main).saveDocument(d);
  ui(() -> {main.loadDocumentsForTest();((EditText)main.findViewById(R.id.edit_search)).setText(d.getTitle());});
  watch();ui(() -> {ListView l=main.findViewById(R.id.list_documents);assertEquals(1,l.getAdapter().getCount());View row=l.getChildAt(0);assertNotNull(row);assertNull("主页行不得展示源码片段",row.findViewById(R.id.txt_doc_snippet));assertNotNull(row.findViewById(R.id.txt_doc_title));row.performClick();});
  EditorActivity e=await();ui(() -> {assertEquals(1,((ViewFlipper)e.findViewById(R.id.view_flipper)).getDisplayedChild());e.findViewById(R.id.tab_code).performClick();assertEquals(0,((ViewFlipper)e.findViewById(R.id.view_flipper)).getDisplayedChild());e.finish();});
  watch();ui(() -> {main.findViewById(R.id.btn_new).performClick();AlertDialog dialog=main.getActiveDialogForTest();((EditText)dialog.findViewById(R.id.dialog_edit_input)).setText("任意后缀.shader.custom");dialog.findViewById(R.id.dialog_btn_positive).performClick();});
  EditorActivity blank=await();ui(() -> {assertEquals(0,((ViewFlipper)blank.findViewById(R.id.view_flipper)).getDisplayedChild());assertEquals("任意后缀.shader.custom",blank.getCurrentDocumentForTest().getTitle());});
 }
 public void testOriginalChoicePreservesAllText() throws Throwable {
  String raw="```html\n<h1>one</h1>\n```\n```css\nh1{color:red}\n```";
  watch();ui(() -> {clip(raw);main.findViewById(R.id.btn_quick_paste_preview).performClick();ListView l=main.getActiveDialogForTest().getListView();l.performItemClick(l.getAdapter().getView(2,null,l),2,2);});
  EditorActivity e=await();ui(() -> assertEquals(raw,((EditText)e.findViewById(R.id.edit_code)).getText().toString()));
 }
}

