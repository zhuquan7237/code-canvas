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

    static void applyChip(TextView chip, DocumentKind kind) {
        if (chip == null) return;
        int fill;
        int ink;
        if (kind == null) kind = DocumentKind.UNKNOWN;
        switch (kind) {
            case HTML:
            case CODE:
                fill = R.color.canvas_selection;
                ink = R.color.canvas_primary;
                break;
            case SVG:
            case MERMAID:
                fill = R.color.canvas_preview_surface;
                ink = R.color.canvas_preview;
                break;
            case MARKDOWN:
            case TEXT:
                fill = R.color.canvas_success_surface;
                ink = R.color.canvas_success;
                break;
            case JSON:
            case JSONL:
            case XML:
            case YAML:
            case CSV:
                fill = R.color.canvas_warning_surface;
                ink = R.color.canvas_warning;
                break;
            case IMAGE:
            case GIF:
                fill = R.color.canvas_preview_surface;
                ink = R.color.canvas_preview;
                break;
            case PDF:
                fill = R.color.canvas_danger_surface;
                ink = R.color.canvas_danger;
                break;
            default:
                fill = R.color.canvas_surface_subtle;
                ink = R.color.canvas_ink_secondary;
                break;
        }
        chip.setBackgroundResource(R.drawable.bg_chip);
        chip.setBackgroundTintList(ColorStateList.valueOf(chip.getResources().getColor(fill)));
        chip.setTextColor(chip.getResources().getColor(ink));
    }
}
