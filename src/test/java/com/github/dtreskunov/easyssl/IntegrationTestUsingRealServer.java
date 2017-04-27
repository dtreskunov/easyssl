package com.github.dtreskunov.easyssl;

import java.util.Arrays;

import javax.net.ssl.SSLContext;

import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.embedded.LocalServerPort;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import com.github.dtreskunov.easyssl.server.Server;
import com.github.dtreskunov.easyssl.spring.EasySslBeans;
import com.github.dtreskunov.easyssl.spring.EasySslProperties;

@RunWith(SpringRunner.class)
@SpringBootTest(properties = {"spring.profiles.active=test"}, classes = {Server.class}, webEnvironment = WebEnvironment.RANDOM_PORT)
public class IntegrationTestUsingRealServer {

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Autowired
    @LocalServerPort
    private int port;

    @Autowired
    SSLContext sslContext;

    private RestTemplate restTemplate;

    private RestTemplate getRestTemplate(SSLContext sslContext) throws Exception {
        HttpClient httpClient = HttpClientBuilder.create().setSSLContext(sslContext).build();
        HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory(httpClient);
        return new RestTemplateBuilder().rootUri("https://localhost:" + port).requestFactory(requestFactory).build();
    }

    @Before
    public void setup() throws Exception {
        restTemplate = getRestTemplate(sslContext);
    }

    @Test
    public void happyCase() throws Exception {
        ResponseEntity<String> response = restTemplate.getForEntity("/whoami", String.class);
        Assert.assertThat(response.getStatusCode(), Matchers.is(HttpStatus.OK));
    }

    @Test
    public void serverRejectsRevokedClient() throws Exception {
        EasySslProperties revokedClientProperties = new EasySslProperties();
        revokedClientProperties.setCaCertificate(Arrays.asList(new ClassPathResource("/ssl/ca/cert.pem")));
        revokedClientProperties.setCertificate(new ClassPathResource("/ssl/localhost2/cert.pem"));
        revokedClientProperties.setKey(new ClassPathResource("/ssl/localhost2/key.pem"));
        RestTemplate revokedClientRestTemplate = getRestTemplate(EasySslBeans.getSSLContext(revokedClientProperties));

        thrown.expect(HttpClientErrorException.class);
        thrown.expectMessage("403");

        revokedClientRestTemplate.getForEntity("/", String.class);
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

        thrown.expect(HttpClientErrorException.class);
        thrown.expectMessage("403");

        untrustedClientRestTemplate.getForEntity("/whoami", String.class);
    }

    @Test
    public void clientRejectsUntrustedServer() throws Exception {
        EasySslProperties untrustedServerProperties = new EasySslProperties();
        untrustedServerProperties.setCaCertificate(Arrays.asList(new ClassPathResource("/ssl/fake_ca/cert.pem")));
        untrustedServerProperties.setCertificate(new ClassPathResource("/ssl/localhost1/cert.pem"));
        untrustedServerProperties.setKey(new ClassPathResource("/ssl/localhost1/key.pem"));
        untrustedServerProperties.setKeyPassword("localhost1-password");
        RestTemplate revokedRestTemplate = getRestTemplate(EasySslBeans.getSSLContext(untrustedServerProperties));

        thrown.expect(ResourceAccessException.class);

        revokedRestTemplate.getForEntity("/", String.class);
    }
}
