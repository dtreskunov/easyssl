package com.github.dtreskunov.easyssl;

import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.TemporalAmount;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CertificateExpirationWarning {
    private static final Logger LOG = LoggerFactory.getLogger(CertificateExpirationWarning.class);

    /**
     * Logs a warning when any certificate in the chain is close to expiring.
     * @param chain
     * @param provenance added to the log message to help disambiguate which certificate is expiring
     * @param warningThreshold how close to expiring the certificate needs to be before a warning is logged
     */
    public static void check(X509Certificate[] chain, String provenance, TemporalAmount warningThreshold) {
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
