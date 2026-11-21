package app.carnet.entrainement;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import androidx.core.content.FileProvider;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.io.File;
import java.io.FileOutputStream;
import android.os.Vibrator;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.webkit.WebViewAssetLoader;

public class MainActivity extends Activity {
    private static final String HOST = "appassets.androidplatform.net";
    private WebView web;
    private ValueCallback<Uri[]> fileCb;
    private Uri camUri;
    private static final int REQ_FILE = 42;

    private Uri cacheUri(String prefix, String ext) throws Exception {
        File f = File.createTempFile(prefix, ext, getCacheDir());
        return FileProvider.getUriForFile(this, getPackageName() + ".files", f);
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.parseColor("#0c110f"));
        getWindow().setNavigationBarColor(Color.parseColor("#0c110f"));

        web = new WebView(this);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setMediaPlaybackRequiresUserGesture(false);

        // Sert les fichiers embarqués (dossier web/) sous une adresse https locale, sans réseau.
        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return loader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                if (HOST.equals(u.getHost())) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception ignored) { }
                return true;
            }
        });

        // Choix d'une photo : appareil photo ou galerie.
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> cb, FileChooserParams params) {
                if (fileCb != null) fileCb.onReceiveValue(null);
                fileCb = cb;
                Intent pick = params.createIntent();
                Intent cam = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                try {
                    camUri = cacheUri("photo", ".jpg");
                    cam.putExtra(MediaStore.EXTRA_OUTPUT, camUri);
                    cam.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (Exception e) { cam = null; camUri = null; }
                Intent chooser = Intent.createChooser(pick, "Photo du plat");
                if (cam != null) chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{cam});
                try { startActivityForResult(chooser, REQ_FILE); }
                catch (Exception e) { fileCb = null; return false; }
                return true;
            }
        });

        web.addJavascriptInterface(new Bridge(), "Android");

        if (state != null) web.restoreState(state);
        else web.loadUrl("https://" + HOST + "/assets/index.html");
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_FILE || fileCb == null) return;
        Uri[] result = null;
        if (res == RESULT_OK) {
            if (data != null && data.getData() != null) result = new Uri[]{data.getData()};
            else if (camUri != null) result = new Uri[]{camUri};
        }
        fileCb.onReceiveValue(result);
        fileCb = null;
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        // Ferme d'abord une fenêtre ou revient à l'accueil ; sinon met l'app en arrière-plan.
        web.evaluateJavascript("window.appBack ? window.appBack() : false", value -> {
            if (!"true".equals(value)) moveTaskToBack(true);
        });
    }

    private class Bridge {
        @JavascriptInterface
        public void share(String text) {
            runOnUiThread(() -> {
                Intent i = new Intent(Intent.ACTION_SEND);
                i.setType("text/plain");
                i.putExtra(Intent.EXTRA_TEXT, text);
                startActivity(Intent.createChooser(i, "Envoyer le carnet"));
            });
        }

        @JavascriptInterface
        public void keepAwake(boolean on) {
            runOnUiThread(() -> {
                if (on) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            });
        }

        @JavascriptInterface
        @SuppressWarnings("deprecation")
        public void vibrate(long ms) {
            Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (v == null) return;
            if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
            else v.vibrate(ms);
        }

        @JavascriptInterface
        public void shareImage(String dataUrl, String text) {
            runOnUiThread(() -> {
                try {
                    String b64 = dataUrl.substring(dataUrl.indexOf(',') + 1);
                    byte[] bytes = Base64.decode(b64, Base64.DEFAULT);
                    File f = File.createTempFile("plat", ".jpg", getCacheDir());
                    FileOutputStream os = new FileOutputStream(f);
                    os.write(bytes);
                    os.close();
                    Uri uri = FileProvider.getUriForFile(MainActivity.this, getPackageName() + ".files", f);
                    Intent i = new Intent(Intent.ACTION_SEND);
                    i.setType("image/jpeg");
                    i.putExtra(Intent.EXTRA_STREAM, uri);
                    i.putExtra(Intent.EXTRA_TEXT, text);
                    i.setClipData(ClipData.newRawUri("plat", uri));
                    i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(Intent.createChooser(i, "Envoyer le plat à Claude"));
                } catch (Exception e) {
                    share(text);
                }
            });
        }

        // Analyse automatique des photos de repas avec l'API Claude (clé de l'utilisateur, gardée sur le téléphone).
        private SharedPreferences prefs() { return getSharedPreferences("carnet", MODE_PRIVATE); }

        @JavascriptInterface
        public boolean hasKey() { return prefs().getString("apiKey", "").length() > 0; }

        @JavascriptInterface
        public void setKey(String key) { prefs().edit().putString("apiKey", key == null ? "" : key.trim()).apply(); }

        @JavascriptInterface
        public void analyze(final String id, final String dataUrl, final String prompt) {
            final String key = prefs().getString("apiKey", "");
            new Thread(() -> {
                String text = "", err = "";
                try {
                    if (key.isEmpty()) throw new Exception("Aucune clé API enregistrée.");
                    String b64 = dataUrl.substring(dataUrl.indexOf(',') + 1);
                    JSONObject img = new JSONObject().put("type", "image").put("source",
                            new JSONObject().put("type", "base64").put("media_type", "image/jpeg").put("data", b64));
                    JSONObject txt = new JSONObject().put("type", "text").put("text", prompt);
                    JSONObject body = new JSONObject().put("model", "claude-sonnet-5-5").put("max_tokens", 500)
                            .put("messages", new JSONArray().put(new JSONObject().put("role", "user")
                                    .put("content", new JSONArray().put(img).put(txt))));
                    HttpURLConnection c = (HttpURLConnection) new URL("https://api.anthropic.com/v1/messages").openConnection();
                    c.setRequestMethod("POST");
                    c.setConnectTimeout(20000);
                    c.setReadTimeout(60000);
                    c.setDoOutput(true);
                    c.setRequestProperty("content-type", "application/json");
                    c.setRequestProperty("x-api-key", key);
                    c.setRequestProperty("anthropic-version", "2023-06-01");
                    try (OutputStream os = c.getOutputStream()) { os.write(body.toString().getBytes(StandardCharsets.UTF_8)); }
                    int code = c.getResponseCode();
                    InputStream is = code < 400 ? c.getInputStream() : c.getErrorStream();
                    ByteArrayOutputStream bo = new ByteArrayOutputStream();
                    byte[] buf = new byte[8192]; int n;
                    while (is != null && (n = is.read(buf)) > 0) bo.write(buf, 0, n);
                    String resp = bo.toString("UTF-8");
                    if (code >= 400) {
                        String msg = resp;
                        try { msg = new JSONObject(resp).getJSONObject("error").getString("message"); } catch (Exception ignored) { }
                        throw new Exception((code == 401 ? "Clé API refusée. " : "Erreur " + code + ". ") + msg);
                    }
                    JSONArray content = new JSONObject(resp).getJSONArray("content");
                    for (int i = 0; i < content.length(); i++) {
                        JSONObject part = content.getJSONObject(i);
                        if ("text".equals(part.optString("type"))) text += part.optString("text");
                    }
                } catch (Exception e) {
                    err = e.getMessage() == null ? "Erreur réseau." : e.getMessage();
                }
                final String js = "window.onAnalyse&&window.onAnalyse(" + JSONObject.quote(id) + "," + JSONObject.quote(text) + "," + JSONObject.quote(err) + ")";
                runOnUiThread(() -> web.evaluateJavascript(js, null));
            }).start();
        }

        @JavascriptInterface
        public void copy(String text) {
            runOnUiThread(() -> {
                ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("Carnet", text));
            });
        }
    }
}
