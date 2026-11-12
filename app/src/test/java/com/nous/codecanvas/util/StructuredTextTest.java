package com.nous.codecanvas.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

/** The structured-format rules, with the data-preservation cases that matter most. */
public class StructuredTextTest {

    // --- JSON ----------------------------------------------------------------------------------

    @Test
    public void validJsonIsReindented() {
        StructuredText.JsonResult r = StructuredText.formatJson("{\"a\":1,\"b\":[1,2]}");
        assertTrue(r.valid);
        assertTrue(r.formatted.contains("\n"));
        assertTrue(r.formatted.contains("\"a\": 1"));
    }

    @Test
    public void formattingNeverChangesANumber() {
        // The classic corruption: 1.0 -> 1, and a big integer becoming 1.23E19.
        String source = "{\"a\":1.0,\"b\":12345678901234567890,\"c\":1e400,\"d\":0.10}";
        StructuredText.JsonResult r = StructuredText.formatJson(source);
        assertTrue(r.valid);
        assertTrue("1.0 must stay 1.0", r.formatted.contains("1.0"));
        assertTrue("a big integer must not become an exponent",
                r.formatted.contains("12345678901234567890"));
        assertTrue(r.formatted.contains("1e400"));
        assertTrue(r.formatted.contains("0.10"));
    }

    @Test
    public void formattingKeepsStringEscapesVerbatim() {
        String source = "{\"s\":\"a\\\"b\\\\c\\u4e2d\"}";
        StructuredText.JsonResult r = StructuredText.formatJson(source);
        assertTrue(r.valid);
        assertTrue(r.formatted.contains("a\\\"b\\\\c\\u4e2d"));
    }

    @Test
    public void invalidJsonKeepsTheOriginalAndReportsAPosition() {
        StructuredText.JsonResult r = StructuredText.formatJson("{\"a\": }");
        assertFalse(r.valid);
        assertEquals(null, r.formatted);
        assertNotNull(r.error);
        assertTrue("must point somewhere concrete", r.errorLabel().length() > 0);
    }

    @Test
    public void unbalancedBracesAreLocated() {
        StructuredText.JsonResult r = StructuredText.formatJson("{\n  \"a\": [1, 2\n}");
        assertFalse(r.valid);
        assertTrue("line must be reported", r.errorLine >= 1);
    }

    @Test
    public void topLevelArrayIsAccepted() {
        StructuredText.JsonResult r = StructuredText.formatJson("[1,2,3]");
        assertTrue(r.valid);
        assertTrue(r.formatted.startsWith("["));
    }

    @Test
    public void reindentLeavesNoStrayWhitespaceInsideStrings() {
        String formatted = StructuredText.reindent("{\"k\":\"a  b\"}");
        assertNotNull(formatted);
        assertTrue(formatted.contains("\"a  b\""));
    }

    // --- JSONL ---------------------------------------------------------------------------------

    @Test
    public void oneBrokenLineDoesNotHideTheOthers() {
        String source = "{\"a\":1}\nnot json\n{\"b\":2}";
        StructuredText.JsonlResult r = StructuredText.parseJsonl(source);
        assertEquals(3, r.lines.size());
        assertEquals(2, r.validCount);
        assertEquals(1, r.invalidCount);
        assertTrue(r.lines.get(0).valid);
        assertFalse(r.lines.get(1).valid);
        assertTrue(r.lines.get(2).valid);
        assertEquals("the failing line number must be the source line", 2, r.lines.get(1).line);
    }

    @Test
    public void blankLinesAreSkippedNotCountedAsErrors() {
        StructuredText.JsonlResult r = StructuredText.parseJsonl("{\"a\":1}\n\n{\"b\":2}\n");
        assertEquals(2, r.lines.size());
        assertEquals(0, r.invalidCount);
    }

    @Test
    public void jsonlSummaryIsHonestWhenEverythingFails() {
        StructuredText.JsonlResult r = StructuredText.parseJsonl("oops\nnope");
        assertEquals(0, r.validCount);
        assertEquals(2, r.invalidCount);
        assertTrue(r.summary().contains("2"));
    }

    // --- YAML ----------------------------------------------------------------------------------

