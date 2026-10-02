package com.nous.codecanvas;
import android.content.Intent;import android.test.InstrumentationTestCase;import android.view.View;import android.widget.GridView;import android.widget.ListView;import com.nous.codecanvas.data.DocumentRepository;import com.nous.codecanvas.model.CanvasDocument;import com.nous.codecanvas.ui.MainActivity;
import java.util.ArrayList;import java.util.List;
/** Real home screen: density of the compact list and the two-column grid. */
public class HomeLayoutDensityTest extends InstrumentationTestCase {
 private MainActivity main;private final List<String> created=new ArrayList<>();
 private static int fullyVisible(android.view.ViewGroup list){int shown=0;for(int i=0;i<list.getChildCount();i++){View row=list.getChildAt(i);if(row.getTop()>=0&&row.getBottom()<=list.getHeight())shown++;}return shown;}
 @Override protected void setUp() throws Exception {super.setUp();
  TestAppearance.resetToSystem(getInstrumentation().getTargetContext());
  DocumentRepository repo=new DocumentRepository(getInstrumentation().getTargetContext());
  for(int i=0;i<12;i++){String id="density-"+System.nanoTime()+"-"+i;created.add(id);repo.saveDocument(new CanvasDocument(id,"测试文件_"+i+(i%3==0?".html":i%3==1?".svg":".xml"),"<h1>密度测试 "+i+"</h1>",System.currentTimeMillis()));}
  main=(MainActivity)getInstrumentation().startActivitySync(new Intent(getInstrumentation().getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
  getInstrumentation().waitForIdleSync();
 }
 @Override protected void tearDown() throws Exception {
  DocumentRepository repo=new DocumentRepository(getInstrumentation().getTargetContext());
  for(String id:created)repo.deleteDocument(id);
  getInstrumentation().runOnMainSync(()->{if(!main.isFinishing())main.finish();});getInstrumentation().waitForIdleSync();super.tearDown();
 }
 public void testCompactListShowsAtLeastSixCompleteFilesWithoutScrolling() throws Throwable {
  final int[] shown={0};final int[] total={0};
  runTestOnUiThread(()->{ListView l=main.findViewById(R.id.list_documents);assertEquals("紧凑列表必须是默认视图",View.VISIBLE,l.getVisibility());assertEquals("方格视图必须默认隐藏",View.GONE,main.findViewById(R.id.grid_documents).getVisibility());total[0]=l.getAdapter().getCount();shown[0]=fullyVisible(l);});
  getInstrumentation().waitForIdleSync();
  assertTrue("夹具数量必须足够",total[0]>=12);
  assertTrue("一屏必须完整展示至少 6 个文件，实际 "+shown[0],shown[0]>=6);
 }
 public void testGridModeShowsAtLeastSixFilesAndKeepsSelectionOnRestart() throws Throwable {
  runTestOnUiThread(()->main.findViewById(R.id.btn_layout_grid).performClick());getInstrumentation().waitForIdleSync();
  final int[] shown={0};
  runTestOnUiThread(()->{GridView g=main.findViewById(R.id.grid_documents);assertEquals("方格视图必须显示",View.VISIBLE,g.getVisibility());assertEquals("方格必须是两列",2,g.getNumColumns());assertTrue("方格必须展示全部文件",g.getAdapter().getCount()>=12);shown[0]=fullyVisible(g);});
  assertTrue("一屏必须完整展示至少 6 个方格文件，实际 "+shown[0],shown[0]>=6);
  runTestOnUiThread(()->{if(!main.isFinishing())main.finish();});getInstrumentation().waitForIdleSync();
  main=(MainActivity)getInstrumentation().startActivitySync(new Intent(getInstrumentation().getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));getInstrumentation().waitForIdleSync();
  runTestOnUiThread(()->assertEquals("方格选择必须跨重启保留",View.VISIBLE,main.findViewById(R.id.grid_documents).getVisibility()));
  runTestOnUiThread(()->main.findViewById(R.id.btn_layout_list).performClick());getInstrumentation().waitForIdleSync();
  runTestOnUiThread(()->assertEquals("必须能切回紧凑列表",View.VISIBLE,main.findViewById(R.id.list_documents).getVisibility()));
 }
}
