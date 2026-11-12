package com.nous.codecanvas.ui;

import android.content.res.ColorStateList;
import android.widget.TextView;

import com.nous.codecanvas.R;
import com.nous.codecanvas.data.DocumentKind;

/**
 * One colour pair per file type, using inks that already clear the contrast tests
 * on their own washed backgrounds. A CSV and an HTML file should not wear the same gray chip.
 */
final class KindStyle {

    private KindStyle() {
    }

    static int fillRes(DocumentKind kind) {
        if (kind == null) kind = DocumentKind.UNKNOWN;
        switch (kind) {
            case HTML:
            case CODE:
                return R.color.canvas_selection;
            case SVG:
            case MERMAID:
            case IMAGE:
            case GIF:
                return R.color.canvas_preview_surface;
            case MARKDOWN:
            case TEXT:
                return R.color.canvas_success_surface;
            case JSON:
            case JSONL:
            case XML:
            case YAML:
            case CSV:
                return R.color.canvas_warning_surface;
            case PDF:
                return R.color.canvas_danger_surface;
            default:
                return R.color.canvas_surface_subtle;
        }
    }

    static int inkRes(DocumentKind kind) {
        if (kind == null) kind = DocumentKind.UNKNOWN;
        switch (kind) {
            case HTML:
            case CODE:
                return R.color.canvas_primary;
            case SVG:
            case MERMAID:
            case IMAGE:
            case GIF:
                return R.color.canvas_preview;
            case MARKDOWN:
            case TEXT:
                return R.color.canvas_success;
            case JSON:
            case JSONL:
            case XML:
            case YAML:
            case CSV:
                return R.color.canvas_warning;
            case PDF:
                return R.color.canvas_danger;
            default:
                return R.color.canvas_ink_secondary;
        }
    }

    /** Short mark drawn on the placeholder tile. */
    static String mark(DocumentKind kind) {
        if (kind == null) return "文件";
        switch (kind) {
            case HTML: return "HTML";
            case SVG: return "SVG";
            case MARKDOWN: return "MD";
            case MERMAID: return "图";
            case JSON: return "JSON";
            case JSONL: return "JSONL";
            case XML: return "XML";
            case YAML: return "YAML";
            case CSV: return "CSV";
            case CODE: return "代码";
            case TEXT: return "文本";
            case IMAGE: return "图片";
            case GIF: return "GIF";
            case PDF: return "PDF";
            default: return "文件";
        }
    }

    static void applyChip(TextView chip, DocumentKind kind) {
        if (chip == null) return;
        chip.setBackgroundResource(R.drawable.bg_chip);
        chip.setBackgroundTintList(ColorStateList.valueOf(
                chip.getResources().getColor(fillRes(kind))));
        chip.setTextColor(chip.getResources().getColor(inkRes(kind)));
    }
}
