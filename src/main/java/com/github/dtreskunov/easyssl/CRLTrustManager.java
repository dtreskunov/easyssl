package com.github.dtreskunov.easyssl;

import java.security.InvalidKeyException;
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

import javax.net.ssl.X509TrustManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.util.Assert;

/**
 * Verifies chains of {@link X509Certificate X.509 certificates} against the provided {@link X509CRL Certificate Revocation List} (CRL).
 * The CRL is specified as a Spring {@link Resource}, so it can be loaded from a remote URL. The CRL is checked for
 * {@link Resource#lastModified() last modified} timestamp and is refreshed accordingly. The signature on the CRL is verified
 * against CA's {@link PublicKey}.
 */
public class CRLTrustManager implements X509TrustManager {
    private final Logger m_log = LoggerFactory.getLogger(getClass());
    private final Resource m_crlResource;
    private final Collection<PublicKey> m_publicKeys;
    private Optional<X509CRL> m_crl = Optional.empty();
    private long m_lastRefreshed = -1;

    public CRLTrustManager(Resource crlResource, Collection<PublicKey> publicKeys) {
        Assert.notNull(publicKeys, "publicKeys may not be null");
        m_crlResource = crlResource;
        m_publicKeys = publicKeys;
    }

    private static X509CRL loadCRL(Resource resource, Collection<PublicKey> publicKeys) throws Exception {
        X509CRL crl = (X509CRL) CertificateFactory.getInstance("X.509").generateCRL(resource.getInputStream());
        for (PublicKey publicKey: publicKeys) {
            try {
                crl.verify(publicKey);
                return crl;
            } catch (InvalidKeyException e) {
                continue;
            }
        }
        throw new SignatureException("Unable to verify CRL against any provided public keys");
    }

    private synchronized Optional<X509CRL> getCRL() throws Exception {
        if (m_crlResource == null || !m_crlResource.exists()) {
            return Optional.empty();
        }
        long lastModified = m_crlResource.lastModified();
        if (!m_crl.isPresent() || lastModified > m_lastRefreshed) {
            m_crl = Optional.of(loadCRL(m_crlResource, m_publicKeys));
            m_lastRefreshed = lastModified;
            m_log.info("Loaded CRL from {}", m_crlResource);
        }
        return m_crl;
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
        try {
            m_crl = getCRL();
        } catch (Exception e) {
            throw new RuntimeException("Error getting CRL", e);
        }
        if (!m_crl.isPresent()) {
            return;
        }
        for (X509Certificate cert: chain) {
            X509CRLEntry revocation  = m_crl.get().getRevokedCertificate(cert);
            if (revocation == null) {
                continue;
            }
            throw new CertificateRevokedException(
                    Optional.ofNullable(revocation.getRevocationDate()).orElse(new Date()),
                    Optional.ofNullable(revocation.getRevocationReason()).orElse(CRLReason.UNSPECIFIED),
                    Optional.ofNullable(revocation.getCertificateIssuer()).orElse(m_crl.get().getIssuerX500Principal()),
                    Collections.emptyMap());
        }
    }
}
