package com.nous.codecanvas.ui;

import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.test.InstrumentationTestCase;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.ListView;
import android.widget.TextView;

import com.nous.codecanvas.R;
import com.nous.codecanvas.data.DocumentRepository;
import com.nous.codecanvas.model.CanvasDocument;
import com.nous.codecanvas.util.TextComparison;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class CompareTextDeviceTest extends InstrumentationTestCase {
    private Context context;
    private DocumentRepository repository;
    private CompareTextActivity activity;
    private final List<String> fixtures = new ArrayList<>();

    @Override protected void setUp() throws Exception {
        super.setUp();
        assertNotNull(getInstrumentation().getUiAutomation());
        context = getInstrumentation().getTargetContext();
        repository = new DocumentRepository(context);
    }

    @Override protected void tearDown() throws Exception {
        ui(() -> {
            if (activity != null && !activity.isDestroyed()) activity.finishAndRemoveTask();
        });
        for (String id : fixtures) repository.deleteDocument(id);
        getInstrumentation().waitForIdleSync();
        super.tearDown();
    }

    public void testSameFileNameMetadataDirectionWhitespaceAndOriginals() throws Exception {
        String baseText = "header\r\nsame  \r\nmiddle\r\n";
        String otherText = "header\nsame\nmiddle\nextra";
        CanvasDocument a = save("qa080-report.md",baseText, false);
        CanvasDocument b = save("qa080-report.md",otherText, false);
        CanvasDocument other = save("qa080-report.md","do not choose this one", false);
        CanvasDocument binary = save("qa080-only-image.png","", true);
        CanvasDocument base = repository.findById(a.getId());
        CanvasDocument target = repository.findById(b.getId());
        launch(a);
        assertNotNull(awaitPicker(other, true));
        assertNull(awaitPicker(binary, false));
        select(b);
        awaitCount(2,1);
        ui(() -> {
            assertTrue(text(R.id.compare_left).contains(suffix(a)));
            assertTrue(text(R.id.compare_right).contains(suffix(b)));
            assertEquals(activity.getString(R.string.compare_eof_removed),text(R.id.compare_eof));
            ListView lines = activity.findViewById(R.id.compare_list);
            assertEquals(3, lines.getAdapter().getCount());
            TextComparison.Row removed=(TextComparison.Row)lines.getAdapter().getItem(0);
            assertEquals("same  ", removed.text);
            assertFalse(removed.added);
            activity.findViewById(R.id.btn_compare_swap).performClick();
        });
        awaitCount(1,2);
        ui(() -> {
            assertTrue(text(R.id.compare_left).contains(suffix(b)));
            assertEquals(activity.getString(R.string.compare_eof_added),text(R.id.compare_eof));
        });
        assertSnapshotUnchanged(base); assertSnapshotUnchanged(target);
    }

    public void testEofOnlyHasAnExplicitResult() throws Exception {
        CanvasDocument a=save("qa080-base.txt","header\r\nitem\r\n",false);
        CanvasDocument b=save("qa080-eof.txt","header\nitem",false);
        launch(a); select(b);
        awaitText(R.id.compare_status,R.string.compare_eof_only);
        ui(() -> {
            assertEquals(View.VISIBLE,activity.findViewById(R.id.compare_eof).getVisibility());
            assertEquals(activity.getString(R.string.compare_eof_removed),text(R.id.compare_eof));
        });
    }

    public void testLongSingleLineStopsAndAnotherFileCanBeChosen() throws Exception {
        CanvasDocument a=save("qa080-base.txt","line\n",false);
        CanvasDocument longLine=save("qa080-long.txt",
                new String(new char[TextComparison.MAX_LINE_CHARS+1]).replace('\0','a'),false);
        CanvasDocument other=save("qa080-target.txt","next\n",false);
        launch(a); select(longLine); awaitText(R.id.compare_status,R.string.compare_limit);
        ui(() -> activity.findViewById(R.id.btn_compare_choose).performClick());
        select(other); awaitCount(1,1);
    }

    private CanvasDocument save(String title,String source,boolean blob) throws Exception {
        CanvasDocument doc=new CanvasDocument(UUID.randomUUID().toString(),title,source,1234567890000L);
        doc.setBlob(blob);
        repository.saveDocument(doc); fixtures.add(doc.getId());
        return doc;
    }

    private void launch(CanvasDocument first) {
        activity=(CompareTextActivity)getInstrumentation().startActivitySync(new Intent(context,CompareTextActivity.class)
                .putExtra(CompareTextActivity.EXTRA_LEFT,first.getId())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK));
    }

    private void select(CanvasDocument doc) {
        AccessibilityNodeInfo node=awaitPicker(doc,true);
        assertNotNull("File row with distinct metadata not found: "+doc.getTitle(),node);
        for (AccessibilityNodeInfo p=node;p!=null;p=p.getParent()) {
            if (p.isClickable() && p.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return;
        }
        fail("File row was not clickable");
    }

    private AccessibilityNodeInfo awaitPicker(CanvasDocument doc,boolean wait) {
        for(int i=0;i<(wait?65:1);i++){
            AccessibilityNodeInfo root=getInstrumentation().getUiAutomation().getRootInActiveWindow();
            if(root!=null) for(AccessibilityNodeInfo node:root.findAccessibilityNodeInfosByText(suffix(doc))){
                if(node.getText()!=null && node.getText().toString().contains(doc.getTitle())
                        && node.getText().toString().endsWith(suffix(doc))) return node;
            }
            if(wait)SystemClock.sleep(120);
        }
        return null;
    }

    private static String suffix(CanvasDocument doc){
        return doc.getId().substring(doc.getId().length()-8);
    }

    private void awaitCount(int add,int remove){
        awaitValue(R.id.compare_status,activity.getString(R.string.compare_counts,add,remove));
    }
    private void awaitText(int view,int res){ awaitValue(view,activity.getString(res)); }
    private void awaitValue(int view,String expected){
        String[] value={null};
        for(int i=0;i<65;i++){
            ui(() -> value[0]=text(view));
            if(expected.equals(value[0])) return;
            SystemClock.sleep(120);
        }
        assertEquals(expected,value[0]);
    }

    private String text(int id){return ((TextView)activity.findViewById(id)).getText().toString();}
    private void ui(Runnable task){getInstrumentation().runOnMainSync(task);}

    private void assertSnapshotUnchanged(CanvasDocument earlier) throws Exception {
        CanvasDocument after=repository.findById(earlier.getId());
        assertEquals(earlier.getContent(),after.getContent());
        assertEquals(earlier.getRevision(),after.getRevision());
        assertEquals(earlier.getUpdatedAt(),after.getUpdatedAt());
    }
}
