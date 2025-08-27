package com.playtimelb.export;

import com.playtimelb.PlaytimeLeaderboardMod;

import javax.net.ssl.*;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

public class InsecureSsl {
    public static SSLSocketFactory trustAllSslSocketFactory() throws Exception {
        TrustManager[] trustAll = new TrustManager[]{
                new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {

                    }

                    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                    public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                }
        };

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustAll, new SecureRandom());
        return sslContext.getSocketFactory();
    }

    public static HostnameVerifier trustAllHosts() {
        return (hostname, session) -> true;
    }
}
