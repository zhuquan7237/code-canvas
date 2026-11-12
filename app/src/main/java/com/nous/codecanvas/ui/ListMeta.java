package com.nous.codecanvas.ui;

import java.util.Calendar;
import java.util.Locale;

/**
 * The second line of a file row. Today is only a time, so 360dp at 1.3x type
 * can still show the size, the line count and when it changed.
 */
public final class ListMeta {

    private ListMeta() {
    }

    public static String whenLabel(long nowMillis, long thenMillis) {
        Calendar now = Calendar.getInstance();
        now.setTimeInMillis(nowMillis);
        Calendar then = Calendar.getInstance();
        then.setTimeInMillis(thenMillis);
        boolean sameYear = now.get(Calendar.YEAR) == then.get(Calendar.YEAR);
        boolean sameDay = sameYear && now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR);
        if (sameDay) {
            return String.format(Locale.getDefault(), "%02d:%02d",
                    then.get(Calendar.HOUR_OF_DAY), then.get(Calendar.MINUTE));
        }
        if (sameYear) {
            return String.format(Locale.getDefault(), "%d-%d %02d:%02d",
                    then.get(Calendar.MONTH) + 1, then.get(Calendar.DAY_OF_MONTH),
                    then.get(Calendar.HOUR_OF_DAY), then.get(Calendar.MINUTE));
        }
        return String.format(Locale.getDefault(), "%d-%d-%d",
                then.get(Calendar.YEAR), then.get(Calendar.MONTH) + 1, then.get(Calendar.DAY_OF_MONTH));
    }

    public static String rowDetail(String sizeText, long nowMillis, long thenMillis) {
        String size = sizeText == null ? "" : sizeText;
        return size + " · " + whenLabel(nowMillis, thenMillis);
    }
}
