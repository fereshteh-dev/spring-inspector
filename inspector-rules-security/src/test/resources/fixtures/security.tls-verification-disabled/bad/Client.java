package com.acme;

import java.security.cert.X509Certificate;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSession;
import javax.net.ssl.X509TrustManager;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;

class Client {
    static class TrustAll implements X509TrustManager {
        public void checkClientTrusted(X509Certificate[] chain, String type) {}

        public void checkServerTrusted(X509Certificate[] chain, String type) {}

        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }

    static class AnyHost implements javax.net.ssl.HostnameVerifier {
        public boolean verify(String host, SSLSession session) {
            return true;
        }
    }

    void lambda() {
        HttpsURLConnection.setDefaultHostnameVerifier((host, session) -> true);
    }
}
