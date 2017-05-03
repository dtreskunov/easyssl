package com.github.dtreskunov.easyssl.spring;

import java.util.List;

import javax.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;

/**
 * Settings that define the application's SSL identity.
 *
 * <pre>
 * # In application.yml
 * easyssl:
 *   caCertificate:             classpath:ca.pem # also accepts arrays
 *   certificate:               file:cert.pem
 *   key:                       file:key.pem
 *   keyPassword:               secret
 *   certificateRevocationList: http://ca/crl.pem
 *   # If the servlet container (if any) should NOT be configured to use SSL:
 *   # serverCustomizationEnabled: false
 *   # If auto-configuration should NOT be enabled:
 *   # enabled: false
 * </pre>
 */
@ConfigurationProperties(prefix = "easyssl")
public class EasySslProperties {

    @NotNull
    private List<Resource> m_caCertificate;

    @NotNull
    private Resource m_certificate;

    private Resource m_certificateRevocationList;

    @NotNull
    private Resource m_key;

    private String m_keyPassword;

    private boolean m_enabled = true;
    private boolean m_serverCustomizationEnabled = true;

    /**
     * @return Certificate Authority's (CA) certificate(s), used for validating client and server certificates, and the signature on the CRL.
     */
    public List<Resource> getCaCertificate() {
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

    /**
     * @return Password used to decrypt the private key.
     */
    public String getKeyPassword() {
        return m_keyPassword;
    }

    /**
     * @return Whether any {@code @Bean}s should be injected at all.
     */
    public boolean isEnabled() {
        return m_enabled;
    }
    /**
     * @return Whether the servlet container customization should be enabled.
     */
    public boolean isServerCustomizationEnabled() {
        return m_serverCustomizationEnabled;
    }

    public void setCaCertificate(List<Resource> caCertificate) {
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
    public void setKeyPassword(String keyPassword) {
        m_keyPassword = keyPassword;
    }
    public void setEnabled(boolean enabled) {
        m_enabled = enabled;
    }
    public void setServerCustomizationEnabled(boolean serverCustomizationEnabled) {
        m_serverCustomizationEnabled = serverCustomizationEnabled;
    }
}
