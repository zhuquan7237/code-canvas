package com.nous.codecanvas.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AiCodeExtractor {

    public static class CodeBlock {
        public final String language;
        public final String code;

        public CodeBlock(String language, String code) {
            this.language = language != null ? language : "";
            this.code = code != null ? code : "";
        }
    }

    private static final Pattern FENCED_BLOCK_PATTERN = Pattern.compile(
            "(?:^|\\r?\\n)[ \\t]*```([a-zA-Z0-9_-]*)[ \\t]*\\r?\\n(.*?)\\r?\\n[ \\t]*```[ \\t]*(?=\\r?\\n|$)",
            Pattern.DOTALL
    );

    /**
     * When a reply is prose with markup in the middle and no code fence, extracting "the code" is a
     * guess — so make it a guess the user can reject. Returns the markup region when the text
     * clearly has prose around it, or null when the whole text already is the markup.
     */
    public static String markupRegion(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        if (trimmed.startsWith("<")) {
            return null;   // already markup; nothing to guess
        }
        String[] lines = text.split("\r?\n", -1);
        int first = -1;
        int last = -1;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.startsWith("<") && line.length() > 1) {
                if (first < 0) {
                    first = i;
                }
                last = i;
            }
        }
        if (first < 0 || last <= first) {
            return null;
        }
        // Only offer the region when it is most of the interesting content: a lone "<br>" in a
        // paragraph is not a code block.
        int regionLines = last - first + 1;
        if (regionLines < 2) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = first; i <= last; i++) {
            sb.append(lines[i]);
            if (i < last) {
                sb.append('\n');
            }
        }
        String region = sb.toString();
        if (region.length() < 16 || region.length() > text.length()) {
            return null;
        }
        return region;
    }

    public static List<CodeBlock> extract(String input) {
        if (input == null) {
            return Collections.emptyList();
        }

        // Handle BOM if present
        String text = input;
        if (text.startsWith("\uFEFF")) {
            text = text.substring(1);
        }

        if (text.trim().isEmpty()) {
            return Collections.emptyList();
        }

        // Raw source may contain markdown examples inside <pre> or JS template strings.
        // Only parse outer markdown wrappers when the response does not begin as source.
        String start = text.trim();
        if (start.startsWith("<") || start.matches("(?s)^(const|let|var|function|import|export|class|document\\.|window\\.).*")) {
            return Collections.singletonList(new CodeBlock("", text));
        }

        List<CodeBlock> result = new ArrayList<>();
        Matcher matcher = FENCED_BLOCK_PATTERN.matcher(text);

        while (matcher.find()) {
            String lang = matcher.group(1).trim();
            String code = matcher.group(2);
            result.add(new CodeBlock(lang, code));
        }

        if (result.isEmpty()) {
            // Unfenced raw content returned as one code block unchanged
            result.add(new CodeBlock("", text));
        }

        return result;
    }
}
