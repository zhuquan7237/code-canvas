package com.nous.codecanvas.util;

public class SvgWrapper {

    public static String wrapSvgInResponsiveHtml(String svgContent, boolean darkTheme) {
        String bgColor = darkTheme ? "#121417" : "#F8FAFC";
        String gridColor = darkTheme ? "rgba(255,255,255,0.05)" : "rgba(0,0,0,0.04)";

        return "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head>\n" +
                "  <meta charset=\"utf-8\">\n" +
                "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, maximum-scale=5.0, user-scalable=yes\">\n" +
                "  <style>\n" +
                "    * { box-sizing: border-box; margin: 0; padding: 0; }\n" +
                "    html, body {\n" +
                "      width: 100%;\n" +
                "      min-height: 100%;\n" +
                "      background-color: " + bgColor + ";\n" +
                "      background-image: linear-gradient(" + gridColor + " 1px, transparent 1px), linear-gradient(90deg, " + gridColor + " 1px, transparent 1px);\n" +
                "      background-size: 20px 20px;\n" +
                "      display: flex;\n" +
                "      align-items: center;\n" +
                "      justify-content: center;\n" +
                "      padding: 16px;\n" +
                "    }\n" +
                "    .svg-wrapper {\n" +
                "      width: 100%;\n" +
                "      max-width: 100%;\n" +
                "      display: flex;\n" +
                "      align-items: center;\n" +
                "      justify-content: center;\n" +
                "      overflow: auto;\n" +
                "    }\n" +
                "    .svg-wrapper svg {\n" +
                "      max-width: 100%;\n" +
                "      height: auto;\n" +
                "      display: block;\n" +
                "    }\n" +
                "  </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "  <div class=\"svg-wrapper\">\n" +
                svgContent + "\n" +
                "  </div>\n" +
                "</body>\n" +
                "</html>";
    }
}
