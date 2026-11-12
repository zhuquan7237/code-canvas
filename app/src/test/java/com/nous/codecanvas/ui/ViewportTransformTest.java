package com.nous.codecanvas.ui;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ViewportTransformTest {

    @Test
    public void pinchKeepsThePointBetweenTheFingersFixed() {
        ViewportTransform t = new ViewportTransform();
        t.viewW = 400f;
        t.viewH = 800f;
        t.tx = 12f;
        t.ty = -8f;
        t.scale = 1.4f;
        float cx = t.contentX(180f);
        float cy = t.contentY(260f);
        t.zoomAbout(180f, 260f, 2.2f);
        assertEquals(cx, t.contentX(180f), 0.02f);
        assertEquals(cy, t.contentY(260f), 0.02f);
        assertEquals(1.4f * 2.2f, t.scale, 0.02f);
    }

    @Test
    public void buttonsZoomAroundTheCentre() {
        ViewportTransform t = new ViewportTransform();
        t.viewW = 300f;
        t.viewH = 600f;
        float cx = t.contentX(150f);
        float cy = t.contentY(300f);
        t.zoomAbout(150f, 300f, 1.4f);
        assertEquals(cx, t.contentX(150f), 0.02f);
        assertEquals(cy, t.contentY(300f), 0.02f);
    }

    @Test
    public void doubleTapZoomsAroundTheTapThenBack() {
        ViewportTransform t = new ViewportTransform();
        t.viewW = 400f;
        t.viewH = 800f;
        float cx = t.contentX(90f);
        float cy = t.contentY(140f);
        t.doubleTap(90f, 140f);
        assertEquals(ViewportTransform.DOUBLE_TAP_SCALE, t.scale, 0.02f);
        assertEquals(cx, t.contentX(90f), 0.02f);
        assertEquals(cy, t.contentY(140f), 0.02f);
        t.doubleTap(90f, 140f);
        assertEquals(1f, t.scale, 0.02f);
        assertEquals(cx, t.contentX(90f), 0.05f);
    }

    @Test
    public void overscrollIsDampedAndThenSettlesInside() {
        ViewportTransform t = new ViewportTransform();
        t.viewW = 200f;
        t.viewH = 200f;
        t.pan(5000f, 5000f);
        t.dampEdges();
        float resistedX = t.tx;
        float hard = t.viewW - Math.min(t.viewW, t.viewW * t.scale) * 0.15f;
        assertTrue(resistedX < 5000f);
        assertTrue(resistedX > hard);
        t.settleEdges();
        assertTrue(t.tx <= hard + 0.01f);
    }

    @Test
    public void flingMovesThenComesToRest() {
        ViewportTransform t = new ViewportTransform();
        t.viewW = 400f;
        t.viewH = 800f;
        t.scale = 2f;
        t.fling(-900f, -400f);
        float start = t.tx;
        boolean moving = false;
        for (int i = 0; i < 90; i++) moving = t.step(0.016f) || moving;
        assertTrue(t.tx != start || t.ty != 0f);
        assertEquals(0f, t.vx, 0.01f);
        assertEquals(0f, t.vy, 0.01f);
    }
}
