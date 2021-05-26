package com.github.dtreskunov.easyssl;

import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import com.github.dtreskunov.easyssl.spring.EasySslBeans;
import com.github.dtreskunov.easyssl.spring.EasySslProperties;

public class FileFormatTests {

    /**
     * openssl ecparam -genkey -name secp256r1 | openssl ec | openssl pkcs8 -out #{key} -topk8 -v1 PBE-SHA1-RC4-128 -passout pass:#{key_pass}
     */
    @Test
    public void testECEncryptedPKCS8() throws Exception {
        EasySslProperties revokedClientProperties = new EasySslProperties();
        revokedClientProperties.setCaCertificate(Arrays.asList(new ClassPathResource("/ssl/ca/cert.pem")));
        revokedClientProperties.setCertificate(new ClassPathResource("/ssl/ECEncryptedPKCS8/cert.pem"));
        revokedClientProperties.setKey(new ClassPathResource("/ssl/ECEncryptedPKCS8/key.pem"));
        revokedClientProperties.setKeyPassword("ECEncryptedPKCS8");
        EasySslBeans.getSSLContext(revokedClientProperties);
    }

    /**
     * openssl ecparam -genkey -name secp256r1 | openssl ec | openssl pkcs8 -out #{key} -topk8 -nocrypt
     */
    @Test
    public void testECPlainPKCS8() throws Exception {
        EasySslProperties revokedClientProperties = new EasySslProperties();
        revokedClientProperties.setCaCertificate(Arrays.asList(new ClassPathResource("/ssl/ca/cert.pem")));
        revokedClientProperties.setCertificate(new ClassPathResource("/ssl/ECPlainPKCS8/cert.pem"));
        revokedClientProperties.setKey(new ClassPathResource("/ssl/ECPlainPKCS8/key.pem"));
        EasySslBeans.getSSLContext(revokedClientProperties);
    }

    /**
     * openssl ecparam -genkey -name secp256r1 | openssl ec -out #{key} -aes128 -passout pass:#{key_pass}
     */
    @Test
    public void testECEncryptedOpenSsl() throws Exception {
        EasySslProperties revokedClientProperties = new EasySslProperties();
        revokedClientProperties.setCaCertificate(Arrays.asList(new ClassPathResource("/ssl/ca/cert.pem")));
        revokedClientProperties.setCertificate(new ClassPathResource("/ssl/ECEncryptedOpenSsl/cert.pem"));
        revokedClientProperties.setKey(new ClassPathResource("/ssl/ECEncryptedOpenSsl/key.pem"));
        revokedClientProperties.setKeyPassword("ECEncryptedOpenSsl");
        EasySslBeans.getSSLContext(revokedClientProperties);
    }

    /**
     * openssl ecparam -genkey -name secp256r1 | openssl ec -out #{key}
     */
    @Test
    public void testECPlainOpenSsl() throws Exception {
        EasySslProperties revokedClientProperties = new EasySslProperties();
        revokedClientProperties.setCaCertificate(Arrays.asList(new ClassPathResource("/ssl/ca/cert.pem")));
        revokedClientProperties.setCertificate(new ClassPathResource("/ssl/ECPlainOpenSsl/cert.pem"));
        revokedClientProperties.setKey(new ClassPathResource("/ssl/ECPlainOpenSsl/key.pem"));
        EasySslBeans.getSSLContext(revokedClientProperties);
    }

    /**
     * openssl ecparam -genkey -name secp256r1 | openssl ec -out #{key}
     */
    @Test
    public void testECPlainOpenSslChain() throws Exception {
        EasySslProperties revokedClientProperties = new EasySslProperties();
        revokedClientProperties.setCaCertificate(Arrays.asList(new ClassPathResource("/ssl/ca/cert.pem")));
        revokedClientProperties.setCertificate(new ClassPathResource("/ssl/ECPlainOpenSsl/cert_chain.pem"));
        revokedClientProperties.setKey(new ClassPathResource("/ssl/ECPlainOpenSsl/key.pem"));
        EasySslBeans.getSSLContext(revokedClientProperties);
    }
}
