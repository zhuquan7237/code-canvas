package com.nous.codecanvas.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

public class AiCodeExtractor {

    public static final int MAX_BLOCKS = 24;
    private static final char BACKTICK = 96;

    public static class CodeBlock {
        public final String language;
        public final String code;
        private final int wrapperStart;
        private final int wrapperEnd;

        public CodeBlock(String language, String code) {
            this(language, code, -1, -1);
        }

        private CodeBlock(String language, String code, int start, int end) {
            this.language = language != null ? language : "";
            this.code = code != null ? code : "";
            this.wrapperStart = start;
            this.wrapperEnd = end;
        }

        public boolean hasSurroundingText(String original) {
            return wrapperStart >= 0 && (!original.substring(0, wrapperStart).replace("\uFEFF", "").trim().isEmpty()
                    || !original.substring(wrapperEnd).trim().isEmpty());
        }
    }

    private static final Pattern RAW_JS = Pattern.compile(
            "^(?:(?:export\\s+(?:default\\s+)?)?(?:(?:const|let|var)\\s+[$\\w]+\\s*[=:]"
            + "|(?:async\\s+)?function\\s*[$\\w]*\\s*\\("
            + "|class\\s+[$\\w]+(?:\\s+extends\\s+[$\\w]+)?\\s*\\{)"
            + "|import\\s+(?:['\"{*]|[$\\w]+\\s*(?:,|from\\s+['\"]))"
            + "|(?:document|window)\\.[$\\w]+\\s*[.(=:\\[])");
    private static final Pattern LANGUAGE = Pattern.compile("[a-zA-Z0-9_.+#!-]{1,32}");

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
        int first = -1;
        int firstEnd = -1;
        int lastEnd = -1;
        for (int line = 0; line < text.length();) {
            int end = line;
            while (end < text.length() && text.charAt(end) != '\n' && text.charAt(end) != '\r') end++;
            int next = end;
            if (next < text.length() && text.charAt(next) == '\r') next++;
            if (next < text.length() && text.charAt(next) == '\n') next++;
            int mark = line;
            while (mark < end && Character.isWhitespace(text.charAt(mark))) mark++;
            if (mark + 1 < end && text.charAt(mark) == '<') {
                char type = text.charAt(mark + 1);
                if (Character.isLetter(type) || type == '/' || type == '!' || type == '?') {
                    if (first < 0) { first = line; firstEnd = end; }
                    lastEnd = end;
                }
            }
            line = next;
        }
        if (first < 0 || firstEnd == lastEnd || lastEnd - first < 16) return null;
        String region = text.substring(first, lastEnd);
        return RenderKindDetector.detect("", region) == RenderKind.RAW ? null : region;
    }

    /**
     * Whether the text actually contains a fenced code block.
     *
     * <p>{@link #extract} cannot answer this: it never returns an empty list — with no fence it
     * wraps the whole input as one block, which is the right behaviour for "import this reply" and
     * the wrong answer for "is this a code block at all". Anything deciding whether a clip is worth
     * offering has to ask this question instead.</p>
     */
    public static boolean hasFencedBlock(String input) {
        return !extractFenced(input, 1).isEmpty();
    }

    public static List<CodeBlock> extract(String input) {
        if (input == null) return Collections.emptyList();
        String text = input.startsWith("\uFEFF") ? input.substring(1) : input;
        if (text.trim().isEmpty()) return Collections.emptyList();
        List<CodeBlock> fenced = extractFenced(input);
        return fenced.isEmpty() ? Collections.singletonList(new CodeBlock("", text)) : fenced;
    }

    public static List<CodeBlock> extractFenced(String input) {
        return extractFenced(input, MAX_BLOCKS + 1);
    }

    private static List<CodeBlock> extractFenced(String text, int limit) {
        if (text == null || text.isEmpty()) return Collections.emptyList();
        int first = text.startsWith("\uFEFF") ? 1 : 0;
        while (first < text.length() && Character.isWhitespace(text.charAt(first))) first++;
        if (first == text.length() || text.charAt(first) == '<'
                || RAW_JS.matcher(text).region(first, Math.min(text.length(), first + 256)).lookingAt())
            return Collections.emptyList();

        List<CodeBlock> result = new ArrayList<>();
        int opening = -1, body = -1, fenceLength = 0;
        char delimiter = 0;
        String language = "";
        for (int line = text.startsWith("\uFEFF") ? 1 : 0; line < text.length();) {
            int end = line;
            while (end < text.length() && text.charAt(end) != '\n' && text.charAt(end) != '\r') end++;
            int next = end;
            if (next < text.length() && text.charAt(next) == '\r') next++;
            if (next < text.length() && text.charAt(next) == '\n') next++;

            int mark = line;
            while (mark < end && (text.charAt(mark) == ' ' || text.charAt(mark) == '\t')) mark++;
            if (mark < end && (text.charAt(mark) == BACKTICK || text.charAt(mark) == '~')) {
                char candidate = text.charAt(mark);
                int tail = mark + 1;
                while (tail < end && text.charAt(tail) == candidate) tail++;
                int length = tail - mark;
                if (length >= 3) {
                    if (opening == -1) {
                        boolean invalidInfo = false;
                        if (candidate == BACKTICK) {
                            for (int j = tail; j < end; j++)
                                if (text.charAt(j) == BACKTICK) { invalidInfo = true; break; }
                        }
                        if (!invalidInfo) {
                            opening = line;
                            body = next;
                            delimiter = candidate;
                            fenceLength = length;
                            language = languageToken(text, tail, end);
                        }
                    } else if (candidate == delimiter && length >= fenceLength && onlySpace(text, tail, end)) {
                        int bodyEnd = line;
                        if (bodyEnd > body && text.charAt(bodyEnd - 1) == '\n') bodyEnd--;
                        if (bodyEnd > body && text.charAt(bodyEnd - 1) == '\r') bodyEnd--;
                        if (bodyEnd > body && !onlySpace(text, body, bodyEnd))
                            result.add(new CodeBlock(language, text.substring(body, bodyEnd), opening, next));
                        if (result.size() >= limit) return result;
                        opening = -1;
                    }
                }
            }
            line = next;
        }
        return result;
    }

    private static String languageToken(String text, int left, int end) {
        while (left < end && Character.isWhitespace(text.charAt(left))) left++;
        int right = left;
        while (right < end && !Character.isWhitespace(text.charAt(right)) && right - left < 33) right++;
        String token = text.substring(left, right);
        return LANGUAGE.matcher(token).matches() || token.equalsIgnoreCase("text/plain") ? token : "";
    }

    private static boolean onlySpace(String text, int left, int right) {
        for (int j = left; j < right; j++) if (!Character.isWhitespace(text.charAt(j))) return false;
        return true;
    }
}
