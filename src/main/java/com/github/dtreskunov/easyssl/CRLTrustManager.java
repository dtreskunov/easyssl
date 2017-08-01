package com.github.dtreskunov.easyssl;

import java.io.IOException;
import java.security.PublicKey;
import java.security.SignatureException;
import java.security.cert.CRLReason;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.CertificateRevokedException;
import java.security.cert.X509CRL;
import java.security.cert.X509CRLEntry;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.TemporalAmount;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Optional;

import javax.net.ssl.X509TrustManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.util.Assert;

/**
 * Verifies chains of {@link X509Certificate X.509 certificates} against the provided {@link X509CRL Certificate Revocation List} (CRL).
 * The CRL is specified as a Spring {@link Resource}, so it can be loaded from a remote URL. The CRL is checked for
 * {@link Resource#lastModified() last modified} timestamp and is refreshed accordingly. This check is skipped if the last refresh was
 * within {@code crlResourceCheckInterval} from now. The signature on the CRL is verified against some of provided {@link PublicKey}s
 * (there may be several CAs).
 */
public class CRLTrustManager implements X509TrustManager {
    private static final Logger LOG = LoggerFactory.getLogger(CRLTrustManager.class);
    private final Resource m_crlResource;
    private final TemporalAmount m_crlResourceCheckInterval;
    private final Collection<PublicKey> m_publicKeys;

    private X509CRL m_crl = null;
    private Instant m_lastLoaded = null;

    public CRLTrustManager(Resource crlResource, TemporalAmount crlResourceCheckInterval, Collection<PublicKey> publicKeys) {
        Assert.notNull(crlResourceCheckInterval, "crlResourceCheckInterval may not be null");
        Assert.notNull(publicKeys, "publicKeys may not be null");
        m_crlResource = crlResource;
        m_crlResourceCheckInterval = crlResourceCheckInterval;
        m_publicKeys = publicKeys;
    }

    private static X509CRL loadCRL(Resource resource, Collection<PublicKey> publicKeys) throws Exception {
        X509CRL crl = (X509CRL) CertificateFactory.getInstance("X.509").generateCRL(resource.getInputStream());
        for (PublicKey publicKey: publicKeys) {
            try {
                crl.verify(publicKey);
                LOG.info("Loaded CRL from {}", resource);
                return crl;
            } catch (Exception e) {
                LOG.debug("Unable to verify CRL against provided public key", e);
                continue;
            }
        }
        throw new SignatureException("Unable to verify CRL against any provided public keys");
    }

    private boolean shouldLoadCRL() throws IOException {
        if (m_lastLoaded == null) {
            return true;
        }
        if (Instant.now().isAfter(m_lastLoaded.plus(m_crlResourceCheckInterval)) &&
                Instant.ofEpochMilli(m_crlResource.lastModified()).isAfter(m_lastLoaded)) {
            return true;
        }
        return false;
    }

    private synchronized void maybeLoadCRL() throws Exception {
        if (!shouldLoadCRL()) {
            return;
        }
        m_crl = loadCRL(m_crlResource, m_publicKeys);
        m_lastLoaded = Instant.now();
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
        if (chain == null || chain.length == 0 || m_crlResource == null) {
            return;
        }
        try {
            maybeLoadCRL();
        } catch (Exception e) {
            throw new RuntimeException("Error loading CRL", e);
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
