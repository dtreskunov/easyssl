package com.github.dtreskunov.easyssl;

import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

class ServerCertificateChainGetter {
    private static class SavingTrustManager implements X509TrustManager {
        private X509Certificate[] chain;

        public X509Certificate[] getAcceptedIssuers() {
            return null;//throw new UnsupportedOperationException();
        }

        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            throw new UnsupportedOperationException();
        }

        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            this.chain = chain;
        }
    }

    public static X509Certificate[] getServerCertificateChain(String host, int port) throws Exception {
        KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
        SSLContext context = SSLContext.getInstance("TLS");
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(ks);
        ServerCertificateChainGetter.SavingTrustManager tm = new SavingTrustManager();

        context.init(null, new TrustManager[] { tm }, null);
        SSLSocketFactory factory = context.getSocketFactory();
        SSLSocket socket = (SSLSocket) factory.createSocket(host, port);
        socket.startHandshake();
        socket.close();

        return tm.chain;
    }
}
