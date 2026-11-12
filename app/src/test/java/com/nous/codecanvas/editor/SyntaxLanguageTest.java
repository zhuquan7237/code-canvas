package com.nous.codecanvas.editor;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SyntaxLanguageTest {

    @Test
    public void extensionsPickALanguage() {
        assertEquals("py", SyntaxHighlighter.languageForTitle("hello.py"));
        assertEquals("js", SyntaxHighlighter.languageForTitle("app.tsx"));
        assertEquals("kt", SyntaxHighlighter.languageForTitle("Main.kt"));
        assertEquals("css", SyntaxHighlighter.languageForTitle("theme.scss"));
        assertEquals("sql", SyntaxHighlighter.languageForTitle("q.SQL"));
        assertEquals("sh", SyntaxHighlighter.languageForTitle("run.bash"));
        assertEquals("yaml", SyntaxHighlighter.languageForTitle("app.yml"));
        assertEquals("code", SyntaxHighlighter.languageForTitle("notes.txt"));
    }
}
