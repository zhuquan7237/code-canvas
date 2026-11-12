package com.nous.codecanvas.data;

import com.nous.codecanvas.model.CanvasDocument;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Decides a file's {@link DocumentKind} from three independent signals.
 *
 * <p>Extension alone is not enough: chat apps hand files over as {@code octet-stream} with the real
 * name, file managers sometimes hand over a name without an extension, and a {@code .txt} that
 * starts with {@code %PDF-} is a PDF. So the order is: magic bytes (they cannot lie), then
 * extension (the author's stated intent), then MIME, then a printable-ratio guess.</p>
 */
public final class FileKindDetector {

    private static final Set<String> CODE_EXTENSIONS = new HashSet<>(Arrays.asList(
            "py", "pyw", "js", "mjs", "cjs", "jsx", "ts", "tsx", "java", "kt", "kts", "c", "h", "cc",
            "cpp", "cxx", "hpp", "cs", "go", "rs", "rb", "php", "swift", "m", "mm", "scala", "lua",
            "pl", "r", "dart", "sh", "bash", "zsh", "fish", "ps1", "bat", "cmd", "css", "scss",
            "sass", "less", "sql", "gradle", "groovy", "toml", "ini", "cfg", "conf", "properties",
            "dockerfile", "makefile", "mk", "vue", "svelte", "asm", "s", "v", "vhd", "vhdl", "tex",
            "proto", "graphql", "gql", "env", "gitignore", "editorconfig"));

    private static final Set<String> IMAGE_EXTENSIONS = new HashSet<>(Arrays.asList(
            "png", "jpg", "jpeg", "webp", "bmp", "heic", "heif", "avif", "ico"));

    private FileKindDetector() {
    }

    /**
     * @param name     display name, may be null or extension-less
     * @param mime     MIME type as reported by the provider, may be null or {@code *}{@code /*}
     * @param head     first bytes of the file (a few KB is plenty), may be null
     */
    public static DocumentKind detect(String name, String mime, byte[] head) {
        DocumentKind byMagic = fromMagic(head);
        if (byMagic != null) return byMagic;

        String ext = extensionOf(name);
        DocumentKind byExt = fromExtension(ext);
        if (byExt != null) return byExt;

        DocumentKind byMime = fromMime(mime);
        if (byMime != null) return byMime;

        if (head != null && head.length > 0) {
            return looksBinary(head) ? DocumentKind.BINARY : DocumentKind.TEXT;
        }
        return DocumentKind.UNKNOWN;
    }

    public static String extensionOf(String name) {
        if (name == null) return "";
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        String base = slash >= 0 ? name.substring(slash + 1) : name;
        int dot = base.lastIndexOf('.');
        if (dot < 0 || dot == base.length() - 1) {
            // Files like "Makefile" / "Dockerfile" carry their type in the name itself.
            String lower = base.toLowerCase(Locale.ROOT);
            if (lower.equals("makefile") || lower.equals("dockerfile")) return lower;
            return "";
        }
        return base.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** Magic numbers win over every other signal: a file's first bytes cannot be renamed. */
    public static DocumentKind fromMagic(byte[] head) {
        if (head == null || head.length < 4) return null;
        if (startsWith(head, new byte[]{'%', 'P', 'D', 'F'})) return DocumentKind.PDF;
        if (startsWith(head, new byte[]{(byte) 0x89, 'P', 'N', 'G'})) return DocumentKind.IMAGE;
        if (startsWith(head, new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF})) return DocumentKind.IMAGE;
        if (startsWith(head, new byte[]{'G', 'I', 'F', '8'})) return DocumentKind.GIF;
        if (head.length >= 12
                && startsWith(head, new byte[]{'R', 'I', 'F', 'F'})
                && head[8] == 'W' && head[9] == 'E' && head[10] == 'B' && head[11] == 'P') {
            return DocumentKind.IMAGE;
        }
        if (startsWith(head, new byte[]{'B', 'M'})) return DocumentKind.IMAGE;
        return null;
    }

    public static DocumentKind fromExtension(String ext) {
        if (ext == null || ext.isEmpty()) return null;
        switch (ext) {
            case "html": case "htm": case "xhtml": return DocumentKind.HTML;
            case "svg": return DocumentKind.SVG;
            case "md": case "markdown": case "mdx": return DocumentKind.MARKDOWN;
            case "mmd": case "mermaid": return DocumentKind.MERMAID;
            case "json": return DocumentKind.JSON;
            case "jsonl": case "ndjson": return DocumentKind.JSONL;
            case "xml": case "xsd": case "xsl": case "plist": return DocumentKind.XML;
            case "yaml": case "yml": return DocumentKind.YAML;
            case "csv": case "tsv": return DocumentKind.CSV;
            case "txt": case "text": case "log": case "rst": return DocumentKind.TEXT;
            case "gif": return DocumentKind.GIF;
            case "pdf": return DocumentKind.PDF;
            case "png": case "jpg": case "jpeg": case "webp": case "bmp":
            case "heic": case "heif": case "avif": case "ico": return DocumentKind.IMAGE;
            default:
                return CODE_EXTENSIONS.contains(ext) ? DocumentKind.CODE : null;
        }
    }

    public static DocumentKind fromMime(String mime) {
        if (mime == null) return null;
        String m = mime.toLowerCase(Locale.ROOT).trim();
        if (m.isEmpty() || m.equals("*/*") || m.equals("application/octet-stream")) return null;
        if (m.equals("application/pdf")) return DocumentKind.PDF;
        if (m.equals("image/gif")) return DocumentKind.GIF;
        if (m.startsWith("image/")) return DocumentKind.IMAGE;
        if (m.equals("image/svg+xml")) return DocumentKind.SVG;
        if (m.equals("text/markdown")) return DocumentKind.MARKDOWN;
        if (m.equals("text/csv")) return DocumentKind.CSV;
        if (m.equals("application/json") || m.endsWith("+json")) return DocumentKind.JSON;
        if (m.equals("application/xml") || m.equals("text/xml")) return DocumentKind.XML;
        if (m.equals("text/yaml") || m.equals("application/x-yaml")) return DocumentKind.YAML;
        if (m.equals("text/html") || m.equals("application/xhtml+xml")) return DocumentKind.HTML;
        if (m.equals("text/css") || m.equals("text/javascript") || m.equals("application/javascript")) {
            return DocumentKind.CODE;
        }
        if (m.startsWith("text/")) return DocumentKind.TEXT;
        return null;
    }

    /**
     * A NUL byte in the first few KB is the classic binary tell; so is a high share of control
     * characters. Text in any of the encodings this app supports (UTF-8/16, GB18030, Latin-1) never
     * contains a raw NUL.
     */
    public static boolean looksBinary(byte[] head) {
        if (head == null || head.length == 0) return false;
        int control = 0;
        int sample = Math.min(head.length, 4096);
        for (int i = 0; i < sample; i++) {
            int b = head[i] & 0xFF;
            if (b == 0) return true;
            if (b < 0x09 || (b > 0x0D && b < 0x20)) control++;
        }
        return control * 100 / sample > 5;
    }

    /**
     * Re-decide a text document's kind from its current title and a prefix of its text.
     *
     * <p>Stored {@code kind} used to win forever, so renaming {@code notes.txt} to {@code art.svg}
     * left the file opening as plain text. Blobs are left alone: an image's bytes are the type,
     * and a renamed {@code .png} must not become a text file just because the editor has no text.</p>
     */
    public static void applyTo(CanvasDocument doc) {
        if (doc == null || doc.isBlob()) return;
        String content = doc.getContent() == null ? "" : doc.getContent();
        int n = Math.min(content.length(), 4096);
        byte[] head = content.substring(0, n).getBytes(StandardCharsets.UTF_8);
        DocumentKind detected = detect(doc.getTitle(), doc.getMimeType(), head);
        if (detected != null) {
            doc.setKindName(detected.name());
        }
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) return false;
        }
        return true;
    }
}
