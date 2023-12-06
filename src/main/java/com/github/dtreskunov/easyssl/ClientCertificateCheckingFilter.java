package com.github.dtreskunov.easyssl;

import java.io.IOException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.X509TrustManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.server.Ssl.ClientAuth;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.GenericFilterBean;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;

/**
 * A servlet filter that responds with a {@link HttpStatus#FORBIDDEN 403 Forbidden} when the provided
 * {@link X509Certificate client certificate} is not trusted according to the provided {@link X509TrustManager} - for example,
 * if the certificate has been revoked.
 * <p><strong>If no client certificate is associated with the request, the request is considered valid.</strong>
 * If such requests should be rejected, you must instruct the servlet container to require certificates (i.e. use Spring Boot's
 * {@link ClientAuth#NEED}).
 * <p>
 * This class is only necessary because Spring Boot doesn't (yet) expose underlying servlet containers' CRL/OCSP features -
 * see <a href="https://github.com/spring-projects/spring-boot/issues/6171">SPRING-BOOT 6171</a>. This means that connections
 * from revoked clients will still be accepted by the server, however, request handler logic won't be reached.
 */
class ClientCertificateCheckingFilter extends GenericFilterBean {

    private static final String REQUEST_ATTRIBUTE_X509_CERTIFICATE = "javax.servlet.request.X509Certificate";
    private final Logger m_log = LoggerFactory.getLogger(getClass());
    private final X509TrustManager m_trustManager;

    public ClientCertificateCheckingFilter(X509TrustManager trustManager) {
        super();
        m_trustManager = trustManager;
    }

    private void checkClientCertificate(ServletRequest request) throws CertificateException {
        X509Certificate[] certChain = (X509Certificate[]) request.getAttribute(REQUEST_ATTRIBUTE_X509_CERTIFICATE);
        if (certChain == null || certChain.length == 0) {
            m_log.trace("No client certificate provided - not checking validity");
        } else {
            String authType = certChain[0].getPublicKey().getAlgorithm(); // should be "RSA"
            m_trustManager.checkClientTrusted(certChain, authType);
            m_log.trace("Request did include a trusted client certificate");
        }
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        try {
            checkClientCertificate(request);
        } catch (CertificateException e) {
            m_log.warn("Request included an untrusted client certificate: " + e.getMessage());
            ((HttpServletResponse)response).sendError(HttpStatus.FORBIDDEN.value(), "Client certificate invalid");
            return;
        }
        chain.doFilter(request, response);
    }
}
