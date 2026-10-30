package com.acme;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSession;
import javax.net.ssl.X509TrustManager;

class Client {
    abstract static class Pinned implements X509TrustManager {
        public void checkServerTrusted(X509Certificate[] chain, String type) throws CertificateException {
            pin(chain);
        }

        abstract void pin(X509Certificate[] chain) throws CertificateException;
    }

    static class KnownHost implements javax.net.ssl.HostnameVerifier {
        public boolean verify(String host, SSLSession session) {
            return host.equals("api.example.com");
        }
    }

    void lambda() {
        HttpsURLConnection.setDefaultHostnameVerifier((host, session) -> host.endsWith(".example.com"));
    }

    boolean verify(String signature, byte[] data) {
        return true;
    }
}
