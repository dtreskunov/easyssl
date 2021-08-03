package com.github.dtreskunov.easyssl;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Duration;

import javax.net.ssl.X509TrustManager;

import org.springframework.util.Assert;

/**
  * Logs an error or a warning when any certificate in the chain has expired or is close to expiring.
  * Actually rejecting expired certificates is handled elsewhere. 
 */
class ExpirationCheckTrustManager implements X509TrustManager {
    private Duration warningThreshold;

    public ExpirationCheckTrustManager(Duration warningThreshold) {
        Assert.notNull(warningThreshold, "warningThreshold may not be null");
        this.warningThreshold = warningThreshold;
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        CertificateExpirationCheck.check(chain, "remote client", warningThreshold);
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        CertificateExpirationCheck.check(chain, "remote server", warningThreshold);
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return null;
    }

}
