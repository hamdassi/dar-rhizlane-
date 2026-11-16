package com.darrhizlane.tablette;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URL;
import java.util.Enumeration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Écran de connexion : saisie ou détection de l'adresse du serveur Dar Rhizlane. */
public class ConnexionActivity extends Activity {

    public static final String PREFS = "dr";
    public static final String CLE_BASE = "base";

    private EditText ip, port;
    private TextView etat;
    private ProgressBar progres;
    private Button btnTester, btnDetecter;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        String base = p.getString(CLE_BASE, null);
        if (base != null) {                        // déjà configuré : aller directement à l'application
            demarrer();
            return;
        }
        setContentView(R.layout.activity_connexion);
        ip = findViewById(R.id.ip);
        port = findViewById(R.id.port);
        etat = findViewById(R.id.etat);
        progres = findViewById(R.id.progres);
        btnTester = findViewById(R.id.btnTester);
        btnDetecter = findViewById(R.id.btnDetecter);

        btnTester.setOnClickListener(v -> tester(baseSaisie()));
        btnDetecter.setOnClickListener(v -> detecter());
    }

    private String baseSaisie() {
        String a = ip.getText().toString().trim();
        String pr = port.getText().toString().trim();
        if (TextUtils.isEmpty(pr)) pr = "47820";
        if (TextUtils.isEmpty(a)) return null;
        return "http://" + a + ":" + pr;
    }

    private void demarrer() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    private void occupe(boolean oui, String msg) {
        progres.setVisibility(oui ? View.VISIBLE : View.GONE);
        btnTester.setEnabled(!oui);
        btnDetecter.setEnabled(!oui);
        etat.setText(msg == null ? "" : msg);
    }

    /** Teste une adresse ; si c'est bien un serveur Dar Rhizlane, l'enregistre et ouvre l'application. */
    private void tester(final String base) {
        if (base == null) { etat.setText("Saisissez l'adresse IP du serveur."); return; }
        occupe(true, "Connexion à " + base + " …");
        new Thread(() -> {
            final boolean ok = estServeur(base, 4000);
            runOnUiThread(() -> {
                occupe(false, null);
                if (ok) {
                    getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(CLE_BASE, base).apply();
                    demarrer();
                } else {
                    etat.setText("Serveur introuvable à cette adresse.\nVérifiez l'adresse IP, que le serveur est allumé et que le « partage réseau » est activé (Administration › Réseau), et que la tablette est sur le même Wi-Fi.");
                }
            });
        }).start();
    }

    /** Scanne le sous-réseau local (x.x.x.1-254) pour trouver le serveur automatiquement. */
    private void detecter() {
        final String prefixe = prefixeLocal();
        final String pr = TextUtils.isEmpty(port.getText().toString().trim()) ? "47820" : port.getText().toString().trim();
        if (prefixe == null) { etat.setText("Impossible de déterminer le réseau. Vérifiez le Wi-Fi, puis saisissez l'adresse à la main."); return; }
        occupe(true, "Recherche du serveur sur le réseau…");
        final ExecutorService ex = Executors.newFixedThreadPool(60);
        final AtomicBoolean trouve = new AtomicBoolean(false);
        final AtomicInteger faits = new AtomicInteger(0);
        for (int i = 1; i <= 254; i++) {
            final String hote = prefixe + i;
            ex.submit(() -> {
                if (trouve.get()) return;
                String base = "http://" + hote + ":" + pr;
                if (estServeur(base, 500) && trouve.compareAndSet(false, true)) {
                    runOnUiThread(() -> {
                        ex.shutdownNow();
                        ip.setText(hote);
                        occupe(false, "Serveur trouvé : " + hote);
                        tester(base);
                    });
                }
                int n = faits.incrementAndGet();
                if (n >= 254 && !trouve.get()) {
                    runOnUiThread(() -> occupe(false, "Aucun serveur trouvé automatiquement. Saisissez l'adresse IP affichée sur le serveur."));
                }
            });
        }
    }

    /** Vrai si l'adresse répond comme un serveur Dar Rhizlane (/api/info). */
    private boolean estServeur(String base, int timeout) {
        HttpURLConnection c = null;
        try {
            URL u = new URL(base + "/api/info");
            c = (HttpURLConnection) u.openConnection();
            c.setConnectTimeout(timeout);
            c.setReadTimeout(timeout);
            c.setRequestProperty("X-DR", "1");
            if (c.getResponseCode() != 200) return false;
            InputStream in = c.getInputStream();
            ByteArrayOutputStream o = new ByteArrayOutputStream();
            byte[] buf = new byte[2048];
            int r;
            while ((r = in.read(buf)) > 0 && o.size() < 8192) o.write(buf, 0, r);
            return o.toString("UTF-8").contains("dar-rhizlane");
        } catch (Exception e) {
            return false;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private String prefixeLocal() {
        try {
            Enumeration<NetworkInterface> ifs = NetworkInterface.getNetworkInterfaces();
            while (ifs.hasMoreElements()) {
                NetworkInterface ni = ifs.nextElement();
                if (!ni.isUp() || ni.isLoopback()) continue;
                Enumeration<InetAddress> as = ni.getInetAddresses();
                while (as.hasMoreElements()) {
                    InetAddress a = as.nextElement();
                    if (a.isSiteLocalAddress() && a instanceof java.net.Inet4Address) {
                        String ip = a.getHostAddress();
                        int p = ip.lastIndexOf('.');
                        if (p > 0) return ip.substring(0, p + 1);
                    }
                }
            }
        } catch (Exception ignore) { }
        return null;
    }
}
