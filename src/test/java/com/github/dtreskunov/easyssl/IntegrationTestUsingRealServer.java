package com.github.dtreskunov.easyssl;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.cert.X509Certificate;
import java.util.Arrays;

import javax.net.ssl.SSLContext;

import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.DefaultClientTlsStrategy;
import org.apache.hc.client5.http.ssl.HostnameVerificationPolicy;
import org.apache.hc.client5.http.ssl.HttpsSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import com.github.dtreskunov.easyssl.server.Server;

@SpringBootTest(properties = {"spring.profiles.active=test"}, classes = {Server.class}, webEnvironment = WebEnvironment.RANDOM_PORT)
public class IntegrationTestUsingRealServer {
    private static final Path LOCALHOST1 = Path.of("src/test/resources/ssl/localhost1");
    private static final Path LOCALHOST2 = Path.of("src/test/resources/ssl/localhost2");

    @Autowired
    @LocalServerPort
    private int port;

    @Value("${local.server.protocol}")
    private String protocol; // injected by EasySSL

    @Autowired
    SSLContext sslContext;

    @Autowired
    EasySslHelper easySslHelper;

    private RestTemplate restTemplate;

    private RestTemplate getRestTemplate(EasySslHelper easySslHelper) throws Exception {
        DefaultClientTlsStrategy s = new DefaultClientTlsStrategy(
            easySslHelper.getSSLContext(),
            HostnameVerificationPolicy.BOTH,
            HttpsSupport.getDefaultHostnameVerifier());
        
        PoolingHttpClientConnectionManager connectionManager = PoolingHttpClientConnectionManagerBuilder.create().setTlsSocketStrategy(s).build();

        HttpClient httpClient = HttpClients.custom()
            .setConnectionManager(connectionManager)
            .build();

        return new RestTemplateBuilder()
                .rootUri(protocol + "://localhost:" + port)
                .requestFactory(() -> new HttpComponentsClientHttpRequestFactory(httpClient))
                .build();
    }

    @BeforeEach
    public void setup() throws Exception {
        assertThat(protocol, is("https"));
        restTemplate = getRestTemplate(easySslHelper);
    }

    @Test
    public void happyCase() throws Exception {
        ResponseEntity<String> response = restTemplate.getForEntity("/whoami", String.class);
        assertThat(response.getStatusCode(), is(HttpStatus.OK));
        assertThat(response.getBody(), is("CN=localhost, OU=Localhost1"));
    }

    @Test
    public void happyCase_updateCertificate() throws Exception {
        X509Certificate[] originalServerCertificates = ServerCertificateChainGetter.getServerCertificateChain("localhost", port);
        assertThat(originalServerCertificates[0].getSubjectX500Principal().toString(), is("CN=localhost, OU=Localhost1"));

        ResponseEntity<String> response = restTemplate.getForEntity("/whoami", String.class);
        assertThat(response.getStatusCode(), is(HttpStatus.OK));
        assertThat(response.getBody(), is("CN=localhost, OU=Localhost1"));

        swapPaths(LOCALHOST1, LOCALHOST2);

        try {
            easySslHelper.reinitialize();

            X509Certificate[] updatedServerCertificates = ServerCertificateChainGetter.getServerCertificateChain("localhost", port);
            assertThat("server did not automatically load the updated cert", updatedServerCertificates[0].getSubjectX500Principal().toString(), is("CN=localhost, OU=Localhost2"));

            response = restTemplate.getForEntity("/whoami", String.class);
            assertThat(response.getStatusCode(), is(HttpStatus.OK));
            assertThat("client did not automatically load the updated cert", response.getBody(), is("CN=localhost, OU=Localhost2"));
        } finally {
            swapPaths(LOCALHOST1, LOCALHOST2);
        }
    }

    @Test
    @Disabled
    public void reinitialize_soakTest() throws Exception {
        for (int i=0; i<1000; i++) {
            easySslHelper.reinitialize();
        };
    }

    @Test
    public void happyCase_alternative() throws Exception {
        EasySslProperties clientProperties = new EasySslProperties();
        clientProperties.setCaCertificate(Arrays.asList(new ClassPathResource("/ssl/ca/cert.pem")));
        clientProperties.setCertificate(new ClassPathResource("/ssl/ECEncryptedOpenSsl/cert.pem"));
        clientProperties.setKey(new ClassPathResource("/ssl/ECEncryptedOpenSsl/key.pem"));
        clientProperties.setKeyPassword("ECEncryptedOpenSsl");
        RestTemplate revokedClientRestTemplate = getRestTemplate(new EasySslHelper(clientProperties));
        ResponseEntity<String> response = revokedClientRestTemplate.getForEntity("/whoami", String.class);
        assertThat(response.getStatusCode(), is(HttpStatus.OK));
        assertThat(response.getBody(), is("CN=ECEncryptedOpenSsl"));
    }

    @Test
    public void happyCase_anotherCA() throws Exception {
        EasySslProperties clientProperties = new EasySslProperties();
        clientProperties.setCaCertificate(Arrays.asList(new ClassPathResource("/ssl/cacerts.pem")));
        clientProperties.setCertificate(new ClassPathResource("/ssl/iss_by_another_ca/cert.pem"));
        clientProperties.setKey(new ClassPathResource("/ssl/iss_by_another_ca/key.pem"));
        RestTemplate revokedClientRestTemplate = getRestTemplate(new EasySslHelper(clientProperties));
        ResponseEntity<String> response = revokedClientRestTemplate.getForEntity("/whoami", String.class);
        assertThat(response.getStatusCode(), is(HttpStatus.OK));
        assertThat(response.getBody(), is("CN=localhost, OU=iss_by_another_ca"));
    }

    @Test
    public void serverRejectsRevokedClient() throws Exception {
        EasySslProperties revokedClientProperties = new EasySslProperties();
        revokedClientProperties.setCaCertificate(Arrays.asList(new ClassPathResource("/ssl/ca/cert.pem")));
        revokedClientProperties.setCertificate(new ClassPathResource("/ssl/revoked_localhost/cert.pem"));
        revokedClientProperties.setKey(new ClassPathResource("/ssl/revoked_localhost/key.pem"));
        revokedClientProperties.setKeyPassword("localhost-password");
        RestTemplate revokedClientRestTemplate = getRestTemplate(new EasySslHelper(revokedClientProperties));

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
        RestTemplate untrustedClientRestTemplate = getRestTemplate(new EasySslHelper(untrustedClientProperties));

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
        untrustedServerProperties.setKeyPassword("localhost-password");
        RestTemplate revokedRestTemplate = getRestTemplate(new EasySslHelper(untrustedServerProperties));

        assertThrows(ResourceAccessException.class, () ->
                revokedRestTemplate.getForEntity("/", String.class));
    }

    private void swapPaths(Path path1, Path path2) throws IOException {
        Path tempPath = Files.createTempDirectory(getClass().getSimpleName());
        Files.move(path1, tempPath, StandardCopyOption.REPLACE_EXISTING);
        Files.move(path2, path1);
        Files.move(tempPath, path2);
    }
}
