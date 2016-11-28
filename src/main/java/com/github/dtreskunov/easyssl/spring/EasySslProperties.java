package com.github.dtreskunov.easyssl.spring;

import javax.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * Settings that define the application's SSL identity.
 * 
 * <pre>
 * # In application.yml
 * easyssl:
 *   caCertificate:             classpath:ca.pem
 *   certificate:               file:cert.pem
 *   key:                       file:key.pem
 *   certificateRevocationList: http://ca/crl.pem
 * </pre>
 */
@Component
@ConfigurationProperties(prefix = "easyssl")
public class EasySslProperties {
    
    @NotNull
    private Resource m_caCertificate;
    
    @NotNull
    private Resource m_certificate;
    
    private Resource m_certificateRevocationList;
    
    @NotNull
    private Resource m_key;
    
    /**
     * @return Certificate Authority's (CA) certificate, used for validating client and server certificates, and the signature on the CRL.
     */
    public Resource getCaCertificate() {
        return m_caCertificate;
    }
    
    /**
     * @return The application's SSL certificate, used for establishing a secure connection to client or server.
     */
    public Resource getCertificate() {
        return m_certificate;
    }
    
    /**
     * @return Certificate revocation list, used for checking validity of certificates presented to this application by client or server.
     */
    public Resource getCertificateRevocationList() {
        return m_certificateRevocationList;
    }
    
    /**
     * @return The key associated with the application's SSL certificate, used as a proof of ownership of the certificate.
     */
    public Resource getKey() {
        return m_key;
    }
    
    public void setCaCertificate(Resource caCertificate) {
        m_caCertificate = caCertificate;
    }
    public void setCertificate(Resource certificate) {
        m_certificate = certificate;
    }
    public void setCertificateRevocationList(Resource certificateRevocationList) {
        m_certificateRevocationList = certificateRevocationList;
    }
    public void setKey(Resource key) {
        m_key = key;
    }
}