package com.github.dtreskunov.easyssl;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.TemporalAmount;

import javax.net.ssl.X509TrustManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;

/**
 * Logs a warning when any certificatein the chain is close to expiring.
 */
public class ExpirationWarningTrustManager implements X509TrustManager {
    private static final Logger LOG = LoggerFactory.getLogger(ExpirationWarningTrustManager.class);
    private static final String CLIENT = "client";
    private static final String SERVER = "server";
    private TemporalAmount warningThreshold;

    public ExpirationWarningTrustManager(TemporalAmount warningThreshold) {
        Assert.notNull(warningThreshold, "warningThreshold may not be null");
        this.warningThreshold = warningThreshold;
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        check(chain, CLIENT);
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        check(chain, SERVER);
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return null;
    }

    private void check(X509Certificate[] chain, String provenance) {
        if (chain == null || chain.length == 0) {
            return;
        }
        Instant warnIfExpiresBefore = Instant.now().plus(warningThreshold);

        for (int i=0; i<chain.length; i++) {
            X509Certificate cert = chain[i];
            if (cert.getNotAfter().toInstant().isBefore(warnIfExpiresBefore)) {
                LOG.warn("{} certificate i={} sub='{}' iss='{}' serial={} expires on {} (less than {} from now)",
                    provenance, i, cert.getSubjectX500Principal(), cert.getIssuerX500Principal(), cert.getSerialNumber(),
                    cert.getNotAfter(), warningThreshold);
            }
        }
    }
}
