package com.github.dtreskunov.easyssl;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import javax.net.ssl.SSLContext;

import com.github.dtreskunov.easyssl.server.Server;

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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@SpringBootTest(properties = {"spring.profiles.active=another_ca"}, classes = {Server.class}, webEnvironment = WebEnvironment.RANDOM_PORT)
class IntegrationTestUsingRealServerAnotherCA {
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
        assertThat(response.getBody(), is("CN=localhost, OU=iss_by_another_ca"));
    }
}
