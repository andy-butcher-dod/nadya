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
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;

/** Shows the Nadya web app (bundled in assets/www) full screen, and connects it to reminders and the widget. */
public class MainActivity extends Activity {

    // Android's reserved address for serving an app's own files to a WebView over https.
    static final String HOST = "appassets.androidplatform.net";

    private WebView web;

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
                    InputStream in = getAssets().open("www" + path);
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
