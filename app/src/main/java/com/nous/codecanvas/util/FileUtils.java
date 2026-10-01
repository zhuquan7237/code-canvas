package com.nous.codecanvas.util;

import java.io.File;

public class FileUtils {
    public static String sanitizeFileName(String input) {
        if (input == null) {
            return "untitled.txt";
        }
        String trimmed = input.trim();
        if (trimmed.isEmpty()) {
            return "untitled.txt";
        }
        // Normalize path separators
        trimmed = trimmed.replace('\\', '/');
        // Extract base name after last slash
        int lastSlash = trimmed.lastIndexOf('/');
        if (lastSlash >= 0) {
            trimmed = trimmed.substring(lastSlash + 1);
        }
        // Remove traversal dots if any at beginning
        trimmed = trimmed.replaceAll("^\\.+", "");
        // Remove illegal characters for file systems: < > : " / \ | ? * and control chars
        trimmed = trimmed.replaceAll("[<>:\"/\\\\|?*\\x00-\\x1F]", "_");
        trimmed = trimmed.trim();
        if (trimmed.isEmpty()) {
            return "untitled.txt";
        }
        return trimmed;
    }

    public static String getExtension(String fileName) {
        if (fileName == null) return "";
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex >= 0 && dotIndex < fileName.length() - 1) {
            return fileName.substring(dotIndex + 1).toLowerCase();
        }
        return "";
    }
}
