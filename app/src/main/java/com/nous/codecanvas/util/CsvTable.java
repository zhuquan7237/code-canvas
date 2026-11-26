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
        public final boolean inputClipped;

        Table(int columnCount, boolean inputClipped) {
            this.columnCount = columnCount;
            this.inputClipped = inputClipped;
        }
    }

    private CsvTable() {
    }

    public static Table parse(String text) {
        return parse(text, ',');
    }

    public static Table parse(String text, char separator) {
        return parsePrefix(text, separator, Integer.MAX_VALUE);
    }

    /** Preview only: stop before a partial record and leave the original untouched. */
    public static Table parsePreview(String text, char separator) {
        return parsePrefix(text, separator, 128 * 1024);
    }

    private static Table parsePrefix(String text, char separator, int maxChars) {
        String source = text == null ? "" : text;

        List<Row> rows = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        List<String> cells = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        boolean fieldStarted = false;
        boolean recordQuoted = false;
        int keepRows = 0;
        int line = 1;
        int recordLine = 1;
        int maxColumns = 0;

        // Skip BOM without copying the input.
        int i = !source.isEmpty() && source.charAt(0) == '\uFEFF' ? 1 : 0;
        int n = Math.min(source.length(), maxChars);
        boolean inputClipped = n < source.length();
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
                if (c == '\r' || (c == '\n' && source.charAt(i - 1) != '\r')) line++;
                cell.append(c);
                i++;
                continue;
            }
            if (c == '"' && !fieldStarted) {
                quoted = true;
                fieldStarted = true;
                recordQuoted = true;
                i++;
                continue;
            }
            if (c == separator) {
                cells.add(cell.toString());
                cell.setLength(0);
                fieldStarted = false;
                i++;
                continue;
            }
            if (c == '\r' || c == '\n') {
                cells.add(cell.toString());
                cell.setLength(0);
                Row row = new Row(new ArrayList<>(cells), recordLine);
                rows.add(row);
                maxColumns = Math.max(maxColumns, cells.size());
                if (recordQuoted || cells.size() > 1 || !cells.get(0).trim().isEmpty()) {
                    keepRows = rows.size();
                }
                cells.clear();
                fieldStarted = false;
                recordQuoted = false;
                if (c == '\r' && i + 1 < n && source.charAt(i + 1) == '\n') i++;
                i++;
                line++;
                recordLine = line;
                continue;
            }
            cell.append(c);
            fieldStarted = true;
            i++;
        }
        if (quoted && !inputClipped) {
            errors.add("第 " + recordLine + " 行起的引号没有闭合，最后一段被当作一个单元格");
        }
        if (!inputClipped && (fieldStarted || !cells.isEmpty())) {
            cells.add(cell.toString());
            Row row = new Row(new ArrayList<>(cells), recordLine);
            rows.add(row);
            maxColumns = Math.max(maxColumns, cells.size());
            if (recordQuoted || cells.size() > 1 || !cells.get(0).trim().isEmpty()) {
                keepRows = rows.size();
            }
        }

        // Drop only trailing bare blank records, not explicit quoted blanks.
        rows.subList(keepRows, rows.size()).clear();
        if (rows.isEmpty()) maxColumns = 0;

        Table table = new Table(maxColumns, inputClipped);
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

    public static char guessSeparator(String text) {
        return guessSeparator(text, ',');
    }

    /** Score consistent widths over complete logical records, ignoring quoted separators. */
    public static char guessSeparator(String text, char preferred) {
        if (text == null || text.isEmpty()) return preferred;
        char winner = preferred;
        long best = separatorScore(text, preferred);
        for (char candidate : new char[]{',', '\t', ';'}) {
            if (candidate == preferred) continue;
            long score = separatorScore(text, candidate);
            if (score > best) {
                winner = candidate;
                best = score;
            }
        }
        return winner;
    }

    private static long separatorScore(String source, char separator) {
        int limit = Math.min(8192, source.length());
        int start = source.charAt(0) == '\uFEFF' ? 1 : 0;
        int width = 1;
        int records = 0;
        int multi = 0;
        int bestFrequency = 0;
        // At most 8192 separators; scoring allocates no strings or cell lists.
        int[] frequencies = new int[limit + 2];
        boolean quoted = false;
        boolean closedQuote = false;
        boolean malformed = false;
        boolean recordMalformed = false;
        boolean started = false;
        boolean significant = false;
        for (int i = start; i <= limit && records < 20; i++) {
            boolean eof = i == limit;
            if (eof && (limit != source.length() || quoted)) break;
            char c = eof ? '\n' : source.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < limit && source.charAt(i + 1) == '"') i++;
                    else {
                        quoted = false;
                        closedQuote = true;
                    }
                }
            } else if (c == '"' && !started) {
                started = significant = quoted = true;
            } else if (c == separator) {
                width++;
                closedQuote = started = false;
                significant = true;
            } else if (c == '\r' || c == '\n') {
                if (significant) {
                    records++;
                    malformed |= recordMalformed;
                    if (width > 1) {
                        multi++;
                        bestFrequency = Math.max(bestFrequency, ++frequencies[width]);
                    }
                }
                if (c == '\r' && i + 1 < limit && source.charAt(i + 1) == '\n') i++;
                width = 1;
                closedQuote = recordMalformed = started = significant = false;
            } else {
                // A candidate that failed to recognize a quoted column must not
                // treat its embedded newlines as convincing independent records.
                if (c == '"' || (closedQuote && !Character.isWhitespace(c))) recordMalformed = true;
                started = true;
                if (!Character.isWhitespace(c)) significant = true;
            }
        }
        if (bestFrequency == 0) return 0;
        // Prefer repeatable multi-column rows, then fewer ragged records.
        // Truly ambiguous samples keep the caller's format preference.
        return (malformed ? 0L : 1_000_000L) + 10000L * bestFrequency - 100L * (records - bestFrequency) + multi;
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
