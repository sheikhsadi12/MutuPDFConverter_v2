package com.mutu.pdfconverter;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import java.io.*;
import java.nio.charset.StandardCharsets;

public class MainActivity extends AppCompatActivity {

    // ── Request codes ──────────────────────────────────────────
    private static final int REQ_PICK_HTML   = 101;
    private static final int REQ_PICK_FONT   = 102;

    // ── UI refs ────────────────────────────────────────────────
    private WebView  webPreview;
    private Spinner  spBangla, spArabic, spEnglish;
    private TextView tvFileName;
    private Button   btnUpload, btnUploadFont, btnConvert;

    // ── State ──────────────────────────────────────────────────
    private String   currentHtmlContent = "";
    private String   banglaFont  = "SolaimanLipi";
    private String   arabicFont  = "Amiri";
    private String   englishFont = "Georgia";

    // Built-in font lists (ttf must exist in assets/fonts/)
    private final String[] banglaFonts  = {"SolaimanLipi", "Kalpurush", "Hind Siliguri"};
    private final String[] arabicFonts  = {"Amiri", "Noto Naskh Arabic"};
    private final String[] englishFonts = {"Georgia", "Times New Roman", "Courier New"};

    // Custom-uploaded fonts stored in internal storage
    private FontManager fontManager;

    // ──────────────────────────────────────────────────────────
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

    // ── Bind XML views ─────────────────────────────────────────
    private void bindViews() {
        webPreview    = findViewById(R.id.webPreview);
        spBangla      = findViewById(R.id.spBangla);
        spArabic      = findViewById(R.id.spArabic);
        spEnglish     = findViewById(R.id.spEnglish);
        tvFileName    = findViewById(R.id.tvFileName);
        btnUpload     = findViewById(R.id.btnUpload);
        btnUploadFont = findViewById(R.id.btnUploadFont);
        btnConvert    = findViewById(R.id.btnConvert);
    }

    // ── Populate spinners ──────────────────────────────────────
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

    // ── WebView settings ───────────────────────────────────────
    private void setupWebView() {
        WebSettings ws = webPreview.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setAllowFileAccess(true);
        ws.setAllowContentAccess(true);
        ws.setAllowFileAccessFromFileURLs(true);
        ws.setAllowUniversalAccessFromFileURLs(true);
        webPreview.setWebViewClient(new WebViewClient());

        // Show placeholder on first launch
        loadPlaceholder();
    }

    private void loadPlaceholder() {
        String html = "<html><body style='background:#1a1a2e;color:#8892b0;"
                + "display:flex;align-items:center;justify-content:center;"
                + "height:100vh;margin:0;font-family:sans-serif;font-size:14px;'>"
                + "<div style='text-align:center'>"
                + "<div style='font-size:40px'>📄</div>"
                + "<p>HTML ফাইল আপলোড করো<br>Preview এখানে দেখাবে</p>"
                + "</div></body></html>";
        webPreview.loadData(html, "text/html", "UTF-8");
    }

    // ── Button listeners ───────────────────────────────────────
    private void setupListeners() {
        btnUpload.setOnClickListener(v -> pickFile());
        btnUploadFont.setOnClickListener(v -> pickFont());
        btnConvert.setOnClickListener(v -> exportToPDF());
    }

    // ── File picker (HTML) ─────────────────────────────────────
    private void pickFile() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"text/html", "application/octet-stream"});
        startActivityForResult(Intent.createChooser(i, "HTML ফাইল বেছে নাও"), REQ_PICK_HTML);
    }

    // ── Font picker ────────────────────────────────────────────
    private void pickFont() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("*/*");
        startActivityForResult(Intent.createChooser(i, "Font ফাইল (.ttf/.otf)"), REQ_PICK_FONT);
    }

    // ── Activity result ────────────────────────────────────────
    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != Activity.RESULT_OK || data == null) return;

        Uri uri = data.getData();
        if (uri == null) return;

        if (req == REQ_PICK_HTML) {
            handleHtmlFile(uri);
        } else if (req == REQ_PICK_FONT) {
            handleFontFile(uri);
        }
    }

    // ── Read HTML file ─────────────────────────────────────────
    private void handleHtmlFile(Uri uri) {
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
            String name = uri.getLastPathSegment();
            tvFileName.setText("📄 " + (name != null ? name : "file.html"));
            refreshPreview();

        } catch (IOException e) {
            toast("Error: " + e.getMessage());
        }
    }

    // ── Copy font to internal storage ──────────────────────────
    private void handleFontFile(Uri uri) {
        String saved = fontManager.saveFont(uri);
        if (saved != null) {
            toast("✅ Font সেভ হয়েছে: " + saved);
            // Add to Bangla spinner as custom option
            banglaFont = saved;
            refreshPreview();
        } else {
            toast("❌ Font সেভ করা যায়নি");
        }
    }

    // ── Build HTML with injected fonts + lang detection ────────
    private void refreshPreview() {
        if (currentHtmlContent.isEmpty()) return;
        String rendered = WebViewRenderer.buildHtml(
                currentHtmlContent, banglaFont, arabicFont, englishFont, fontManager);
        webPreview.loadDataWithBaseURL(
                "file:///android_asset/", rendered, "text/html", "UTF-8", null);
    }

    // ── PDF Export via Android Print framework ─────────────────
    private void exportToPDF() {
        if (currentHtmlContent.isEmpty()) {
            toast("আগে HTML ফাইল আপলোড করো");
            return;
        }
        PrintManager pm = (PrintManager) getSystemService(Context.PRINT_SERVICE);
        if (pm == null) { toast("Print service unavailable"); return; }

        PrintDocumentAdapter adapter = webPreview.createPrintDocumentAdapter("MutuDoc");
        PrintAttributes attrs = new PrintAttributes.Builder()
                .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                .setResolution(new PrintAttributes.Resolution("pdf", "pdf", 600, 600))
                .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                .build();

        pm.print("MutuPDF_" + System.currentTimeMillis(), adapter, attrs);
    }

    // ── Utility ────────────────────────────────────────────────
    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
