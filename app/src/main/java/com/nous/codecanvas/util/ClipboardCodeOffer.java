package com.nous.codecanvas.util;

import java.util.List;

/**
 * The one decision behind the clipboard banner: is this clipboard content worth offering?
 *
 * <p>It lives here, as pure logic, for two reasons. A feature that reads the clipboard on launch
 * has to be provably shy — the app must never nag about a copied phone number, a URL or a chat
 * message — and that shyness has to be testable without a device. {@link AiCodeExtractor} and
 * {@link RenderKindDetector} answer different questions (where is the code / what does it render
 * as), so neither alone is the answer to "would the user want this imported".</p>
 */
public final class ClipboardCodeOffer {

    /** Below this the clip cannot be a document; above it we refuse for the same 2MB reason as import. */
    public static final int MIN_LENGTH = 16;
    public static final int MAX_LENGTH = 2 * 1024 * 1024;

    private ClipboardCodeOffer() {}

    /**
     * True only when the clip is plausibly renderable markup: it detects as HTML/SVG/XML, or it
     * contains a recognisable code fence, or it has a markup region that markupRegion can cut out.
     * Prose, URLs, numbers and ordinary chat text all answer false.
     */
    public static boolean looksLikeCode(String text) {
        if (text == null) return false;
        int length = text.length();
        if (length < MIN_LENGTH || length > MAX_LENGTH) return false;

        // 1. Does it read as renderable markup on its own?
        if (RenderKindDetector.detect("", text) != RenderKind.RAW) return true;

        // 2. Was it handed over inside a fenced block? AiCodeExtractor.extract() cannot answer
        //    that — it wraps anything at all in one block — so ask the fence directly.
        if (AiCodeExtractor.hasFencedBlock(text)) return true;

        // 3. Is there a markup region that markupRegion can cut out of the surrounding prose?
        return AiCodeExtractor.markupRegion(text) != null;
    }
}
