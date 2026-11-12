package com.nous.codecanvas.util;

import java.io.StringReader;
import java.util.Locale;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.xml.sax.InputSource;

public class XmlValidator {

    public static class ValidationResult {
        private final boolean valid;
        private final String errorMessage;

        public ValidationResult(boolean valid, String errorMessage) {
            this.valid = valid;
            this.errorMessage = errorMessage;
        }

        public boolean isValid() {
            return valid;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }

    public static ValidationResult validateSecurely(String xmlContent) {
        if (xmlContent == null || xmlContent.trim().isEmpty()) {
            return new ValidationResult(false, "XML content is empty");
        }

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // Disallow XXE features securely on standard Android/Java XML parsers.
            // If the parser cannot refuse DOCTYPE, fail closed instead of parsing anyway.
            boolean doctypeBlocked = false;
            try {
                factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            } catch (Exception ignored) {}
            try {
                factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            } catch (Exception ignored) {}
            try {
                factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            } catch (Exception ignored) {}
            try {
                factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
                doctypeBlocked = true;
            } catch (Exception ignored) {}
            try {
                factory.setXIncludeAware(false);
            } catch (Exception ignored) {}
            try {
                factory.setExpandEntityReferences(false);
            } catch (Exception ignored) {}
            if (!doctypeBlocked) {
                // Some Android parsers cannot toggle that feature. A document that declares a
                // DOCTYPE is refused; one that does not is still parsed with the other XXE
                // switches off.
                String head = xmlContent.length() > 8192 ? xmlContent.substring(0, 8192) : xmlContent;
                if (head.toUpperCase(Locale.ROOT).contains("<!DOCTYPE")) {
                    return new ValidationResult(false, "安全拦截：解析器无法关闭 DOCTYPE，已拒绝带文档类型声明的 XML");
                }
            }
            factory.setNamespaceAware(true);

            String scanned = xmlContent.length() > 8192 ? xmlContent.substring(0, 8192) : xmlContent;
            String upper = scanned.toUpperCase(Locale.ROOT);
            if (upper.contains("<!DOCTYPE")
                    && (upper.contains("SYSTEM") || upper.contains("PUBLIC") || upper.contains("ENTITY"))) {
                return new ValidationResult(false, "安全拦截：禁止外部 DOCTYPE / ENTITY 实体声明 (防 XXE)");
            }

            DocumentBuilder builder = factory.newDocumentBuilder();
            builder.parse(new InputSource(new StringReader(xmlContent)));
            return new ValidationResult(true, null);
        } catch (Exception e) {
            String msg = e.getMessage();
            if (msg == null || msg.isEmpty()) {
                msg = e.getClass().getSimpleName();
            }
            return new ValidationResult(false, msg);
        }
    }
}
