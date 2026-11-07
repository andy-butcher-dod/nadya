package uk.co.definitionofdone.nadya;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;

/** Shows the Nadya web app (bundled in assets/www) full screen, and connects it to reminders and the widget. */
public class MainActivity extends Activity {

    // Android's reserved address for serving an app's own files to a WebView over https.
    static final String HOST = "appassets.androidplatform.net";

    private WebView web;

    // Where the latest lessons and screens are published (the web app on GitHub Pages).
    static final String REMOTE = "https://andy-butcher-dod.github.io/nadya/index.html";
    private static final long CHECK_EVERY_MS = 10 * 60 * 1000;
    private long lastCheck = 0;
    private String bundledVersion;

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        web = new WebView(this);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest req) {
                Uri u = req.getUrl();
                if (!HOST.equals(u.getHost())) return null;
                String path = u.getPath();
                if (path == null || path.equals("/") || path.isEmpty()) path = "/index.html";
                try {
                    InputStream in = path.equals("/index.html") ? indexStream() : getAssets().open("www" + path);
                    return new WebResourceResponse(mime(path), "utf-8", in);
                } catch (IOException e) {
                    return new WebResourceResponse("text/plain", "utf-8", 404, "Not found",
                            new HashMap<>(), new ByteArrayInputStream(new byte[0]));
                }
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                Uri u = req.getUrl();
                if (HOST.equals(u.getHost())) return false;
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, u));
                } catch (Exception ignored) {
                }
                return true;
            }
        });
        web.addJavascriptInterface(new Bridge(), "NadyaNative");
        web.loadUrl("https://" + HOST + "/index.html");

        Reminders.createChannel(this);
        Reminders.schedule(this);
        askForNotificationsOnce();
    }

    private void askForNotificationsOnce() {
        SharedPreferences p = Store.prefs(this);
        if (Build.VERSION.SDK_INT >= 33 && p.getBoolean("remOn", true) && !Reminders.allowed(this)
                && !p.getBoolean("askedNotif", false)) {
            p.edit().putBoolean("askedNotif", true).apply();
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
    }

    private static String mime(String path) {
        if (path.endsWith(".html")) return "text/html";
        if (path.endsWith(".js")) return "text/javascript";
        if (path.endsWith(".css")) return "text/css";
        if (path.endsWith(".png")) return "image/png";
        if (path.endsWith(".json") || path.endsWith(".webmanifest")) return "application/json";
        return "application/octet-stream";
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkForUpdate();
    }

    @Override
    public void onBackPressed() {
        // The timeline view adds a history entry, so Back closes it instead of the app.
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onPause() {
        super.onPause();
        NadyaWidget.refresh(this);
    }

    // ---- Automatic updates of the web part (lessons, timeline, screens) ----

    private File updatedIndex() {
        return new File(new File(getFilesDir(), "www"), "index.html");
    }

    private static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        in.close();
        return out.toString("UTF-8");
    }

    static String versionOf(String html) {
        Matcher m = Pattern.compile("const APP_VERSION=\"([0-9.]+)\"").matcher(html);
        return m.find() ? m.group(1) : null;
    }

    /** Compares versions like 1.12.2; null counts as oldest. */
    static int compareVersions(String a, String b) {
        if (a == null) return b == null ? 0 : -1;
        if (b == null) return 1;
        String[] x = a.split("\\."), y = b.split("\\.");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int p = i < x.length ? toInt(x[i]) : 0, q = i < y.length ? toInt(y[i]) : 0;
            if (p != q) return p < q ? -1 : 1;
        }
        return 0;
    }

    private static int toInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (Exception e) {
            return 0;
        }
    }

    private synchronized String bundledVersion() {
        if (bundledVersion == null) {
            try {
                bundledVersion = versionOf(readAll(getAssets().open("www/index.html")));
            } catch (IOException e) {
                bundledVersion = "0";
            }
        }
        return bundledVersion;
    }

    /** The downloaded version, if it is newer than the one built into the app; otherwise null. */
    private String downloadedVersion() {
        String d = Store.prefs(this).getString("webVersion", null);
        if (d == null || !updatedIndex().exists()) return null;
        return compareVersions(d, bundledVersion()) > 0 ? d : null;
    }

    private String currentVersion() {
        String d = downloadedVersion();
        return d != null ? d : bundledVersion();
    }

    private InputStream indexStream() throws IOException {
        if (downloadedVersion() != null) return new FileInputStream(updatedIndex());
        return getAssets().open("www/index.html");
    }

    /** Fetches the latest web app in the background; if it is newer, saves it and tells the page. */
    private void checkForUpdate() {
        long now = System.currentTimeMillis();
        if (now - lastCheck < CHECK_EVERY_MS) return;
        lastCheck = now;
        new Thread(() -> {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(REMOTE + "?t=" + now).openConnection();
                c.setUseCaches(false);
                c.setConnectTimeout(10000);
                c.setReadTimeout(20000);
                c.setRequestProperty("Cache-Control", "no-cache");
                if (c.getResponseCode() != 200) return;
                String html = readAll(c.getInputStream());
                c.disconnect();
                String v = versionOf(html);
                // Only accept a complete copy of the real app.
                if (v == null || html.length() < 50000 || !html.contains("</html>") || !html.contains("NadyaNative")) return;
                if (compareVersions(v, currentVersion()) <= 0) return;
                File dir = updatedIndex().getParentFile();
                if (!dir.exists() && !dir.mkdirs()) return;
                File tmp = new File(dir, "index.html.tmp");
                try (FileOutputStream out = new FileOutputStream(tmp)) {
                    out.write(html.getBytes("UTF-8"));
                }
                if (!tmp.renameTo(updatedIndex())) return;
                Store.prefs(this).edit().putString("webVersion", v).apply();
                runOnUiThread(() -> web.evaluateJavascript("window.nadyaUpdateReady&&nadyaUpdateReady()", null));
            } catch (Exception ignored) {
                // Offline or GitHub unavailable: keep using the copy we have.
            }
        }).start();
    }

    /** Called from the web app's JavaScript as window.NadyaNative. */
    private class Bridge {
        @JavascriptInterface
        public void reportProgress(String json) {
            try {
                JSONObject o = new JSONObject(json);
                SharedPreferences.Editor e = Store.prefs(MainActivity.this).edit();
                if (o.isNull("last")) e.remove("last");
                else e.putString("last", o.getString("last"));
                e.putInt("streak", o.optInt("streak", 0));
                e.putInt("best", o.optInt("best", 0));
                e.apply();
            } catch (Exception ignored) {
            }
            NadyaWidget.refresh(MainActivity.this);
        }

        @JavascriptInterface
        public String getReminder() {
            SharedPreferences p = Store.prefs(MainActivity.this);
            PowerManager pm = getSystemService(PowerManager.class);
            boolean unrestricted = pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
            return "{\"on\":" + p.getBoolean("remOn", true)
                    + ",\"hour\":" + p.getInt("remHour", 18)
                    + ",\"min\":" + p.getInt("remMin", 0)
                    + ",\"allowed\":" + Reminders.allowed(MainActivity.this)
                    + ",\"next\":" + p.getLong("nextAt", 0)
                    + ",\"lastAt\":" + p.getLong("lastAt", 0)
                    + ",\"lastResult\":\"" + p.getString("lastResult", "") + "\""
                    + ",\"unrestricted\":" + unrestricted + "}";
        }

        @JavascriptInterface
        public String shellVersion() {
            try {
                return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            } catch (Exception e) {
                return "";
            }
        }

        @JavascriptInterface
        public String testReminder() {
            return Reminders.sendTest(MainActivity.this);
        }

        @JavascriptInterface
        public void openAppSettings() {
            runOnUiThread(() -> {
                try {
                    startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:" + getPackageName())));
                } catch (Exception ignored) {
                }
            });
        }

        @JavascriptInterface
        public void setReminder(boolean on, int hour, int min) {
            Store.prefs(MainActivity.this).edit()
                    .putBoolean("remOn", on).putInt("remHour", hour).putInt("remMin", min).apply();
            Reminders.schedule(MainActivity.this);
            if (on && Build.VERSION.SDK_INT >= 33 && !Reminders.allowed(MainActivity.this)) {
                runOnUiThread(() -> requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1));
            }
        }
    }
}
