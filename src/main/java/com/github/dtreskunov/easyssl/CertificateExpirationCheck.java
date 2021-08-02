package com.github.dtreskunov.easyssl;

import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.TemporalAmount;
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CertificateExpirationCheck {
    private static final Logger LOG = LoggerFactory.getLogger(CertificateExpirationCheck.class);
    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor(
        ThreadFactoryFactory.createThreadFactory(true, CertificateExpirationCheck.class.getSimpleName() + " daemon"));

    /**
     * Logs an error when any certificate in the chain has expired, and a warning if it's close to expiring.
     * @param certificates
     * @param provenance added to the log message to help disambiguate which certificate is expiring
     * @param warningThreshold how close to expiring the certificate needs to be before a warning is logged (may be null)
     */
    public static void check(X509Certificate[] certificates, String provenance, Duration warningThreshold) {
        if (certificates == null || certificates.length == 0) {
            return;
        }
        Instant now = Instant.now();
        Instant warnIfExpiresBefore = (warningThreshold == null ? null : now.plus(warningThreshold));

        for (int i=0; i<certificates.length; i++) {
            X509Certificate cert = certificates[i];
            Instant certExpiration = cert.getNotAfter().toInstant();
            if (certExpiration.isBefore(now)) {
                LOG.error("{} certificate i={} sub='{}' iss='{}' serial={} has expired on {} ({} ago)",
                    provenance, i, cert.getSubjectX500Principal(), cert.getIssuerX500Principal(), cert.getSerialNumber(),
                    certExpiration, Duration.between(certExpiration, now));
            } else if (warnIfExpiresBefore != null && certExpiration.isBefore(warnIfExpiresBefore)) {
                LOG.warn("{} certificate i={} sub='{}' iss='{}' serial={} expires on {} (less than {} from now)",
                    provenance, i, cert.getSubjectX500Principal(), cert.getIssuerX500Principal(), cert.getSerialNumber(),
                    certExpiration, warningThreshold);
            }
        }
    }
    
    /**
     * Schedules a {@link #check(X509Certificate[], String, TemporalAmount) check} of the certificate chain. The initial delay
     * is computed based on the earliest expiration time from among the supplied certificates and the supplied threshold.
     * If the threshold is null, then the initial delay corresponds to the earliest expiration. Otherwise, to the earliest
     * expiration minus the threshold.
     * 
     * @param certificates certificates to check
     * @param provenance added to the log message to help disambiguate which certificate is expiring
     * @param warningThreshold how far in advance of earliest expiration to do the initial check (null is equiv to zero)
     * @param interval delay between repeated checks after the initial delay (may be null to disable repeated checks)
     * @return {@link ScheduledFuture} which may be null if {@code certificates} is null or empty
     */
    public static ScheduledFuture<?> scheduleCheck(Certificate[] certificates, String provenance, Duration warningThreshold, Duration interval) {
        if (certificates == null || certificates.length == 0) {
            return null;
        }
        // this will throw an ArrayStoreException if any certificates are not X509Certificates, but this is quite a safe assumption
        X509Certificate[] x509Certificates = Arrays.copyOf(certificates, certificates.length, X509Certificate[].class);
        Runnable task = () -> {
            CertificateExpirationCheck.check(x509Certificates, provenance, warningThreshold);
        };
        // schedule first run just after the earliest-expiring cert becomes eligible for the warning
        Instant earliestExpiration = Stream.of(x509Certificates).map(X509Certificate::getNotAfter).sorted().findFirst().get().toInstant();
        Instant earliestRun = earliestExpiration.minus(warningThreshold == null ? Duration.ZERO : warningThreshold);
        Duration durationUntilEarliestRun = Duration.between(Instant.now(), earliestRun);
        Duration initialDelay = durationUntilEarliestRun.isNegative() ? Duration.ZERO : durationUntilEarliestRun;

        if (interval == null) {
            LOG.trace("Scheduling local certificate expiration check with delay {} (not repeated)", initialDelay);
            return SCHEDULER.schedule(task, initialDelay.getSeconds(), TimeUnit.SECONDS);
        } else {
            LOG.trace("Scheduling local certificate expiration check with delay {} (repeated with interval {})", initialDelay, interval);
            return SCHEDULER.scheduleAtFixedRate(task, initialDelay.getSeconds(), interval.getSeconds(), TimeUnit.SECONDS);
        }
    }
}
