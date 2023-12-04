package com.github.dtreskunov.easyssl.ext;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.charset.Charset;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.Resource;
import org.springframework.util.StreamUtils;

import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import software.amazon.awssdk.services.secretsmanager.model.SecretsManagerException;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
public class AwsSecretsManagerResourceProtocolTest {
    @Configuration
    @Import(AwsSecretsManagerProtocolBeans.class)
    public static class TestConfig {
        @MockBean
        private SecretsManagerClient secretsClient;

        @Bean
        public Resource happyResource(@Value("aws-secrets-manager:happy") Resource resource) {
            return resource;
        }
        @Bean
        public Resource sadResource(@Value("aws-secrets-manager:sad") Resource resource) {
            return resource;
        }
    }

    @Autowired
    private SecretsManagerClient secretsClient;

    @Autowired
    @Qualifier("happyResource")
    private Resource happyResource;

    @Autowired
    @Qualifier("sadResource")
    private Resource sadResource;

    @Test
    public void testHappy() throws IOException {
        GetSecretValueRequest mockedRequest = GetSecretValueRequest.builder().secretId("happy").build();
        GetSecretValueResponse mockedResponse = GetSecretValueResponse.builder().secretString("awesome").build();
        Mockito
            .when(secretsClient.getSecretValue(mockedRequest))
            .thenReturn(mockedResponse);
        assertThat(
                StreamUtils.copyToString(happyResource.getInputStream(), Charset.defaultCharset()),
                is("awesome"));
    }

    @Test
    public void testSad() {
        GetSecretValueRequest mockedRequest = GetSecretValueRequest.builder().secretId("sad").build();
        Mockito
            .when(secretsClient.getSecretValue(mockedRequest))
            .thenThrow(SecretsManagerException.builder().build());
        assertThrows(SecretsManagerException.class, () ->
            sadResource.getInputStream());
    }
}
