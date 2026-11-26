package com.nous.codecanvas.util;

import org.junit.Test;
import static org.junit.Assert.*;

public class JsonOutlineTest {
    @Test public void keepsSeparateExactPathsAndNumbersAcrossObjectArrays() {
        JsonOutline.Result r = JsonOutline.inspect("{\"choices\":[{\"arguments\":{\"a.b\":9007199254740993,"
                + "\"a\":{\"b\":1e-9},\"\\u0078\":null}},{\"arguments\":[]}],\"status\":\"完成\"}");
        assertEquals(-1, r.errorOffset);
        assertFalse(r.ambiguous);
        assertTrue(r.html.contains("$[&#39;choices&#39;][0][&#39;arguments&#39;][&#39;a.b&#39;]"));
        assertTrue(r.html.contains("$[&#39;choices&#39;][0][&#39;arguments&#39;][&#39;a&#39;][&#39;b&#39;]"));
        assertTrue(r.html.contains("$[&#39;choices&#39;][1][&#39;arguments&#39;]"));
        assertTrue(r.html.contains("[&#39;arguments&#39;][&#39;x&#39;]"));
        assertTrue(r.html.contains("$[&#39;status&#39;]"));
        assertTrue(r.html.contains("9007199254740993"));
        assertTrue(r.html.contains("1e-9"));
        assertTrue(r.html.contains("\\u0078"));
    }

    @Test public void noMisleadingPathsForEscapedDuplicatesOrDescendants() {
        JsonOutline.Result r = JsonOutline.inspect("{\"x\":{\"inner\":1},\"\\u0078\":2,\"ok\":{\"x\":3}}");
        assertTrue(r.ambiguous);
        assertTrue(r.html.contains("重复键"));
        assertTrue(r.html.contains("&quot;inner&quot;"));
        assertTrue(r.html.contains("\\u0078"));
        assertFalse(r.html.contains("data-path=\"$[&#39;x&#39;]"));
        assertTrue(r.html.contains("data-path=\"$[&#39;ok&#39;][&#39;x&#39;]"));
    }

    @Test public void encodesUnicodeControlApostropheAndBackslashInMemberNames() {
        JsonOutline.Result r = JsonOutline.inspect(
                "{\"0\":{},\"\":[],\"\\uD83D\\uDE03<\\u0001\\n'\\\\\\\"\":true,\"\\uD800\":false}");
        assertEquals(-1, r.errorOffset);
        assertTrue(r.pathsOmitted);
        assertTrue(r.html.contains("$[&#39;0&#39;]"));
        assertTrue(r.html.contains("$[&#39;&#39;]"));
        assertTrue(r.html.contains("😃&lt;\\u0001\\n\\&#39;\\\\&quot;"));
        assertTrue(r.html.contains("\\uD800"));
        assertTrue(JsonOutline.inspect("[[null]]").html.contains("data-path=\"$[0][0]\""));
    }

    @Test public void strictlyRecognizesScalarsNumbersAndMalformedInput() {
        for (String good : new String[]{"false", "null", "\"\u4e2d\"", "-0", "1.0E+40", "\ufeff[1]"}) {
            assertEquals(good, -1, JsonOutline.inspect(good).errorOffset);
        }
        for (String bad : new String[]{"", "00", "2.", "1e", "+1", "[1,]", "{\"x\":}", "{\"x\":7,}",
                "{\"x\": 1}{\"y\":2}", "\"bad\\x\"", "{\"x\": [1} ", "true\u000b"}) {
            JsonOutline.Result result = JsonOutline.inspect(bad);
            assertTrue(bad, result.errorOffset >= 0 && result.errorOffset <= bad.length());
            assertEquals("", result.html);
        }
        String bad = "{\r\n \"x\": ]}";
        assertArrayEquals(new int[]{2, 7}, JsonOutline.position(bad, JsonOutline.inspect(bad).errorOffset));
    }

    @Test public void renderAndPathBudgetsHaveExplicitBoundaries() {
        String tooDeep = "[".repeat(JsonOutline.MAX_DEPTH + 1) + "0"
                + "]".repeat(JsonOutline.MAX_DEPTH + 1);
        assertTrue(JsonOutline.inspect(tooDeep).limited);
        assertTrue(JsonOutline.inspect(" ".repeat(JsonOutline.MAX_TEXT + 1)).limited);
        assertTrue(JsonOutline.inspect("[" + "0,".repeat(JsonOutline.MAX_NODES) + "0]").limited);
        JsonOutline.Result hugeKey = JsonOutline.inspect("{\"" + "x".repeat(16000) + "\":2}");
        assertTrue(hugeKey.pathsOmitted);
        assertFalse(hugeKey.limited);
    }

    @Test public void capsIndentWithoutLosingDeepNodes() {
        JsonOutline.Result deep = JsonOutline.inspect("[".repeat(40) + "123" + "]".repeat(40));
        assertFalse(deep.limited);
        assertEquals(9, deep.html.split("class=\"cc-children\"", -1).length - 1);
        assertTrue(deep.html.contains("data-path=\"$" + "[0]".repeat(40) + "\""));
    }

    @Test public void htmlDoesNotInterpretOriginalTokensOrPathAttributeAsMarkup() {
        JsonOutline.Result r = JsonOutline.inspect("{\"<b>&\\\" x\":\"</script>  <svg onload=foo>\"}");
        assertEquals(-1, r.errorOffset);
        assertFalse(r.html.contains("<svg"));
        assertFalse(r.html.contains("</script>"));
        assertTrue(r.html.contains("&lt;/script&gt;  &lt;svg onload=foo&gt;"));
        assertTrue(r.html.contains("data-path=\"$[&#39;&lt;b&gt;&amp;&quot; x&#39;]\""));
    }
}
