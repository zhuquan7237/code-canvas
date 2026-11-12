package com.nous.codecanvas.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A CSV/TSV reader that follows the actual quoting rules.
 *
 * <p>Splitting on commas is the classic bug: it turns {@code "a,b",c} into three cells and breaks
 * on any cell containing a newline. This reader handles quoted fields, doubled quotes inside them,
 * embedded commas and embedded line breaks, and remembers which physical line each record started
 * on so an error can point at a real place in the file.</p>
 */
public final class CsvTable {

    /** One parsed record. */
    public static final class Row {
        public final List<String> cells;
        /** 1-based line in the source where this record began. */
        public final int line;

        Row(List<String> cells, int line) {
            this.cells = cells;
            this.line = line;
        }
    }

    public static final class Table {
        public final List<Row> rows = new ArrayList<>();
        public final List<String> errors = new ArrayList<>();
        public final int columnCount;

        Table(int columnCount) {
            this.columnCount = columnCount;
        }
    }

    private CsvTable() {
    }

    public static Table parse(String text) {
        return parse(text, ',');
    }

    public static Table parse(String text, char separator) {
        String source = text == null ? "" : text;
        // A UTF-8 BOM would otherwise become part of the first header cell.
        if (!source.isEmpty() && source.charAt(0) == '\uFEFF') source = source.substring(1);

        List<Row> rows = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        List<String> cells = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        int line = 1;
        int recordLine = 1;
        int maxColumns = 0;

        int i = 0;
        int n = source.length();
        while (i < n) {
            char c = source.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < n && source.charAt(i + 1) == '"') {
                        cell.append('"');
                        i += 2;
                        continue;
                    }
                    quoted = false;
                    i++;
                    continue;
                }
                if (c == '\n') line++;
                cell.append(c);
                i++;
                continue;
            }
            if (c == '"' && cell.length() == 0) {
                quoted = true;
                i++;
                continue;
            }
            if (c == separator) {
                cells.add(cell.toString());
                cell.setLength(0);
                i++;
                continue;
            }
            if (c == '\r' || c == '\n') {
                cells.add(cell.toString());
                cell.setLength(0);
                Row row = new Row(new ArrayList<>(cells), recordLine);
                rows.add(row);
                maxColumns = Math.max(maxColumns, cells.size());
                cells.clear();
                if (c == '\r' && i + 1 < n && source.charAt(i + 1) == '\n') i++;
                i++;
                line++;
                recordLine = line;
                continue;
            }
            cell.append(c);
            i++;
        }
        if (quoted) {
            errors.add("第 " + recordLine + " 行起的引号没有闭合，最后一段被当作一个单元格");
        }
        if (cell.length() > 0 || !cells.isEmpty()) {
            cells.add(cell.toString());
            Row row = new Row(new ArrayList<>(cells), recordLine);
            rows.add(row);
            maxColumns = Math.max(maxColumns, cells.size());
        }

        // A trailing empty line is not a row.
        while (!rows.isEmpty() && rows.get(rows.size() - 1).cells.size() == 1
                && rows.get(rows.size() - 1).cells.get(0).trim().isEmpty()) {
            rows.remove(rows.size() - 1);
        }

        Table table = new Table(maxColumns);
        table.rows.addAll(rows);
        table.errors.addAll(errors);
        for (Row row : rows) {
            if (row.cells.size() != maxColumns) {
                table.errors.add("第 " + row.line + " 行有 " + row.cells.size()
                        + " 列，与最多的 " + maxColumns + " 列不一致");
            }
        }
        return table;
    }

    /**
     * Guess the separator from the first several records. A header with no delimiter used to hide
     * a tab-separated body, because the scan stopped at the first newline.
     */
    public static char guessSeparator(String text) {
        if (text == null) return ',';
        int tabs = 0, commas = 0, semicolons = 0, lines = 0;
        int limit = Math.min(text.length(), 8192);
        for (int i = 0; i < limit; i++) {
            char c = text.charAt(i);
            if (c == '\n') {
                lines++;
                if (lines >= 20) break;
                continue;
            }
            if (c == '\t') tabs++;
            else if (c == ',') commas++;
            else if (c == ';') semicolons++;
        }
        if (tabs > commas && tabs > semicolons) return '\t';
        if (semicolons > commas && semicolons > tabs) return ';';
        return ',';
    }

    public static String separatorName(char separator) {
        switch (separator) {
            case '\t': return "制表符";
            case ';': return "分号";
            default: return "逗号";
        }
    }

    /** A short summary for the UI: rows, columns, and the separator that was used. */
    public static String describe(Table table, char separator) {
        if (table == null) return "";
        return table.rows.size() + " 行 × " + table.columnCount + " 列 · "
                + separatorName(separator) + "分隔";
    }

    static String normalizeHeader(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    }
}
