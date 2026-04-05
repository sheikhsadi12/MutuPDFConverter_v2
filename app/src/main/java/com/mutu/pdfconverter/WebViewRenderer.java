package com.mutu.pdfconverter;

/**
 * WebViewRenderer
 * ───────────────
 * Injects @font-face CSS and language-detection JS into raw HTML.
 * Handles 3 scripts: Bangla (U+0980–09FF), Arabic (U+0600–06FF), English (default).
 */
public class WebViewRenderer {

    /**
     * Wraps user HTML with:
     *  1. @font-face declarations for all 3 fonts
     *  2. Base body style
     *  3. JS language-detector that wraps chars in <span class="bn|ar|en">
     */
    public static String buildHtml(
            String rawHtml,
            String banglaFont,
            String arabicFont,
            String englishFont,
            FontManager fm) {

        // Resolve font sources (asset path OR internal storage path)
        String banglaFontSrc  = fm.getFontSrc(banglaFont,  "bangla");
        String arabicFontSrc  = fm.getFontSrc(arabicFont,  "arabic");
        String englishFontSrc = fm.getFontSrc(englishFont, "english");

        String css = buildCSS(banglaFont, arabicFont, englishFont,
                banglaFontSrc, arabicFontSrc, englishFontSrc);
        String js  = buildJS();

        // If rawHtml already has <html>, inject into <head>/<body>
        if (rawHtml.toLowerCase().contains("<html")) {
            // Inject CSS before </head>
            String injected = rawHtml.replaceFirst(
                    "(?i)</head>",
                    "<style>" + css + "</style></head>");
            // Inject JS before </body>
            injected = injected.replaceFirst(
                    "(?i)</body>",
                    "<script>" + js + "</script></body>");
            return injected;
        }

        // Otherwise wrap completely
        return "<!DOCTYPE html><html><head>"
                + "<meta charset='UTF-8'>"
                + "<meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<style>" + css + "</style>"
                + "</head><body>"
                + rawHtml
                + "<script>" + js + "</script>"
                + "</body></html>";
    }

    // ── CSS builder ────────────────────────────────────────────
    private static String buildCSS(
            String bName, String aName, String eName,
            String bSrc,  String aSrc,  String eSrc) {

        return ""
            // Bangla font-face
            + "@font-face {"
            + "  font-family: 'BanglaFont';"
            + "  src: " + bSrc + ";"
            + "}"

            // Arabic font-face
            + "@font-face {"
            + "  font-family: 'ArabicFont';"
            + "  src: " + aSrc + ";"
            + "}"

            // English font-face (only if custom; else use system)
            + (eSrc.isEmpty() ? "" :
              "@font-face {"
            + "  font-family: 'EnglishFont';"
            + "  src: " + eSrc + ";"
            + "}")

            // Base body
            + "body {"
            + "  font-family: '" + eName + "', 'EnglishFont', serif;"
            + "  font-size: 16px;"
            + "  line-height: 1.8;"
            + "  color: #1a1a1a;"
            + "  padding: 12px 16px;"
            + "  background: #ffffff;"
            + "}"

            // Bangla spans
            + ".bn {"
            + "  font-family: 'BanglaFont', serif;"
            + "}"

            // Arabic/Urdu spans  
            + ".ar {"
            + "  font-family: 'ArabicFont', serif;"
            + "  direction: rtl;"
            + "  unicode-bidi: embed;"
            + "}"

            // Print overrides
            + "@media print {"
            + "  body { margin: 0; }"
            + "  .no-print { display: none; }"
            + "}";
    }

    // ── JS language detector ───────────────────────────────────
    private static String buildJS() {
        return ""
            + "(function() {"

            // Walk all text nodes in the DOM
            + "  function walkTextNodes(node) {"
            + "    if (node.nodeType === 3) {" // TEXT_NODE
            + "      wrapText(node);"
            + "    } else {"
            + "      var children = Array.from(node.childNodes);"
            + "      children.forEach(walkTextNodes);"
            + "    }"
            + "  }"

            // Wrap each char with the right span class
            + "  function wrapText(textNode) {"
            + "    var text = textNode.nodeValue;"
            + "    if (!text.trim()) return;" // skip whitespace-only nodes
            + "    var result = '';"
            + "    var i = 0;"
            + "    while (i < text.length) {"
            + "      var code = text.codePointAt(i);"
            + "      var char = String.fromCodePoint(code);"
            + "      var advance = char.length;" // handles surrogates

            // Bangla Unicode block: U+0980–U+09FF
            + "      if (code >= 0x0980 && code <= 0x09FF) {"
            + "        result += \"<span class='bn'>\" + char + \"</span>\";"

            // Arabic Unicode block: U+0600–U+06FF + extended
            + "      } else if (code >= 0x0600 && code <= 0x06FF"
            + "                 || code >= 0xFB50 && code <= 0xFDFF"
            + "                 || code >= 0xFE70 && code <= 0xFEFF) {"
            + "        result += \"<span class='ar'>\" + char + \"</span>\";"

            // Everything else = English/default
            + "      } else {"
            + "        result += char;"
            + "      }"
            + "      i += advance;"
            + "    }"

            // Replace text node with wrapped HTML
            + "    var span = document.createElement('span');"
            + "    span.innerHTML = result;"
            + "    textNode.parentNode.replaceChild(span, textNode);"
            + "  }"

            // Run after DOM is ready
            + "  document.addEventListener('DOMContentLoaded', function() {"
            + "    walkTextNodes(document.body);"
            + "  });"

            // Also run immediately in case DOMContentLoaded already fired
            + "  if (document.readyState !== 'loading') {"
            + "    walkTextNodes(document.body);"
            + "  }"

            + "})();";
    }
}
