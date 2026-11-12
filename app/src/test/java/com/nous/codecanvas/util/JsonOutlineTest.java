package com.nous.codecanvas.util;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class JsonOutlineTest {

    @Test
    public void objectOpensAndCarriesAPath() {
        String formatted = "{\n  \"name\": \"代码画布\",\n  \"preview\": {\n    \"scripts\": true\n  }\n}";
        String html = JsonOutline.toHtml(formatted);
        assertTrue(html.contains("<details open"));
        assertTrue(html.contains("data-path=\"$.preview\""));
        assertTrue(html.contains("data-path=\"$.preview.scripts\""));
        assertTrue(html.contains("data-path=\"$.name\""));
        assertTrue(html.contains("代码画布"));
    }

    @Test
    public void aLeafIsNotAContainer() {
        String html = JsonOutline.toHtml("{\n  \"n\": 2.40\n}");
        assertTrue(html.contains("2.40"));
        assertTrue(html.contains("data-path=\"$.n\""));
    }
}
