package com.github.dtreskunov.easyssl;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;

import javax.net.ssl.SSLContext;

import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import com.github.dtreskunov.easyssl.server.Server;
import com.github.dtreskunov.easyssl.spring.EasySslBeans;
import com.github.dtreskunov.easyssl.spring.EasySslProperties;

@SpringBootTest(properties = {"spring.profiles.active=test"}, classes = {Server.class}, webEnvironment = WebEnvironment.RANDOM_PORT)
public class IntegrationTestUsingRealServer {

    @Autowired
    @LocalServerPort
    private int port;

    @Value("${local.server.protocol}")
    private String protocol; // injected by EasySSL

    @Autowired
    SSLContext sslContext;

    private RestTemplate restTemplate;

    private RestTemplate getRestTemplate(SSLContext sslContext) throws Exception {
        HttpClient httpClient = HttpClientBuilder.create().setSSLContext(sslContext).build();
        return new RestTemplateBuilder()
                .rootUri(protocol + "://localhost:" + port)
                .requestFactory(() -> new HttpComponentsClientHttpRequestFactory(httpClient))
                .build();
    }

    @BeforeEach
    public void setup() throws Exception {
        assertThat(protocol, is("https"));
        restTemplate = getRestTemplate(sslContext);
    }

    @Test
    public void happyCase() throws Exception {
        ResponseEntity<String> response = restTemplate.getForEntity("/whoami", String.class);
        assertThat(response.getStatusCode(), is(HttpStatus.OK));
    }

    @Test
    public void happyCase_alternative() throws Exception {
        EasySslProperties clientProperties = new EasySslProperties();
        clientProperties.setCaCertificate(Arrays.asList(new ClassPathResource("/ssl/ca/cert.pem")));
        clientProperties.setCertificate(new ClassPathResource("/ssl/ECEncryptedOpenSsl/cert.pem"));
        clientProperties.setKey(new ClassPathResource("/ssl/ECEncryptedOpenSsl/key.pem"));
        clientProperties.setKeyPassword("ECEncryptedOpenSsl");
        RestTemplate revokedClientRestTemplate = getRestTemplate(EasySslBeans.getSSLContext(clientProperties));
        ResponseEntity<String> response = revokedClientRestTemplate.getForEntity("/whoami", String.class);
        assertThat(response.getStatusCode(), is(HttpStatus.OK));
    }

    @Test
    public void serverRejectsRevokedClient() throws Exception {
        EasySslProperties revokedClientProperties = new EasySslProperties();
        revokedClientProperties.setCaCertificate(Arrays.asList(new ClassPathResource("/ssl/ca/cert.pem")));
        revokedClientProperties.setCertificate(new ClassPathResource("/ssl/localhost2/cert.pem"));
        revokedClientProperties.setKey(new ClassPathResource("/ssl/localhost2/key.pem"));
        revokedClientProperties.setKeyPassword("localhost2-password");
        RestTemplate revokedClientRestTemplate = getRestTemplate(EasySslBeans.getSSLContext(revokedClientProperties));

        HttpClientErrorException exception = assertThrows(HttpClientErrorException.class, () ->
            revokedClientRestTemplate.getForEntity("/", String.class));
        assertThat(exception.getMessage(), containsString("403"));
    }

    @Test
    public void serverRejectsUntrustedClient() throws Exception {
        EasySslProperties untrustedClientProperties = new EasySslProperties();
        untrustedClientProperties.setCaCertificate(Arrays.asList(new ClassPathResource("/ssl/ca/cert.pem")));
        untrustedClientProperties.setCertificate(new ClassPathResource("/ssl/fake_localhost1/cert.pem"));
        untrustedClientProperties.setKey(new ClassPathResource("/ssl/fake_localhost1/key.pem"));
        RestTemplate untrustedClientRestTemplate = getRestTemplate(EasySslBeans.getSSLContext(untrustedClientProperties));

        // This appears to work since we have ClientAuth.WANT (not ClientAuth.NEED) and the "/" endpoint is not protected
        //
        // But curl doesn't work:
        // curl --cacert ssl/ca/cert.pem --cert ssl/fake_localhost1/cert.pem --key ssl/fake_localhost1/key.pem https://localhost:8443/
        // curl: (35) error:14094416:SSL routines:SSL3_READ_BYTES:sslv3 alert certificate unknown
        untrustedClientRestTemplate.getForEntity("/", String.class);

        HttpClientErrorException exception = assertThrows(HttpClientErrorException.class, () ->
            untrustedClientRestTemplate.getForEntity("/whoami", String.class));
        assertThat(exception.getMessage(), containsString("403"));
    }

    @Test
    public void clientRejectsUntrustedServer() throws Exception {
        EasySslProperties untrustedServerProperties = new EasySslProperties();
        untrustedServerProperties.setCaCertificate(Arrays.asList(new ClassPathResource("/ssl/fake_ca/cert.pem")));
        untrustedServerProperties.setCertificate(new ClassPathResource("/ssl/localhost1/cert.pem"));
        untrustedServerProperties.setKey(new ClassPathResource("/ssl/localhost1/key.pem"));
        untrustedServerProperties.setKeyPassword("localhost1-password");
        RestTemplate revokedRestTemplate = getRestTemplate(EasySslBeans.getSSLContext(untrustedServerProperties));

        assertThrows(ResourceAccessException.class, () ->
            revokedRestTemplate.getForEntity("/", String.class));
    }
}
