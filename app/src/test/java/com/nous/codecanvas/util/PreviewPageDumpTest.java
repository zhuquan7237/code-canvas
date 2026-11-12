package com.nous.codecanvas.util;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/** Dumps generated pages so the inline scripts can be syntax-checked outside the device. */
public class PreviewPageDumpTest {

    private void dump(String name, PreviewPage.Request r) throws Exception {
        String html = PreviewPage.build(r);
        File out = new File("build/page-dump");
        out.mkdirs();
        try (FileOutputStream fos = new FileOutputStream(new File(out, name))) {
            fos.write(html.getBytes(StandardCharsets.UTF_8));
        }
    }

    @Test
    public void dumpAll() throws Exception {
        PreviewPage.Request md = new PreviewPage.Request();
        md.kind = "MARKDOWN";
        md.content = "# hi\n\n| a | b |\n| - | - |\n| 1 | 2 |\n\n$$x^2$$\n";
        dump("markdown.html", md);

        PreviewPage.Request mmd = new PreviewPage.Request();
        mmd.kind = "MERMAID";
        mmd.content = "graph TD\n  A --> B\n";
        dump("mermaid.html", mmd);
    }
}
