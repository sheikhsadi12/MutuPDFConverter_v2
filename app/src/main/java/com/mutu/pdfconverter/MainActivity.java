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

    private WebView  webPreview;
    private Spinner  spBangla, spArabic, spEnglish;
    private TextView tvFileName;
    private Button   btnUpload, btnUploadFont, btnConvert;
    private ProgressBar progressBar;

    private String currentHtmlContent = "";
    private String banglaFont  = "SolaimanLipi";
    private String arabicFont  = "Amiri";
    private String englishFont = "Georgia";

    private final String[] banglaFonts  = {"SolaimanLipi","Kalpurush","Hind Siliguri"};
    private final String[] arabicFonts  = {"Amiri","Noto Naskh Arabic"};
    private final String[] englishFonts = {"Georgia","Times New Roman","Courier New"};

    private FontManager fontManager;

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

        // Java bridge — JS এখান থেকে converted HTML পাঠাবে
        webPreview.addJavascriptInterface(new DocxBridge(), "DocxBridge");
        webPreview.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                progressBar.setVisibility(View.GONE);
            }
        });
        loadPlaceholder();
    }

    private void loadPlaceholder() {
        String html = "<html><body style='background:#1a1a2e;color:#8892b0;"
                + "display:flex;align-items:center;justify-content:center;"
                + "height:100vh;margin:0;font-family:sans-serif;font-size:14px;'>"
                + "<div style='text-align:center'>"
                + "<div style='font-size:48px'>📄</div>"
                + "<p>HTML বা DOCX ফাইল আপলোড করো<br>Preview এখানে দেখাবে</p>"
                + "</div></body></html>";
        webPreview.loadData(html, "text/html", "UTF-8");
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
        startActivityForResult(Intent.createChooser(i, "HTML বা DOCX ফাইল বেছে নাও"), REQ_PICK_FILE);
    }

    private void pickFont() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("*/*");
        startActivityForResult(Intent.createChooser(i, "Font ফাইল (.ttf/.otf)"), REQ_PICK_FONT);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != Activity.RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (req == REQ_PICK_FILE)  handleFile(uri);
        else if (req == REQ_PICK_FONT) handleFontFile(uri);
    }

    private void handleFile(Uri uri) {
        String name = uri.getLastPathSegment();
        if (name == null) name = "file";
        tvFileName.setText("📄 " + name);

        if (name.toLowerCase().endsWith(".docx") || name.toLowerCase().endsWith(".doc")) {
            convertDocxToHtml(uri);
        } else {
            readHtmlFile(uri);
        }
    }

    // ── HTML file সরাসরি পড়ো ─────────────────────────────────
    private void readHtmlFile(Uri uri) {
        try {
            InputStream is = getContentResolver().openInputStream(uri);
            if (is == null) { toast("ফাইল পড়া যাচ্ছে না"); return; }
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line).append("\n");
            is.close();
            currentHtmlContent = sb.toString();
            refreshPreview();
        } catch (IOException e) {
            toast("Error: " + e.getMessage());
        }
    }

    // ── DOCX → Base64 → WebView → mammoth.js → HTML ──────────
    private void convertDocxToHtml(Uri uri) {
        progressBar.setVisibility(View.VISIBLE);
        toast("⏳ DOCX convert হচ্ছে...");

        new Thread(() -> {
            try {
                InputStream is = getContentResolver().openInputStream(uri);
                if (is == null) { runOnUiThread(() -> toast("ফাইল পড়া যাচ্ছে না")); return; }

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int len;
                while ((len = is.read(buf)) != -1) baos.write(buf, 0, len);
                is.close();

                // DOCX bytes → Base64 string
                String base64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);

                // mammoth.js কে trigger করো WebView এ
                runOnUiThread(() -> loadMammothConverter(base64));

            } catch (IOException e) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    toast("Error: " + e.getMessage());
                });
            }
        }).start();
    }

    // mammoth.js দিয়ে conversion page load করো
    private void loadMammothConverter(String base64) {
        String converterHtml = "<!DOCTYPE html><html><head>"
            + "<meta charset='UTF-8'>"
            + "<script src='file:///android_asset/js/mammoth.browser.min.js'></script>"
            + "</head><body>"
            + "<script>"
            + "var base64 = '" + base64 + "';"
            // Base64 → ArrayBuffer
            + "var binary = atob(base64);"
            + "var bytes = new Uint8Array(binary.length);"
            + "for(var i=0;i<binary.length;i++) bytes[i]=binary.charCodeAt(i);"
            + "var arrayBuffer = bytes.buffer;"
            // mammoth convert
            + "mammoth.convertToHtml({arrayBuffer: arrayBuffer})"
            + ".then(function(result){"
            + "  DocxBridge.onConverted(result.value);" // Java bridge কে পাঠাও
            + "}).catch(function(err){"
            + "  DocxBridge.onError(err.toString());"
            + "});"
            + "</script>"
            + "</body></html>";

        webPreview.loadDataWithBaseURL(
            "file:///android_asset/", converterHtml, "text/html", "UTF-8", null);
    }

    // ── Java Bridge — JS থেকে HTML পাবে ─────────────────────
    private class DocxBridge {
        @JavascriptInterface
        public void onConverted(String html) {
            runOnUiThread(() -> {
                currentHtmlContent = html;
                toast("✅ DOCX convert সফল!");
                refreshPreview();
            });
        }

        @JavascriptInterface
        public void onError(String error) {
            runOnUiThread(() -> {
                progressBar.setVisibility(View.GONE);
                toast("❌ Error: " + error);
            });
        }
    }

    private void refreshPreview() {
        if (currentHtmlContent.isEmpty()) return;
        String rendered = WebViewRenderer.buildHtml(
                currentHtmlContent, banglaFont, arabicFont, englishFont, fontManager);
        webPreview.loadDataWithBaseURL(
                "file:///android_asset/", rendered, "text/html", "UTF-8", null);
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
        if (saved != null) { banglaFont = saved; toast("✅ Font সেভ: " + saved); refreshPreview(); }
        else toast("❌ Font সেভ হয়নি");
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
