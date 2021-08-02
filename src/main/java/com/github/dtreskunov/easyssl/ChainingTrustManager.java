package com.github.dtreskunov.easyssl;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.net.ssl.X509TrustManager;

import org.springframework.util.Assert;

/**
 * Delegates certificate checking to 0 or more {@link X509TrustManager}s, stopping after first exception.
 * {@link #getAcceptedIssuers()} returns {@code null}.
 */
public class ChainingTrustManager implements X509TrustManager {

    private final List<X509TrustManager> delegates;

    public ChainingTrustManager(List<X509TrustManager> delegates) {
        Assert.notNull(delegates, "delegates may not be null");
        this.delegates = new ArrayList<>(delegates);
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        for (X509TrustManager delegate: delegates) {
            delegate.checkClientTrusted(chain, authType);
        }
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        for (X509TrustManager delegate: delegates) {
            delegate.checkServerTrusted(chain, authType);
        }
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        List<X509Certificate> certificates = new ArrayList<>(2);
        for (X509TrustManager delegate: delegates) {
            X509Certificate[] delegateAccepts = delegate.getAcceptedIssuers();
            if (delegateAccepts != null) {
                certificates.addAll(Arrays.asList(delegateAccepts));
            }
        }
        X509Certificate[] array = new X509Certificate[certificates.size()];
        return certificates.toArray(array);
    }
}
