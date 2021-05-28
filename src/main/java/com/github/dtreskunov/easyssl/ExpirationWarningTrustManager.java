package com.github.dtreskunov.easyssl;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.temporal.TemporalAmount;

import javax.net.ssl.X509TrustManager;

import org.springframework.util.Assert;

/**
  * Logs a warning when any certificate in the chain is close to expiring.
 */
public class ExpirationWarningTrustManager implements X509TrustManager {
    private TemporalAmount warningThreshold;

    public ExpirationWarningTrustManager(TemporalAmount warningThreshold) {
        Assert.notNull(warningThreshold, "warningThreshold may not be null");
        this.warningThreshold = warningThreshold;
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        CertificateExpirationWarning.check(chain, "remote client", warningThreshold);
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        CertificateExpirationWarning.check(chain, "remote server", warningThreshold);
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return null;
    }

}