    @Test
    public void tabsInYamlAreReported() {
        List<String> problems = StructuredText.checkYaml("a:\n\tb: 1");
        assertFalse(problems.isEmpty());
        assertTrue(problems.get(0).contains("制表符"));
    }

    @Test
    public void unbalancedFlowBracketsAreReported() {
        List<String> problems = StructuredText.checkYaml("a: [1, 2");
        assertTrue(problems.toString().contains("未闭合"));
    }

    @Test
    public void cleanYamlPassesAndSaysWhatItDidNotCheck() {
        List<String> problems = StructuredText.checkYaml("a:\n  b: 1\n  c:\n    - x\n    - y\n");
        assertTrue(problems.toString(), problems.isEmpty());
        assertTrue(StructuredText.yamlVerdict(problems).contains("未做完整"));
        List<StructuredText.YamlRow> rows = StructuredText.yamlOutline("a:\n  b: 1\n  c:\n    - x\n");
        assertFalse(rows.isEmpty());
        assertEquals(1, rows.get(0).line);
        assertTrue(rows.get(1).depth >= 1);
        assertTrue("a failing verdict must also state the limit",
                StructuredText.yamlVerdict(java.util.Arrays.asList("x")).contains("未做完整"));
    }

    @Test
    public void quotedBracketsDoNotCountAsFlow() {
        List<String> problems = StructuredText.checkYaml("a: \"[not a flow\"");
        assertTrue(problems.toString(), problems.isEmpty());
    }

    // --- CSV -----------------------------------------------------------------------------------

    @Test
    public void quotedCellsKeepCommas() {
        CsvTable.Table t = CsvTable.parse("a,b\n\"x,y\",z");
        assertEquals(2, t.rows.size());
        assertEquals(2, t.rows.get(1).cells.size());
        assertEquals("x,y", t.rows.get(1).cells.get(0));
        assertEquals("z", t.rows.get(1).cells.get(1));
    }

    @Test
    public void quotedCellsKeepNewlines() {
        CsvTable.Table t = CsvTable.parse("a,b\n\"line1\nline2\",z");
        assertEquals(2, t.rows.size());
        assertEquals("line1\nline2", t.rows.get(1).cells.get(0));
    }

    @Test
    public void doubledQuotesBecomeOneQuote() {
        CsvTable.Table t = CsvTable.parse("a\n\"he said \"\"hi\"\"\"");
        assertEquals("he said \"hi\"", t.rows.get(1).cells.get(0));
    }

    @Test
    public void crlfRowsParseWithoutStrayCarriageReturns() {
        CsvTable.Table t = CsvTable.parse("a,b\r\nc,d\r\n");
        assertEquals(2, t.rows.size());
        assertEquals("b", t.rows.get(0).cells.get(1));
        assertEquals("d", t.rows.get(1).cells.get(1));
    }

    @Test
    public void raggedRowsAreReportedWithTheirLineNumbers() {
        CsvTable.Table t = CsvTable.parse("a,b,c\n1,2\n3,4,5");
        assertEquals(3, t.columnCount);
        assertTrue(t.errors.toString().contains("第 2 行"));
    }

    @Test
    public void unclosedQuoteIsReportedRatherThanSwallowingTheFile() {
        CsvTable.Table t = CsvTable.parse("a,b\n\"unterminated,x");
        assertFalse(t.errors.isEmpty());
        assertTrue(t.errors.get(0).contains("引号"));
    }

    @Test
    public void separatorIsGuessedFromTheFirstRecords() {
        assertEquals('\t', CsvTable.guessSeparator("a\tb\tc\n1\t2\t3"));
        assertEquals(',', CsvTable.guessSeparator("a,b,c"));
        assertEquals(';', CsvTable.guessSeparator("a;b;c"));
        assertEquals('\t', CsvTable.guessSeparator("name\na\tb\nc\td"));
    }

    @Test
    public void bomIsNotPartOfTheFirstHeader() {
        CsvTable.Table t = CsvTable.parse("\uFEFFa,b\n1,2");
        assertEquals("a", t.rows.get(0).cells.get(0));
    }

    @Test
    public void trailingNewlineDoesNotAddAnEmptyRow() {
        CsvTable.Table t = CsvTable.parse("a,b\n1,2\n");
        assertEquals(2, t.rows.size());
    }
}
