package name.treskunov.denis.easyssl;

import java.io.IOException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.X509TrustManager;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.GenericFilterBean;

/**
 * A servlet filter that responds with a {@link HttpStatus#FORBIDDEN 403 Forbidden} when the provided
 * {@link X509Certificate client certificate} is not trusted according to the provided {@link X509TrustManager} - for example,
 * if the certificate has been revoked.
 * <p>
 * This class is only necessary because Spring Boot doesn't (yet) expose underlying servlet containers' CRL/OCSP features -
 * see <a href="https://github.com/spring-projects/spring-boot/issues/6171">SPRING-BOOT 6171</a>. This means that connections
 * from revoked clients will still be accepted by the server, however, request handler logic won't be reached.
 */
public class ClientCertificateCheckingFilter extends GenericFilterBean {
    
    private static final String REQUEST_ATTRIBUTE_X509_CERTIFICATE = "javax.servlet.request.X509Certificate";
    private final Logger m_log = LoggerFactory.getLogger(getClass());
    private final X509TrustManager m_trustManager;
    
    public ClientCertificateCheckingFilter(X509TrustManager trustManager) {
        super();
        m_trustManager = trustManager;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        try {
            m_trustManager.checkClientTrusted((X509Certificate[]) request
                    .getAttribute(REQUEST_ATTRIBUTE_X509_CERTIFICATE), null);
        } catch (CertificateException e) {
            m_log.warn("There is a problem with the client certificate: " + e.getMessage());
            ((HttpServletResponse)response).sendError(HttpStatus.FORBIDDEN.value(), "There is a problem with the client certificate");
            return;
        } catch (Throwable t) {
            m_log.error("Unable to verify certificate status (will proceed)", t);
        }
        chain.doFilter(request, response);
    }
}
