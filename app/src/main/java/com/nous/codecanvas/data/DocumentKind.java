package com.nous.codecanvas.data;

/**
 * What a document actually is, decided from its extension, its MIME type and its first bytes.
 *
 * <p>This is deliberately separate from {@link com.nous.codecanvas.util.RenderKind}, which only
 * ever had four answers because the app only ever rendered HTML/SVG/XML/plain text. A phone file
 * viewer has to distinguish a PDF from a JPEG from a CSV before it can decide whether the file gets
 * an editor at all — binary formats must never be pushed through a text decoder.</p>
 */
public enum DocumentKind {

    HTML(DefaultView.PREVIEW, true),
    SVG(DefaultView.PREVIEW, true),
    MARKDOWN(DefaultView.PREVIEW, true),
    MERMAID(DefaultView.PREVIEW, true),
    JSON(DefaultView.CODE, true),
    JSONL(DefaultView.CODE, true),
    XML(DefaultView.STRUCTURE, true),
    YAML(DefaultView.CODE, true),
    CSV(DefaultView.TABLE, true),
    CODE(DefaultView.CODE, true),
    TEXT(DefaultView.CODE, true),
    IMAGE(DefaultView.IMAGE, false),
    GIF(DefaultView.IMAGE, false),
    PDF(DefaultView.PDF, false),
    BINARY(DefaultView.NONE, false),
    UNKNOWN(DefaultView.CODE, true);

    /** Which surface opens first for this kind. */
    public enum DefaultView { PREVIEW, CODE, STRUCTURE, TABLE, IMAGE, PDF, NONE }

    private final DefaultView defaultView;
    private final boolean editable;

    DocumentKind(DefaultView defaultView, boolean editable) {
        this.defaultView = defaultView;
        this.editable = editable;
    }

    public DefaultView defaultView() {
        return defaultView;
    }

    /** False for images and PDFs: they are previewed, never decoded into the text editor. */
    public boolean editable() {
        return editable;
    }

    public boolean hasSourceTab() {
        return editable;
    }

    /** Short label for the file-type chip on the home list and the preview badge. */
    public String label() {
        switch (this) {
            case HTML: return "HTML";
            case SVG: return "SVG";
            case MARKDOWN: return "Markdown";
            case MERMAID: return "Mermaid";
            case JSON: return "JSON";
            case JSONL: return "JSONL";
            case XML: return "XML";
            case YAML: return "YAML";
            case CSV: return "CSV";
            case CODE: return "代码";
            case TEXT: return "文本";
            case IMAGE: return "图片";
            case GIF: return "动图";
            case PDF: return "PDF";
            case BINARY: return "二进制";
            default: return "文件";
        }
    }

    public static DocumentKind fromName(String name) {
        if (name == null) return null;
        for (DocumentKind k : values()) {
            if (k.name().equals(name)) return k;
        }
        return null;
    }
}
