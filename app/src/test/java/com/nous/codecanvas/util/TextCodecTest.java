package com.nous.codecanvas.util;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/** Encoding detection and byte-faithful round trips. */
public class TextCodecTest {

    @Test
    public void utf8ChineseRoundTripsExactly() {
        String text = "中文测试\n第二行";
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        TextCodec.Decoded decoded = TextCodec.decode(bytes);
        assertEquals(TextCodec.UTF8, decoded.encoding);
        assertEquals(text, decoded.text);
        assertArrayEquals(bytes, TextCodec.encode(decoded.text, decoded.encoding));
    }

    @Test
    public void gb18030IsDetectedAndWrittenBackAsGb18030() {
        Charset gb = Charset.forName("GB18030");
        String text = "异种不锈钢激光焊接\n焊缝成形";
        byte[] bytes = text.getBytes(gb);
        TextCodec.Decoded decoded = TextCodec.decode(bytes);
        assertEquals(TextCodec.GB18030, decoded.encoding);
        assertEquals(text, decoded.text);
        // The point of remembering the charset: re-encoding produces the original bytes, not UTF-8.
        assertArrayEquals(bytes, TextCodec.encode(decoded.text, decoded.encoding));
        assertFalse(java.util.Arrays.equals(bytes, text.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    public void utf8BomIsStrippedAndTheCharsetRemembered() {
        byte[] bom = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] body = "hello".getBytes(StandardCharsets.UTF_8);
        byte[] withBom = new byte[bom.length + body.length];
        System.arraycopy(bom, 0, withBom, 0, bom.length);
        System.arraycopy(body, 0, withBom, bom.length, body.length);

        TextCodec.Decoded decoded = TextCodec.decode(withBom);
        assertEquals(TextCodec.UTF8, decoded.encoding);
        assertEquals("hello", decoded.text);
    }

    @Test
    public void utf16LittleEndianBomIsHonoured() {
        byte[] bytes = "编码".getBytes(StandardCharsets.UTF_16LE);
        byte[] withBom = new byte[bytes.length + 2];
        withBom[0] = (byte) 0xFF;
        withBom[1] = (byte) 0xFE;
        System.arraycopy(bytes, 0, withBom, 2, bytes.length);

        TextCodec.Decoded decoded = TextCodec.decode(withBom);
        assertEquals(TextCodec.UTF16LE, decoded.encoding);
        assertEquals("编码", decoded.text);
    }

    @Test
    public void crlfIsDetectedAndPreservedOnWrite() {
        String text = "a\r\nb\r\nc";
        TextCodec.Decoded decoded = TextCodec.decode(text.getBytes(StandardCharsets.UTF_8));
        assertEquals("\r\n", decoded.lineEnding);
        String edited = decoded.text + "\r\nd";
        assertEquals(text + "\r\nd", TextCodec.normalizeLineEndings(edited, decoded.lineEnding));
    }

    @Test
    public void loneLfIsDetected() {
        assertEquals("\n", TextCodec.detectLineEnding("a\nb\nc"));
    }

    @Test
    public void mixedEndingsNormaliseToTheDominantOne() {
        String mixed = "a\r\nb\nc\r\nd";
        assertEquals("a\r\nb\r\nc\r\nd", TextCodec.normalizeLineEndings(mixed, "\r\n"));
    }

    @Test
    public void unencodableCharactersAreReportedRatherThanSilentlyReplaced() {
        // Latin-1 cannot represent Chinese at all — the case where writing back would silently
        // replace every character with '?'.
        assertFalse(TextCodec.canEncode("焊缝", TextCodec.LATIN1));
        assertTrue(TextCodec.canEncode("plain ascii", TextCodec.LATIN1));
        assertTrue(TextCodec.canEncode("焊缝 😀", TextCodec.UTF8));
        // GB18030 covers all of Unicode, so it can hold Chinese, emoji and the rest.
        assertTrue(TextCodec.canEncode("焊缝 😀", TextCodec.GB18030));
    }

    @Test
    public void invalidUtf8FallsBackWithoutThrowing() {
        byte[] bytes = new byte[]{(byte) 0xC3, (byte) 0x28, (byte) 0xA0, (byte) 0xA1, 0x41};
        TextCodec.Decoded decoded = TextCodec.decode(bytes);
        assertTrue("must produce some text rather than throwing", decoded.text.length() > 0);
    }

    @Test
    public void emptyInputIsSafe() {
        TextCodec.Decoded decoded = TextCodec.decode(new byte[0]);
        assertEquals("", decoded.text);
        assertEquals(TextCodec.UTF8, decoded.encoding);
        assertEquals("\n", decoded.lineEnding);
    }
}
