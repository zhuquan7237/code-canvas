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
