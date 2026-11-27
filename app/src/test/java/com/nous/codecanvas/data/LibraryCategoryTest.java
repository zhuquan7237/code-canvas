package com.nous.codecanvas.data;

import org.junit.Test;
import static org.junit.Assert.*;

public class LibraryCategoryTest {
    @Test public void eachKindHasExactlyOneSpecificCategory() {
        for (DocumentKind kind : DocumentKind.values()) {
            int count = 0;
            for (LibraryCategory category : LibraryCategory.values()) {
                if (category != LibraryCategory.ALL && category.matches(kind)) count++;
            }
            assertEquals(kind.name(), 1, count);
            assertTrue(LibraryCategory.ALL.matches(kind));
        }
    }

    @Test public void structuredFormatsGroupWithoutPullingInNotebooksOrText() {
        for (DocumentKind kind : new DocumentKind[] {DocumentKind.JSON, DocumentKind.JSONL,
                DocumentKind.YAML, DocumentKind.CSV, DocumentKind.XML}) {
            assertTrue(LibraryCategory.DATA.matches(kind));
        }
        assertEquals(LibraryCategory.NOTEBOOK, LibraryCategory.forKind(DocumentKind.NOTEBOOK));
        assertEquals(LibraryCategory.SOURCE, LibraryCategory.forKind(DocumentKind.TEXT));
        assertEquals(LibraryCategory.VISUAL, LibraryCategory.forKind(DocumentKind.GIF));
        assertEquals(LibraryCategory.DOCUMENT, LibraryCategory.forKind(DocumentKind.PDF));
        assertEquals(LibraryCategory.OTHER, LibraryCategory.forKind(DocumentKind.BINARY));
    }

    @Test public void allNewKeysRoundTripAndLegacyKeysFallBackSafely() {
        for (LibraryCategory category : LibraryCategory.values()) {
            assertEquals(category, LibraryCategory.restore(category.savedKey()));
        }
        assertEquals(LibraryCategory.VISUAL, LibraryCategory.restore("HTML"));
        assertEquals(LibraryCategory.VISUAL, LibraryCategory.restore("SVG"));
        assertEquals(LibraryCategory.DATA, LibraryCategory.restore("XML"));
        assertEquals(LibraryCategory.ALL, LibraryCategory.restore("OTHER"));
        assertEquals(LibraryCategory.ALL, LibraryCategory.restore("unrecognized"));
        assertEquals(LibraryCategory.ALL, LibraryCategory.restore(null));
    }
}
