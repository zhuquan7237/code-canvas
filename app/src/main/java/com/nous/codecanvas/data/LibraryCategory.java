package com.nous.codecanvas.data;

/** Groups the stored, effective document kind, not a second detection by suffix. */
public enum LibraryCategory {
    ALL, DATA, NOTEBOOK, VISUAL, DOCUMENT, SOURCE, OTHER;

    public static LibraryCategory forKind(DocumentKind kind) {
        if (kind == null) return OTHER;
        switch (kind) {
            case JSON: case JSONL: case YAML: case CSV: case XML: return DATA;
            case NOTEBOOK: return NOTEBOOK;
            case HTML: case SVG: case MERMAID: case IMAGE: case GIF: return VISUAL;
            case MARKDOWN: case PDF: return DOCUMENT;
            case CODE: case TEXT: return SOURCE;
            default: return OTHER;
        }
    }

    public boolean matches(DocumentKind kind) {
        return this == ALL || this == forKind(kind);
    }

    public static LibraryCategory restore(String key) {
        if (key == null) return ALL;
        switch (key) {
            case "HTML": case "SVG": return VISUAL;
            case "XML": return DATA;
            default:
                // The old Other key was not equivalent to the new OTHER group.
                if ("group.OTHER".equals(key)) return OTHER;
                if ("OTHER".equals(key)) return ALL;
                try { return valueOf(key); }
                catch (IllegalArgumentException ignored) { return ALL; }
        }
    }

    public String savedKey() {
        return this == OTHER ? "group.OTHER" : name();
    }
}
