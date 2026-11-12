package com.nous.codecanvas.ui;

import org.junit.Test;

import java.util.Calendar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ListMetaTest {

    private static long at(int year, int month, int day, int hour, int minute) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(year, month - 1, day, hour, minute, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    @Test
    public void todayIsJustTheTime() {
        long now = at(2026, 11, 12, 15, 4);
        assertEquals("10:17", ListMeta.whenLabel(now, at(2026, 11, 12, 10, 17)));
    }

    @Test
    public void earlierThisYearKeepsTheDay() {
        long now = at(2026, 11, 12, 15, 0);
        assertEquals("11-2 09:05", ListMeta.whenLabel(now, at(2026, 11, 2, 9, 5)));
    }

    @Test
    public void anotherYearDropsTheClock() {
        long now = at(2026, 11, 12, 15, 0);
        assertEquals("2025-12-31", ListMeta.whenLabel(now, at(2025, 12, 31, 23, 1)));
    }

    @Test
    public void rowDetailKeepsSizeAndWhen() {
        long now = at(2026, 11, 12, 15, 0);
        String detail = ListMeta.rowDetail("215 B · 16 行", now, at(2026, 11, 12, 10, 16));
        assertTrue(detail.startsWith("215 B · 16 行 · "));
        assertTrue(detail.endsWith("10:16"));
    }
}
