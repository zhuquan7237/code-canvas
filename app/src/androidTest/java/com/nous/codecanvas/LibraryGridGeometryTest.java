package com.nous.codecanvas;

import android.content.Context;
import android.content.res.Configuration;
import android.test.InstrumentationTestCase;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.TextView;

/** Measures the actual production card in the platform GridView, not a mock layout. */
public class LibraryGridGeometryTest extends InstrumentationTestCase {
    public void testMixedTitlesDoNotOverlapAt130PercentFontScale() throws Throwable {
        runTestOnUiThread(() -> assertGridRows(1.3f, false));
    }

    public void testMixedTitlesAndSelectionDoNotMoveTheFollowingRow() throws Throwable {
        runTestOnUiThread(() -> assertGridRows(1.3f, true));
    }

    private void assertGridRows(float fontScale, boolean selecting) {
        Context target = getInstrumentation().getTargetContext();
        Configuration config = new Configuration(target.getResources().getConfiguration());
        config.fontScale = fontScale;
        Context context = target.createConfigurationContext(config);
        GridView grid = new GridView(context);
        float density = context.getResources().getDisplayMetrics().density;
        int gap = Math.round(16 * density);
        grid.setNumColumns(2);
        grid.setColumnWidth(Math.round(136 * density));
        grid.setVerticalSpacing(gap);
        grid.setHorizontalSpacing(gap);
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setAdapter(new BaseAdapter() {
            @Override public int getCount() { return 6; }
            @Override public Object getItem(int position) { return position; }
            @Override public long getItemId(int position) { return position; }
            @Override public View getView(int position, View convertView, ViewGroup parent) {
                View card = convertView != null ? convertView : LayoutInflater.from(context)
                        .inflate(R.layout.item_document_card, parent, false);
                boolean longCard = position % 2 == 0;
                ((TextView) card.findViewById(R.id.txt_doc_title)).setText(longCard
                        ? "课程复习材料可视化原型 🧪 试验版封面.svg" : "A.svg");
                ((TextView) card.findViewById(R.id.txt_doc_tag)).setText(longCard ? "SVG" : "JSON");
                ((TextView) card.findViewById(R.id.txt_doc_size)).setText(longCard
                        ? "111 KB · 17,280行 · 11月26日" : "4 B");
                card.findViewById(R.id.btn_card_more).setVisibility(
                        selecting ? View.INVISIBLE : View.VISIBLE);
                return card;
            }
        });
        int w = Math.round(360 * density);
        int h = Math.round(820 * density);
        grid.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        grid.layout(0, 0, w, h);

        assertTrue("Two full rows must be measured", grid.getChildCount() >= 4);
        View tallFirst = grid.getChildAt(0);
        View shortSecond = grid.getChildAt(1);
        View nextRow = grid.getChildAt(2);
        TextView firstTitle = tallFirst.findViewById(R.id.txt_doc_title);
        assertEquals("The fixture must still display the two title lines", 2, firstTitle.getLineCount());
        assertEquals("Cards share a row height even with a short second title",
                tallFirst.getHeight(), shortSecond.getHeight());
        assertTrue("Row 2 must leave the configured gap below every row-1 card",
                nextRow.getTop() >= Math.max(tallFirst.getBottom(), shortSecond.getBottom()) + gap);
    }
}
