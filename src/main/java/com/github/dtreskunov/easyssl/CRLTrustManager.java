package com.github.dtreskunov.easyssl;

import java.security.PublicKey;
import java.security.cert.CRLReason;
import java.security.cert.CertificateException;
import java.security.cert.CertificateRevokedException;
import java.security.cert.X509CRL;
import java.security.cert.X509CRLEntry;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.Date;
import java.util.Optional;

import javax.net.ssl.X509TrustManager;

import org.springframework.core.io.Resource;
import org.springframework.util.Assert;

/**
 * Verifies chains of {@link X509Certificate X.509 certificates} against the provided {@link X509CRL Certificate Revocation List} (CRL).
 * The CRL is specified as a Spring {@link Resource}, so it can be loaded from a remote URL. The CRL is checked for
 * {@link Resource#lastModified() last modified} timestamp and is refreshed accordingly. The signature on the CRL is verified against
 * some of provided {@link PublicKey}s (there may be several CAs).
 */
class CRLTrustManager implements X509TrustManager {
    private final X509CRL m_crl;

    CRLTrustManager(X509CRL crl) throws Exception {
        Assert.notNull(crl, "crl may not be null");
        m_crl = crl;
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
