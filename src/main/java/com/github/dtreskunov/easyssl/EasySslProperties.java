package com.github.dtreskunov.easyssl;

import java.time.Duration;
import java.util.List;

import javax.validation.constraints.NotNull;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.web.server.Ssl.ClientAuth;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.validation.annotation.Validated;

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
 *   refreshInterval: 60s
 *   # If the servlet container (if any) should NOT be configured to use SSL:
 *   # serverCustomizationEnabled: false
 *   # If auto-configuration should NOT be enabled:
 *   # enabled: false
 * </pre>
 */
@Validated
@ConfigurationProperties(prefix = "easyssl")
public class EasySslProperties {

    @Configuration
    @ConditionalOnProperty(value = "easyssl.enabled", matchIfMissing = true)
    static class EasySslPropertiesConfiguration {
        @Bean
        EasySslProperties easySslProperties() {
            return new EasySslProperties();
        }

    }

    @NotNull
    private List<Resource> m_caCertificate;

    @NotNull
    private Resource m_certificate;

    @NotNull
    private Duration m_refreshInterval = Duration.ZERO;
    
    private Duration m_refreshTimeout = Duration.ZERO;

    private List<String> m_refreshCommand;

    private Resource m_certificateRevocationList;

    @NotNull
    private Resource m_key;

    private String m_keyPassword;

    private boolean m_enabled = true;
    private boolean m_serverCustomizationEnabled = true;
    private ClientAuth m_clientAuth = ClientAuth.NEED;
    private Duration m_certificateExpirationWarningThreshold;
    private Duration m_certificateExpirationCheckInterval;

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
     * @return How often the {@link Resource} properties should be refreshed (defaults to "once at startup"): key, cert, CA cert, and CRL.
     */
    public Duration getRefreshInterval() {
        return m_refreshInterval;
    }

    /**
     * @return Timeout on refreshing the {@link Resource} properties (defaults to "no timeout")
     */
    public Duration getRefreshTimeout() {
        return m_refreshTimeout;
    }

    /**
     * @return Command to run before refreshing resources.
     */
    public List<String> getRefreshCommand() {
        return m_refreshCommand;
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

    /**
     * @return Whether requests made to this server should be immediately rejected if the client did not include a certificate.
     * See {@link ClientAuth}.
     */
    public ClientAuth getClientAuth() {
        return m_clientAuth;
    }

    /**
     * @return How far in advance of client/server certificate expiration to log a warning.
     */
    public Duration getCertificateExpirationWarningThreshold() {
        return m_certificateExpirationWarningThreshold;
    }

    /**
     * @return How frequently to check the local certificate for expiration.
     */
    public Duration getCertificateExpirationCheckInterval() {
        return m_certificateExpirationCheckInterval;
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
    public void setRefreshInterval(Duration refreshInterval) {
        m_refreshInterval = refreshInterval;
    }
    public void setRefreshTimeout(Duration refreshTimeout) {
        m_refreshTimeout = refreshTimeout;
    }
    public void setRefreshCommand(List<String> refreshCommand) {
        m_refreshCommand = refreshCommand;
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
    public void setClientAuth(ClientAuth clientAuth) {
        m_clientAuth = clientAuth;
    }
    public void setCertificateExpirationWarningThreshold(Duration certificateExpirationWarningThreshold) {
        m_certificateExpirationWarningThreshold = certificateExpirationWarningThreshold;
    }
    public void setCertificateExpirationCheckInterval(Duration certificateExpirationCheckInterval) {
        m_certificateExpirationCheckInterval = certificateExpirationCheckInterval;
    }
}
