package com.nous.codecanvas.util;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class StructureViewTest {

    @Test
    public void xmlNestsAndMarksTheErrorLine() {
        String html = StructureView.xmlHtml("<canvas>\n<item id=\"1\">\n</item>\n</canvas>", 2);
        assertTrue(html.contains("<details open"));
        assertTrue(html.contains("class=\"cc-xml bad\""));
        assertTrue(html.contains("jump?line=2"));
    }

    @Test
    public void xmlErrorLineReadsParserText() {
        assertEquals(4, StructureView.xmlErrorLine(
                "The element type must be terminated. lineNumber: 4; columnNumber: 3"));
    }

    @Test
    public void yamlFoldsChildrenAndLinksABadLine() {
        List<StructuredText.YamlRow> rows = java.util.Arrays.asList(
                new StructuredText.YamlRow(1, 0, "preview:", false),
                new StructuredText.YamlRow(2, 1, "scripts: true", false),
                new StructuredText.YamlRow(3, 0, "bad:\t1", true));
        String html = StructureView.yamlHtml(rows);
        assertTrue(html.contains("<details open"));
        assertTrue(html.contains("cc-yaml bad"));
        assertTrue(html.contains("jump?line=3"));
    }
}
