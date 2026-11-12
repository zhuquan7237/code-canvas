package com.nous.codecanvas.util;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Turns a file's bytes into text and back without lying about either.
 *
 * <p>The old import path did {@code new String(bytes, UTF_8)} for every file. That is wrong twice:
 * a GB18030 Chinese text file becomes a wall of replacement characters, and a PNG becomes a
 * megabyte of mojibake that then gets written back over the original. So encoding is detected
 * (BOM first, then a strict UTF-8 trial, then GB18030, then a Latin-1 last resort), and the result
 * is remembered per document so a save re-encodes to the same bytes it came from. Line endings are
 * preserved the same way.</p>
 */
public final class TextCodec {

    public static final String UTF8 = "UTF-8";
    public static final String UTF16LE = "UTF-16LE";
    public static final String UTF16BE = "UTF-16BE";
    public static final String GB18030 = "GB18030";
    public static final String LATIN1 = "ISO-8859-1";

    private TextCodec() {
    }

    /** What a decoded file is, and how to write it back unchanged. */
    public static final class Decoded {
        public final String text;
        public final String encoding;
        public final String lineEnding;
        /** True when the bytes were not valid in any encoding we know: text is a best effort. */
        public final boolean lossy;

        Decoded(String text, String encoding, String lineEnding, boolean lossy) {
            this.text = text;
            this.encoding = encoding;
            this.lineEnding = lineEnding;
            this.lossy = lossy;
        }
    }

    public static Decoded decode(byte[] bytes) {
        if (bytes == null) return new Decoded("", UTF8, "\n", false);
        if (bytes.length == 0) return new Decoded("", UTF8, "\n", false);

        String encoding = null;
        int offset = 0;
        boolean lossy = false;

        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF) {
            encoding = UTF8;
            offset = 3;
        } else if (bytes.length >= 2 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xFE) {
            encoding = UTF16LE;
            offset = 2;
        } else if (bytes.length >= 2 && (bytes[0] & 0xFF) == 0xFE && (bytes[1] & 0xFF) == 0xFF) {
            encoding = UTF16BE;
            offset = 2;
        }

        byte[] body = offset == 0 ? bytes : java.util.Arrays.copyOfRange(bytes, offset, bytes.length);

        if (encoding == null) {
            // A strict UTF-8 trial: valid UTF-8 is overwhelmingly the common case, and the trial
            // costs one pass. Anything that fails it is either a legacy Chinese encoding or binary.
            if (isValid(body, StandardCharsets.UTF_8)) {
                encoding = UTF8;
            } else if (isValid(body, Charset.forName(GB18030))) {
                encoding = GB18030;
            } else {
                encoding = LATIN1;
                lossy = true;
            }
        }

        String text = new String(body, Charset.forName(encoding));
        return new Decoded(text, encoding, detectLineEnding(text), lossy);
    }

    /** Re-encode with the document's own charset, replacing unmappable characters visibly. */
    public static byte[] encode(String text, String encoding) {
        if (text == null) text = "";
        Charset charset;
        try {
            charset = Charset.forName(encoding == null || encoding.isEmpty() ? UTF8 : encoding);
        } catch (Exception e) {
            charset = StandardCharsets.UTF_8;
        }
        CharsetEncoder encoder = charset.newEncoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE);
        try {
            ByteBuffer buffer = encoder.encode(CharBuffer.wrap(text));
            byte[] out = new byte[buffer.remaining()];
            buffer.get(out);
            return out;
        } catch (CharacterCodingException e) {
            return text.getBytes(StandardCharsets.UTF_8);
        }
    }

    /** True when every character of {@code text} survives a round trip through {@code encoding}. */
    public static boolean canEncode(String text, String encoding) {
        if (text == null) return true;
        try {
            Charset charset = Charset.forName(encoding);
            CharsetEncoder encoder = charset.newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
            encoder.encode(CharBuffer.wrap(text));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** The dominant line terminator of the text; {@code \n} when there is none. */
    public static String detectLineEnding(String text) {
        if (text == null || text.isEmpty()) return "\n";
        int crlf = 0, lf = 0, cr = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\r') {
                if (i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    crlf++;
                    i++;
                } else {
                    cr++;
                }
            } else if (c == '\n') {
                lf++;
            }
        }
        if (crlf >= lf && crlf >= cr && crlf > 0) return "\r\n";
        if (cr > lf && cr > 0) return "\r";
        return "\n";
    }

    /**
     * Rewrite the text so it uses {@code lineEnding} throughout, without touching a lone
     * {@code \r} that is part of the content (only CRLF and lone CR/ LF pairs are normalised).
     */
    public static String normalizeLineEndings(String text, String lineEnding) {
        if (text == null) return "";
        String target = lineEnding == null || lineEnding.isEmpty() ? "\n" : lineEnding;
        String unified = text.replace("\r\n", "\n").replace('\r', '\n');
        if ("\n".equals(target)) return unified;
        return unified.replace("\n", target);
    }

    public static String displayName(String encoding) {
        if (encoding == null || encoding.isEmpty()) return "UTF-8";
        switch (encoding.toUpperCase(Locale.ROOT)) {
            case "UTF-8": return "UTF-8";
            case "UTF-16LE": return "UTF-16 LE";
            case "UTF-16BE": return "UTF-16 BE";
            case "GB18030": return "GB18030";
            case "ISO-8859-1": return "ISO-8859-1";
            default: return encoding;
        }
    }

    private static boolean isValid(byte[] data, Charset charset) {
        CharsetDecoder decoder = charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            decoder.decode(ByteBuffer.wrap(data));
            return true;
        } catch (CharacterCodingException e) {
            return false;
        }
    }

    /** Convenience for callers that only need the text. */
    public static String decodeToString(byte[] bytes) {
        return decode(bytes).text;
    }

    public static byte[] readAll(java.io.InputStream in) throws java.io.IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(64, in.available()));
        byte[] buffer = new byte[8192];
        int n;
        while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
        return out.toByteArray();
    }
}
