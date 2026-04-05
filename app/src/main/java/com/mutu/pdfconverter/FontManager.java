package com.mutu.pdfconverter;

import android.content.Context;
import android.net.Uri;
import java.io.*;
import java.util.HashMap;
import java.util.Map;

/**
 * FontManager
 * ───────────
 * Manages two font sources:
 *  1. Built-in fonts  → assets/fonts/*.ttf
 *  2. Custom fonts    → app's internal filesDir/fonts/
 *
 * Returns the correct CSS src() string for @font-face.
 */
public class FontManager {

    private final Context ctx;

    // Map: display name → asset file name (without path)
    private static final Map<String, String> BUILT_IN = new HashMap<>();
    static {
        // Bangla
        BUILT_IN.put("SolaimanLipi",    "SolaimanLipi.ttf");
        BUILT_IN.put("Kalpurush",       "Kalpurush.ttf");
        BUILT_IN.put("Hind Siliguri",   "HindSiliguri-Regular.ttf");

        // Arabic
        BUILT_IN.put("Amiri",           "Amiri-Regular.ttf");
        BUILT_IN.put("Noto Naskh Arabic","NotoNaskhArabic-Regular.ttf");

        // English (system fonts — no asset needed)
        BUILT_IN.put("Georgia",         "");
        BUILT_IN.put("Times New Roman", "");
        BUILT_IN.put("Courier New",     "");
    }

    // Map: custom font display name → absolute path in internal storage
    private final Map<String, String> customFonts = new HashMap<>();

    public FontManager(Context ctx) {
        this.ctx = ctx;
        ensureFontDir();
    }

    // ── Resolve CSS src string ─────────────────────────────────
    /**
     * Returns CSS src() value for @font-face.
     * Priority: custom upload → built-in asset → empty (use system font)
     */
    public String getFontSrc(String displayName, String script) {
        // 1. Check custom-uploaded fonts
        if (customFonts.containsKey(displayName)) {
            String path = customFonts.get(displayName);
            return "url('file://" + path + "') format('truetype')";
        }

        // 2. Check built-in assets
        String assetFile = BUILT_IN.get(displayName);
        if (assetFile != null && !assetFile.isEmpty()) {
            return "url('file:///android_asset/fonts/" + assetFile + "') format('truetype')";
        }

        // 3. System font (Georgia, Times etc.) — no src needed, CSS will use font-family name
        return "local('" + displayName + "')";
    }

    // ── Save uploaded font to internal storage ─────────────────
    /**
     * Copies a user-picked font URI into app's internal fonts/ dir.
     * Returns the display name (filename without extension), or null on error.
     */
    public String saveFont(Uri uri) {
        try {
            InputStream is = ctx.getContentResolver().openInputStream(uri);
            if (is == null) return null;

            String fileName = getFileName(uri);
            if (fileName == null) fileName = "custom_" + System.currentTimeMillis() + ".ttf";

            File fontDir  = getFontDir();
            File destFile = new File(fontDir, fileName);

            FileOutputStream fos = new FileOutputStream(destFile);
            byte[] buf = new byte[4096];
            int   len;
            while ((len = is.read(buf)) != -1) fos.write(buf, 0, len);
            fos.close();
            is.close();

            // Strip extension for display name
            String displayName = fileName.replaceFirst("\\.[^.]+$", "");
            customFonts.put(displayName, destFile.getAbsolutePath());
            return displayName;

        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    // ── Helpers ────────────────────────────────────────────────
    private File getFontDir() {
        File dir = new File(ctx.getFilesDir(), "fonts");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    private void ensureFontDir() {
        getFontDir();
    }

    /** Try to get the real file name from a Uri. */
    private String getFileName(Uri uri) {
        String path = uri.getLastPathSegment();
        if (path != null) {
            int slash = path.lastIndexOf('/');
            if (slash >= 0) path = path.substring(slash + 1);
        }
        return path;
    }

    /** Returns map of all custom font names for spinner population. */
    public Map<String, String> getCustomFonts() {
        return customFonts;
    }
}
