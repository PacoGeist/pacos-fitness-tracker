package com.bekdevelopers.ironlog;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.view.View;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Paco's Fitness Tracker: a fully offline workout tracker.
 * The UI is a local HTML page bundled inside the app; all data is saved
 * to this app's private storage on the phone. The app has no INTERNET permission.
 * "Check for updates" hands the GitHub Releases page to the phone's browser;
 * the app itself never downloads anything.
 */
public class MainActivity extends Activity {
    private static final int PICK_FILE = 42;
    private WebView web;
    private ValueCallback<Uri[]> fileCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        web = new WebView(this);
        setContentView(web);
        applyBars(getSharedPreferences("ui", MODE_PRIVATE).getString("theme", "dark"));

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(false);
        s.setBlockNetworkLoads(true);
        s.setCacheMode(WebSettings.LOAD_NO_CACHE);
        s.setTextZoom(100);

        web.addJavascriptInterface(new Bridge(), "AndroidBridge");
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, android.webkit.WebResourceRequest request) {
                return !request.getUrl().toString().startsWith("file:///android_asset/");
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                try {
                    startActivityForResult(Intent.createChooser(i, "Choose a Paco's Fitness Tracker backup"), PICK_FILE);
                } catch (Exception e) {
                    fileCallback = null;
                    return false;
                }
                return true;
            }
        });

        if (savedInstanceState != null) web.restoreState(savedInstanceState);
        else web.loadUrl("file:///android_asset/index.html");
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == PICK_FILE && fileCallback != null) {
            Uri[] result = null;
            if (resultCode == RESULT_OK && data != null && data.getData() != null) result = new Uri[]{data.getData()};
            fileCallback.onReceiveValue(result);
            fileCallback = null;
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        web.evaluateJavascript("(window.handleBack && window.handleBack()) ? 'y' : 'n'", value -> {
            if (value == null || !value.contains("y")) MainActivity.super.onBackPressed();
        });
    }

    /** Match the phone's status and navigation bars to the app theme (system, light or dark). */
    private void applyBars(String theme) {
        boolean dark = "dark".equals(theme) || (!"light".equals(theme)
                && (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES);
        int bg = dark ? Color.parseColor("#0C1020") : Color.parseColor("#F2F4FA");
        int nav = dark ? Color.parseColor("#161B30") : Color.WHITE;
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(nav);
        web.setBackgroundColor(bg);
        int flags = getWindow().getDecorView().getSystemUiVisibility();
        int light = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        getWindow().getDecorView().setSystemUiVisibility(dark ? (flags & ~light) : (flags | light));
    }

    private File dataFile() { return new File(getFilesDir(), "ironlog-data.json"); }

    private class Bridge {
        @JavascriptInterface
        public String loadData() {
            File f = dataFile();
            if (!f.exists()) return null;
            try (FileInputStream in = new FileInputStream(f)) {
                byte[] b = new byte[(int) f.length()];
                int off = 0, n;
                while (off < b.length && (n = in.read(b, off, b.length - off)) > 0) off += n;
                return new String(b, 0, off, StandardCharsets.UTF_8);
            } catch (Exception e) {
                return null;
            }
        }

        @JavascriptInterface
        public synchronized void saveData(String json) {
            File tmp = new File(getFilesDir(), "ironlog-data.tmp");
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                out.write(json.getBytes(StandardCharsets.UTF_8));
                out.getFD().sync();
            } catch (Exception e) {
                return;
            }
            //noinspection ResultOfMethodCallIgnored
            tmp.renameTo(dataFile());
        }

        @JavascriptInterface
        public String getVersion() {
            try {
                return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            } catch (Exception e) {
                return "";
            }
        }

        @JavascriptInterface
        public String getUpdateUrl() { return getString(R.string.update_url); }

        /** Opens the releases page in the phone's browser. Returns false if no browser is available. */
        @JavascriptInterface
        public boolean openUpdatePage() {
            try {
                Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.update_url)));
                i.addCategory(Intent.CATEGORY_BROWSABLE);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        @JavascriptInterface
        public void setTheme(String theme) {
            SharedPreferences p = getSharedPreferences("ui", MODE_PRIVATE);
            p.edit().putString("theme", theme).apply();
            runOnUiThread(() -> applyBars(theme));
        }

        @JavascriptInterface
        public String saveFile(String name, String content) {
            byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
            try {
                if (Build.VERSION.SDK_INT >= 29) {
                    ContentResolver r = getContentResolver();
                    ContentValues v = new ContentValues();
                    v.put(MediaStore.Downloads.DISPLAY_NAME, name);
                    v.put(MediaStore.Downloads.MIME_TYPE, "application/json");
                    v.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                    Uri uri = r.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                    if (uri == null) return "Could not save the backup file";
                    try (OutputStream out = r.openOutputStream(uri)) { out.write(bytes); }
                    return "Saved to Downloads/" + name;
                } else {
                    File dir = getExternalFilesDir(null);
                    File f = new File(dir, name);
                    try (FileOutputStream out = new FileOutputStream(f)) { out.write(bytes); }
                    return "Saved to " + f.getAbsolutePath();
                }
            } catch (Exception e) {
                return "Could not save the backup file";
            }
        }
    }
}
