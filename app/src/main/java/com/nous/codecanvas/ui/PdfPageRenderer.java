package com.nous.codecanvas.ui;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.IOException;

/**
 * Renders one page of a PDF with the platform renderer. No PDF.js and no network:
 * the bytes already stored for the document are enough.
 */
final class PdfPageRenderer {

    static final class Page {
        final Bitmap bitmap;
        final int index;
        final int count;

        Page(Bitmap bitmap, int index, int count) {
            this.bitmap = bitmap;
            this.index = index;
            this.count = count;
        }
    }

    private PdfPageRenderer() {
    }

    static Page render(File file, int requestedIndex, int targetWidth) throws IOException {
        if (file == null || !file.isFile()) {
            throw new IOException("找不到 PDF 原文件");
        }
        int width = Math.max(1, targetWidth);
        try (ParcelFileDescriptor pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
             PdfRenderer renderer = new PdfRenderer(pfd)) {
            int count = renderer.getPageCount();
            if (count <= 0) {
                throw new IOException("这份 PDF 没有页面");
            }
            int index = Math.max(0, Math.min(requestedIndex, count - 1));
            try (PdfRenderer.Page page = renderer.openPage(index)) {
                float scale = width / (float) Math.max(1, page.getWidth());
                int height = Math.max(1, Math.round(page.getHeight() * scale));
                Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                bitmap.eraseColor(Color.WHITE);
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                return new Page(bitmap, index, count);
            }
        }
    }
}
