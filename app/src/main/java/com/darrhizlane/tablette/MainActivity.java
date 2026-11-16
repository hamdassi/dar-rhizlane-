package com.darrhizlane.tablette;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

/** Fenêtre principale : affiche l'application du serveur dans une WebView plein écran. */
public class MainActivity extends Activity {

    private WebView web;
    private SwipeRefreshLayout refresh;
    private String base;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        SharedPreferences p = getSharedPreferences(ConnexionActivity.PREFS, MODE_PRIVATE);
        base = p.getString(ConnexionActivity.CLE_BASE, null);
        if (base == null) {                         // pas encore configuré
            startActivity(new Intent(this, ConnexionActivity.class));
            finish();
            return;
        }
        setContentView(R.layout.activity_main);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        web = findViewById(R.id.web);
        refresh = findViewById(R.id.refresh);
        ImageButton menu = findViewById(R.id.menu);
        menu.setOnClickListener(v -> ouvrirMenu());

        configurerWeb();
        refresh.setOnRefreshListener(() -> web.reload());
        web.loadUrl(base + "/");
    }

    @SuppressWarnings({"SetJavaScriptEnabled"})
    private void configurerWeb() {
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setMediaPlaybackRequiresUserGesture(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);

        final String hoteServeur = Uri.parse(base).getHost();

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                return ouvrirExterne(req.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return ouvrirExterne(Uri.parse(url));
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                refresh.setRefreshing(false);
            }

            @Override
            public void onReceivedError(WebView view, android.webkit.WebResourceRequest request, android.webkit.WebResourceError error) {
                if (request.isForMainFrame()) {
                    refresh.setRefreshing(false);
                    pageErreur();
                }
            }

            /** Les liens vers d'autres sites (WhatsApp, e-mail, cartes…) s'ouvrent dans l'application dédiée. */
            private boolean ouvrirExterne(Uri uri) {
                String scheme = uri.getScheme();
                if (scheme == null) return false;
                if (scheme.equals("http") || scheme.equals("https")) {
                    String h = uri.getHost();
                    if (h != null && h.equals(hoteServeur)) return false; // reste dans l'application
                }
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, uri));
                    return true;
                } catch (Exception e) {
                    return false;
                }
            }
        });

        web.setWebChromeClient(new WebChromeClient());

        // Téléchargements (PDF, CSV) -> dossier Téléchargements de la tablette
        web.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimetype, long contentLength) {
                try {
                    DownloadManager.Request r = new DownloadManager.Request(Uri.parse(url));
                    r.setMimeType(mimetype);
                    String nom = Uri.parse(url).getLastPathSegment();
                    if (nom == null) nom = "dar-rhizlane";
                    r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                    r.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, nom);
                    DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
                    if (dm != null) dm.enqueue(r);
                    Toast.makeText(MainActivity.this, "Téléchargement…", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "Téléchargement impossible.", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void pageErreur() {
        String html = "<html><body style='font-family:sans-serif;background:#f6f1e9;color:#2b2420;text-align:center;padding:40px'>"
                + "<h2 style='color:#7a2e1f'>Serveur injoignable</h2>"
                + "<p>Impossible de joindre le serveur Dar Rhizlane à l'adresse<br><b>" + base + "</b>.</p>"
                + "<p>Vérifiez que le PC serveur est allumé avec l'application ouverte, que le « partage réseau » est activé, et que la tablette est sur le même Wi-Fi.</p>"
                + "</body></html>";
        web.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null);
    }

    private void ouvrirMenu() {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.app_name))
                .setItems(new CharSequence[]{getString(R.string.actualiser), getString(R.string.changer_serveur)}, (d, i) -> {
                    if (i == 0) {
                        web.loadUrl(base + "/");
                    } else {
                        new AlertDialog.Builder(this)
                                .setMessage("Se déconnecter de ce serveur et en choisir un autre ?")
                                .setPositiveButton(R.string.changer_serveur, (dd, ii) -> {
                                    getSharedPreferences(ConnexionActivity.PREFS, MODE_PRIVATE).edit().remove(ConnexionActivity.CLE_BASE).apply();
                                    startActivity(new Intent(this, ConnexionActivity.class));
                                    finish();
                                })
                                .setNegativeButton("Annuler", null)
                                .show();
                    }
                })
                .show();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && web != null && web.canGoBack()) {
            web.goBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}
