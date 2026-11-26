package com.nous.codecanvas.util;

import java.util.Locale;

/** Conservative names for text explicitly imported from an AI reply or the clipboard. */
public final class PasteFileNaming {
    private PasteFileNaming() {}

    public static String extension(String code, String language) {
        String lang = language == null ? "" : language.trim().toLowerCase(Locale.ROOT);
        switch (lang) {
            case "text": case "plain": case "plaintext": case "text/plain": case "txt": return ".txt";
            case "html": case "html5": case "htm": return ".html";
            case "svg": case "xml": case "json": case "css": case "csv": case "tsv":
            case "jsx": case "tsx": case "sql": case "toml": case "ini": case "java":
            case "swift": case "dart": case "go": case "lua": case "php": case "tex":
            case "c": case "h": case "r": case "diff": case "patch": case "vue":
            case "svelte": case "dockerfile": return "." + lang;
            case "xhtml": return ".xhtml";
            case "jsonl": case "ndjson": return ".jsonl";
            case "javascript": case "js": return ".js";
            case "typescript": case "ts": return ".ts";
            case "py": case "python": case "python3": case "py3": return ".py";
            case "md": case "markdown": return ".md";
            case "yaml": case "yml": return ".yaml";
            case "mermaid": case "mmd": return ".mmd";
            case "sh": case "shell": case "shellscript": case "bash": case "zsh": return ".sh";
            case "ps": case "ps1": case "pwsh": case "powershell": return ".ps1";
            case "kotlin": case "kt": return ".kt";
            case "kts": return ".kts";
            case "c++": case "cpp": case "cxx": return ".cpp";
            case "c#": case "cs": case "csharp": return ".cs";
            case "rust": case "rs": return ".rs";
            case "ruby": case "rb": return ".rb";
            case "golang": return ".go";
            default:
                RenderKind kind = RenderKindDetector.detect("", code);
                if (kind == RenderKind.SVG) return ".svg";
                if (kind == RenderKind.HTML) return ".html";
                if (kind == RenderKind.XML) return ".xml";
                return ".txt";
        }
    }
}
