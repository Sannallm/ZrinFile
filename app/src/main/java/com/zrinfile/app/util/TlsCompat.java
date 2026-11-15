package com.zrinfile.app.util;

import android.content.Context;
import android.util.Log;

import java.io.InputStream;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

/**
 * Android 5/6 的系统证书库较旧，缺少部分公共根证书。这里在系统信任的基础上
 * 追加打包在 assets/certs 中的公共根证书，用于访问 GitHub 与 CDN。
 */
public final class TlsCompat {

    private static final String TAG = "ZrinFileTls";
    private static boolean installed;

    private TlsCompat() {
    }

    public static synchronized void install(Context context) {
        if (installed) return;
        installed = true;
        try {
            TrustManagerFactory def = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            def.init((KeyStore) null);
            X509TrustManager system = findX509(def.getTrustManagers());
            if (system == null) return;

            String[] names = context.getAssets().list("certs");
            if (names == null || names.length == 0) return;

            KeyStore extra = KeyStore.getInstance(KeyStore.getDefaultType());
            extra.load(null, null);
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            int count = 0;
            for (String name : names) {
                InputStream in = null;
                try {
                    in = context.getAssets().open("certs/" + name);
                    X509Certificate cert = (X509Certificate) cf.generateCertificate(in);
                    extra.setCertificateEntry("extra-" + count, cert);
                    count++;
                } catch (Exception e) {
                    Log.w(TAG, "skip " + name + ": " + e.getMessage());
                } finally {
                    if (in != null) {
                        try {
                            in.close();
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
            if (count == 0) return;

            TrustManagerFactory extraTmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            extraTmf.init(extra);
            X509TrustManager extraTm = findX509(extraTmf.getTrustManagers());
            if (extraTm == null) return;

            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, new TrustManager[]{new Composite(system, extraTm)}, new SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(ctx.getSocketFactory());
            Log.d(TAG, "installed, extra roots=" + count);
        } catch (Exception e) {
            Log.w(TAG, "install failed: " + e.getMessage());
        }
    }

    private static X509TrustManager findX509(TrustManager[] managers) {
        if (managers == null) return null;
        for (TrustManager tm : managers) {
            if (tm instanceof X509TrustManager) return (X509TrustManager) tm;
        }
        return null;
    }

    private static final class Composite implements X509TrustManager {

        private final X509TrustManager system;
        private final X509TrustManager extra;

        Composite(X509TrustManager system, X509TrustManager extra) {
            this.system = system;
            this.extra = extra;
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws java.security.cert.CertificateException {
            try {
                system.checkClientTrusted(chain, authType);
            } catch (java.security.cert.CertificateException e) {
                extra.checkClientTrusted(chain, authType);
            }
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws java.security.cert.CertificateException {
            try {
                system.checkServerTrusted(chain, authType);
            } catch (java.security.cert.CertificateException e) {
                extra.checkServerTrusted(chain, authType);
            }
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            X509Certificate[] a = system.getAcceptedIssuers();
            X509Certificate[] b = extra.getAcceptedIssuers();
            X509Certificate[] all = new X509Certificate[a.length + b.length];
            System.arraycopy(a, 0, all, 0, a.length);
            System.arraycopy(b, 0, all, a.length, b.length);
            return all;
        }
    }
}
