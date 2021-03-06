package com.github.dtreskunov.easyssl;

import java.security.PublicKey;
import java.security.SignatureException;
import java.security.cert.CRLReason;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.CertificateRevokedException;
import java.security.cert.X509CRL;
import java.security.cert.X509CRLEntry;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.X509TrustManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.util.Assert;

/**
 * Verifies chains of {@link X509Certificate X.509 certificates} against the provided {@link X509CRL Certificate Revocation List} (CRL).
 * The CRL is specified as a Spring {@link Resource}, so it can be loaded from a remote URL. The CRL is checked for
 * {@link Resource#lastModified() last modified} timestamp and is refreshed accordingly. The signature on the CRL is verified against
 * some of provided {@link PublicKey}s (there may be several CAs).
 */
public class CRLTrustManager implements X509TrustManager {
    private static final Logger LOG = LoggerFactory.getLogger(CRLTrustManager.class);
    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor(
        ThreadFactoryFactory.createThreadFactory(true, CRLTrustManager.class.getSimpleName() + " daemon"));

    private X509CRL m_crl = null;
    private long m_crlLoadedTimestamp = -1;

    public CRLTrustManager(Resource crlResource, Collection<PublicKey> publicKeys, long timeout, long period, TimeUnit unit) throws Exception {
        Assert.notNull(crlResource, "crlResource may not be null");
        Assert.notNull(publicKeys, "publicKeys may not be null");
        Assert.notNull(unit, "unit may not be null");
        Assert.isTrue(timeout >= 0, "timeout must be greater than or equal to zero");
        Assert.isTrue(period == 0 || period > timeout, "period must be zero or greater than timeout");

        TimeoutUtils.Builder withTimeout = TimeoutUtils.builder().setName("Load CRL").setTimeout(timeout, unit);
        Runnable loadCRLTask = () -> {
            try {
                withTimeout.run(() -> {
                    try {
                        if (m_crl == null || m_crlLoadedTimestamp < crlResource.lastModified() || true) {
                            m_crl = loadCRL(crlResource, publicKeys);
                            m_crlLoadedTimestamp = System.currentTimeMillis();
                        }
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };

        // ensure any initial exception isn't ignored (as would happen if thrown in the executor thread)
        loadCRLTask.run();
        if (period > 0) {
            SCHEDULER.scheduleAtFixedRate(loadCRLTask, period, period, TimeUnit.SECONDS);
        }
    }

    private static X509CRL loadCRL(Resource resource, Collection<PublicKey> publicKeys) throws Exception {
        X509CRL crl = (X509CRL) CertificateFactory.getInstance("X.509").generateCRL(resource.getInputStream());
        for (PublicKey publicKey: publicKeys) {
            try {
                crl.verify(publicKey);
                LOG.info("Loaded CRL from {}", resource);
                return crl;
            } catch (Exception e) {
                LOG.debug("Unable to verify CRL from {} against a public key {} due to {}", resource, publicKey, e.toString());
                continue;
            }
        }
        throw new SignatureException("Unable to verify CRL against any provided public keys");
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        check(chain);
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        check(chain);
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return null;
    }

    private void check(X509Certificate[] chain) throws CertificateException {
        if (chain == null || chain.length == 0) {
            return;
        }
        if (m_crl == null) {
            throw new RuntimeException("Error loading CRL");
        }
        for (X509Certificate cert: chain) {
            X509CRLEntry revocation  = m_crl.getRevokedCertificate(cert);
            if (revocation != null) {
                throw new CertificateRevokedException(
                        Optional.ofNullable(revocation.getRevocationDate()).orElse(new Date()),
                        Optional.ofNullable(revocation.getRevocationReason()).orElse(CRLReason.UNSPECIFIED),
                        Optional.ofNullable(revocation.getCertificateIssuer()).orElse(m_crl.getIssuerX500Principal()),
                        Collections.emptyMap());
            }
        }
    }
}
