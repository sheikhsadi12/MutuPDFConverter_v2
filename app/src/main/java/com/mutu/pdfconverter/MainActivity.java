package com.mutu.pdfconverter;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.util.Base64;
import android.view.View;
import android.webkit.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import java.io.*;
import java.nio.charset.StandardCharsets;

public class MainActivity extends AppCompatActivity {

    private static final int REQ_PICK_FILE = 101;
    private static final int REQ_PICK_FONT = 102;

    private WebView     webPreview;
    private Spinner     spBangla, spArabic, spEnglish;
    private TextView    tvFileName;
    private Button      btnUpload, btnUploadFont, btnConvert;
    private ProgressBar progressBar;

    private String currentHtmlContent = "";
    private String banglaFont  = "SolaimanLipi";
    private String arabicFont  = "Amiri";
    private String englishFont = "Georgia";

    private final String[] banglaFonts  = {"SolaimanLipi","Kalpurush","Hind Siliguri"};
    private final String[] arabicFonts  = {"Amiri","Noto Naskh Arabic"};
    private final String[] englishFonts = {"Georgia","Times New Roman","Courier New"};

    private FontManager fontManager;
    private boolean mammothPageReady = false;
    private String  pendingBase64    = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        fontManager = new FontManager(this);
        bindViews();
        setupSpinners();
        setupWebView();
        setupListeners();
    }

    private void bindViews() {
        webPreview    = findViewById(R.id.webPreview);
        spBangla      = findViewById(R.id.spBangla);
        spArabic      = findViewById(R.id.spArabic);
        spEnglish     = findViewById(R.id.spEnglish);
        tvFileName    = findViewById(R.id.tvFileName);
        btnUpload     = findViewById(R.id.btnUpload);
        btnUploadFont = findViewById(R.id.btnUploadFont);
        btnConvert    = findViewById(R.id.btnConvert);
        progressBar   = findViewById(R.id.progressBar);
    }

    private void setupSpinners() {
        setSpinner(spBangla,  banglaFonts,  v -> { banglaFont  = banglaFonts[v];  refreshPreview(); });
        setSpinner(spArabic,  arabicFonts,  v -> { arabicFont  = arabicFonts[v];  refreshPreview(); });
        setSpinner(spEnglish, englishFonts, v -> { englishFont = englishFonts[v]; refreshPreview(); });
    }

    private void setSpinner(Spinner sp, String[] items, OnItemSelectedCallback cb) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, items);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sp.setAdapter(adapter);
        sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) { cb.onSelected(pos); }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });
    }

    interface OnItemSelectedCallback { void onSelected(int pos); }

    private void setupWebView() {
        WebSettings ws = webPreview.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setAllowFileAccess(true);
        ws.setAllowFileAccessFromFileURLs(true);
        ws.setAllowUniversalAccessFromFileURLs(true);
        ws.setDomStorageEnabled(true);

        webPreview.addJavascriptInterface(new DocxBridge(), "DocxBridge");
        webPreview.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                if (url != null && url.contains("mammoth_converter.html")) {
                    mammothPageReady = true;
                    if (pendingBase64 != null) {
                        final String b64 = pendingBase64;
                        pendingBase64 = null;
                        view.postDelayed(() -> triggerMammoth(b64), 300);
                    }
                }
            }
        });
        loadPlaceholder();
    }

    private void loadPlaceholder() {
        String html = "<!DOCTYPE html><html><head><meta charset='UTF-8'></head>"
            + "<body style='background:#1a1a2e;color:#8892b0;"
            + "display:flex;align-items:center;justify-content:center;"
            + "height:100vh;margin:0;font-family:sans-serif;font-size:14px;'>"
            + "<div style='text-align:center'>"
            + "<div style='font-size:48px'>📄</div>"
            + "<p>HTML বা DOCX ফাইল আপলোড করো</p>"
            + "</div></body></html>";
        webPreview.loadDataWithBaseURL(
            "file:///android_asset/", html, "text/html", "UTF-8", null);
    }

    private void setupListeners() {
        btnUpload.setOnClickListener(v -> pickFile());
        btnUploadFont.setOnClickListener(v -> pickFont());
        btnConvert.setOnClickListener(v -> exportToPDF());
    }

    private void pickFile() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
            "text/html",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/msword",
            "application/octet-stream"
        });
        startActivityForResult(Intent.createChooser(i, "HTML বা DOCX বেছে নাও"), REQ_PICK_FILE);
    }

    private void pickFont() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("*/*");
        startActivityForResult(Intent.createChooser(i, "Font (.ttf/.otf)"), REQ_PICK_FONT);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != Activity.RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (req == REQ_PICK_FILE) handleFile(uri);
        else if (req == REQ_PICK_FONT) handleFontFile(uri);
    }

    private void handleFile(Uri uri) {
        String name = uri.getLastPathSegment();
        if (name == null) name = "file";
        tvFileName.setText("📄 " + name);

        if (isDocxFile(uri, name)) {
            convertDocxToHtml(uri);
        } else {
            readHtmlFile(uri);
        }
    }

    // ── DOCX detect: MIME type + extension + magic bytes ──────
    private boolean isDocxFile(Uri uri, String name) {
        // 1. MIME type check (সবচেয়ে reliable)
        String mime = getContentResolver().getType(uri);
        if (mime != null && (
            mime.contains("word") ||
            mime.contains("openxmlformats") ||
            mime.equals("application/zip"))) {
            return true;
        }

        // 2. Extension check
        String lower = name.toLowerCase();
        if (lower.endsWith(".docx") || lower.endsWith(".doc")) return true;

        // 3. Magic bytes check — DOCX = ZIP = starts with PK (0x50 0x4B)
        try {
            InputStream is = getContentResolver().openInputStream(uri);
            if (is != null) {
                byte[] header = new byte[4];
                int read = is.read(header);
                is.close();
                if (read >= 2 && header[0] == 0x50 && header[1] == 0x4B) {
                    return true; // ZIP signature = DOCX
                }
            }
        } catch (IOException ignored) {}

        return false;
    }

    // ── HTML সরাসরি পড়ো ───────────────────────────────────────
    private void readHtmlFile(Uri uri) {
        try {
            InputStream is = getContentResolver().openInputStream(uri);
            if (is == null) { toast("ফাইল পড়া যাচ্ছে না"); return; }
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(is, StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line).append("\n");
            is.close();
            currentHtmlContent = sb.toString();
            refreshPreview();
        } catch (IOException e) { toast("Error: " + e.getMessage()); }
    }

    // ── DOCX → Base64 → mammoth.js → HTML ─────────────────────
    private void convertDocxToHtml(Uri uri) {
        progressBar.setVisibility(View.VISIBLE);
        toast("⏳ DOCX convert হচ্ছে...");
        mammothPageReady = false;

        new Thread(() -> {
            try {
                InputStream is = getContentResolver().openInputStream(uri);
                if (is == null) {
                    runOnUiThread(() -> { progressBar.setVisibility(View.GONE); toast("ফাইল পড়া যাচ্ছে না"); });
                    return;
                }
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int len;
                while ((len = is.read(buf)) != -1) baos.write(buf, 0, len);
                is.close();
                String base64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);

                runOnUiThread(() -> {
                    pendingBase64 = base64;
                    webPreview.loadUrl("file:///android_asset/mammoth_converter.html");
                });
            } catch (IOException e) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    toast("Error: " + e.getMessage());
                });
            }
        }).start();
    }

    private void triggerMammoth(String base64) {
        // Base64 কে chunk এ ভাগ করে JS variable এ store করো
        // সরাসরি function arg এ দিলে JS engine crash করে বড় ফাইলে
        String js = "window._docxB64 = '" + base64 + "'; convertDocx(window._docxB64);";
        webPreview.evaluateJavascript(js, value -> {
            if (value != null && value.contains("error")) {
                runOnUiThread(() -> toast("JS error: " + value));
            }
        });
    }

    private class DocxBridge {
        @JavascriptInterface
        public void onConverted(String html) {
            runOnUiThread(() -> {
                currentHtmlContent = html;
                progressBar.setVisibility(View.GONE);
                toast("✅ Convert সফল!");
                refreshPreview();
            });
        }
        @JavascriptInterface
        public void onError(String error) {
            runOnUiThread(() -> {
                progressBar.setVisibility(View.GONE);
                toast("❌ " + error);
            });
        }
    }

    private void refreshPreview() {
        if (currentHtmlContent.isEmpty()) return;
        String rendered = WebViewRenderer.buildHtml(
            currentHtmlContent, banglaFont, arabicFont, englishFont, fontManager);
        String encoded = Base64.encodeToString(
            rendered.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
        webPreview.loadData(encoded, "text/html", "base64");
    }

    private void exportToPDF() {
        if (currentHtmlContent.isEmpty()) { toast("আগে ফাইল আপলোড করো"); return; }
        PrintManager pm = (PrintManager) getSystemService(Context.PRINT_SERVICE);
        if (pm == null) return;
        PrintDocumentAdapter adapter = webPreview.createPrintDocumentAdapter("MutuDoc");
        PrintAttributes attrs = new PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
            .setResolution(new PrintAttributes.Resolution("pdf","pdf",600,600))
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .build();
        pm.print("MutuPDF_" + System.currentTimeMillis(), adapter, attrs);
    }

    private void handleFontFile(Uri uri) {
        String saved = fontManager.saveFont(uri);
        if (saved != null) { banglaFont = saved; toast("✅ Font: " + saved); refreshPreview(); }
        else toast("❌ Font সেভ হয়নি");
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
