package com.nous.codecanvas.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;

/** Extension / MIME / magic-byte triage. */
public class FileKindDetectorTest {

    private static byte[] utf8(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    public void everyFirstStageFormatIsRecognisedByExtension() {
        assertEquals(DocumentKind.HTML, FileKindDetector.detect("a.html", null, utf8("<html>")));
        assertEquals(DocumentKind.HTML, FileKindDetector.detect("a.htm", null, utf8("<html>")));
        assertEquals(DocumentKind.SVG, FileKindDetector.detect("a.svg", null, utf8("<svg/>")));
        assertEquals(DocumentKind.MARKDOWN, FileKindDetector.detect("a.md", null, utf8("# t")));
        assertEquals(DocumentKind.MARKDOWN, FileKindDetector.detect("a.markdown", null, utf8("# t")));
        assertEquals(DocumentKind.MERMAID, FileKindDetector.detect("a.mmd", null, utf8("graph TD")));
        assertEquals(DocumentKind.JSON, FileKindDetector.detect("a.json", null, utf8("{}")));
        assertEquals(DocumentKind.JSONL, FileKindDetector.detect("a.jsonl", null, utf8("{}\n{}")));
        assertEquals(DocumentKind.XML, FileKindDetector.detect("a.xml", null, utf8("<a/>")));
        assertEquals(DocumentKind.YAML, FileKindDetector.detect("a.yaml", null, utf8("a: 1")));
        assertEquals(DocumentKind.YAML, FileKindDetector.detect("a.yml", null, utf8("a: 1")));
        assertEquals(DocumentKind.CSV, FileKindDetector.detect("a.csv", null, utf8("a,b")));
        assertEquals(DocumentKind.TEXT, FileKindDetector.detect("a.txt", null, utf8("hi")));
        assertEquals(DocumentKind.IMAGE, FileKindDetector.detect("a.png", null, new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D}));
        assertEquals(DocumentKind.IMAGE, FileKindDetector.detect("a.jpg", null, new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0}));
        assertEquals(DocumentKind.GIF, FileKindDetector.detect("a.gif", null, utf8("GIF89a")));
        assertEquals(DocumentKind.PDF, FileKindDetector.detect("a.pdf", null, utf8("%PDF-1.7")));
    }

    @Test
    public void requiredCodeLanguagesAreRecognised() {
        String[] names = {"m.py", "m.js", "m.ts", "m.java", "m.kt", "m.c", "m.cpp", "m.css", "m.sql", "m.sh"};
        for (String name : names) {
            assertEquals(name + " must be code", DocumentKind.CODE,
                    FileKindDetector.detect(name, null, utf8("x")));
        }
    }

    @Test
    public void unknownLanguageOpensAsPlainText() {
        assertEquals(DocumentKind.TEXT, FileKindDetector.detect("notes.weird", "text/plain", utf8("hello")));
    }

    @Test
    public void magicBytesBeatAMisleadingExtension() {
        // A PDF that was renamed to .txt is still a PDF; decoding it as UTF-8 would be corruption.
        assertEquals(DocumentKind.PDF, FileKindDetector.detect("renamed.txt", "text/plain", utf8("%PDF-1.4 ...")));
        assertEquals(DocumentKind.GIF, FileKindDetector.detect("renamed.txt", null, utf8("GIF87a")));
    }

    @Test
    public void mimeIsUsedWhenThereIsNoExtension() {
        assertEquals(DocumentKind.PDF, FileKindDetector.detect("download", "application/pdf", null));
        assertEquals(DocumentKind.IMAGE, FileKindDetector.detect("download", "image/jpeg", null));
        assertEquals(DocumentKind.JSON, FileKindDetector.detect("download", "application/json", null));
    }

    @Test
    public void octetStreamAndWildcardAreNotTrustedAsAType() {
        assertEquals(DocumentKind.TEXT, FileKindDetector.detect("x", "application/octet-stream", utf8("plain words")));
        assertEquals(DocumentKind.TEXT, FileKindDetector.detect("x", "*/*", utf8("plain words")));
    }

    @Test
    public void binaryPayloadWithoutAKnownTypeIsRefused() {
        byte[] pngish = new byte[]{(byte) 0x00, (byte) 0x01, (byte) 0x02, (byte) 0x00, (byte) 0xFF};
        assertEquals(DocumentKind.BINARY, FileKindDetector.detect("mystery", null, pngish));
    }

    @Test
    public void editabilityMatchesTheSpec() {
        assertFalse(DocumentKind.IMAGE.editable());
        assertFalse(DocumentKind.GIF.editable());
        assertFalse(DocumentKind.PDF.editable());
        assertTrue(DocumentKind.HTML.editable());
        assertTrue(DocumentKind.SVG.editable());
        assertTrue(DocumentKind.CSV.editable());
        assertTrue(DocumentKind.CODE.editable());
    }

    @Test
    public void defaultViewsMatchTheSpec() {
        assertEquals(DocumentKind.DefaultView.PREVIEW, DocumentKind.HTML.defaultView());
        assertEquals(DocumentKind.DefaultView.PREVIEW, DocumentKind.SVG.defaultView());
        assertEquals(DocumentKind.DefaultView.PREVIEW, DocumentKind.MARKDOWN.defaultView());
        assertEquals(DocumentKind.DefaultView.PREVIEW, DocumentKind.MERMAID.defaultView());
        assertEquals(DocumentKind.DefaultView.CODE, DocumentKind.JSON.defaultView());
        assertEquals(DocumentKind.DefaultView.CODE, DocumentKind.JSONL.defaultView());
        assertEquals(DocumentKind.DefaultView.CODE, DocumentKind.YAML.defaultView());
        assertEquals(DocumentKind.DefaultView.STRUCTURE, DocumentKind.XML.defaultView());
        assertEquals(DocumentKind.DefaultView.TABLE, DocumentKind.CSV.defaultView());
        assertEquals(DocumentKind.DefaultView.IMAGE, DocumentKind.IMAGE.defaultView());
        assertEquals(DocumentKind.DefaultView.PDF, DocumentKind.PDF.defaultView());
    }

    @Test
    public void extensionOfHandlesPathsAndDots() {
        assertEquals("svg", FileKindDetector.extensionOf("/sdcard/a/b/icon.SVG"));
        assertEquals("", FileKindDetector.extensionOf("noextension"));
        assertEquals("", FileKindDetector.extensionOf("trailing."));
        assertEquals("dockerfile", FileKindDetector.extensionOf("Dockerfile"));
    }
}
