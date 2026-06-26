package com.skylogistics.util;

public final class AmountFormatter {
    private static final long UNIT_STEP = 1_000L;
    private static final String[] UNITS = {"K", "M", "G", "T", "P", "E", "Z", "Y"};

    private AmountFormatter() {
    }

    public static String compact(long value) {
        if (value < UNIT_STEP) {
            return Long.toString(value);
        }
        long scaled = value;
        int unitIndex = -1;
        while (scaled >= UNIT_STEP) {
            scaled /= UNIT_STEP;
            unitIndex++;
        }
        return scaled + unit(unitIndex);
    }

    private static String unit(int index) {
        if (index < UNITS.length) {
            return UNITS[index];
        }
        int twoLetterIndex = index - UNITS.length;
        char first = (char) ('A' + twoLetterIndex / 26);
        char second = (char) ('A' + twoLetterIndex % 26);
        return new String(new char[] {first, second});
    }
}
