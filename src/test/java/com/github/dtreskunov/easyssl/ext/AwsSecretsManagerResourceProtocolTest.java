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

import com.amazonaws.services.secretsmanager.AWSSecretsManager;
import com.amazonaws.services.secretsmanager.model.AWSSecretsManagerException;
import com.amazonaws.services.secretsmanager.model.GetSecretValueRequest;
import com.amazonaws.services.secretsmanager.model.GetSecretValueResult;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
public class AwsSecretsManagerResourceProtocolTest {
    @Configuration
    @Import(AwsSecretsManagerProtocolBeans.class)
    public static class TestConfig {
        @MockBean
        private AWSSecretsManager secretsManager;

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
    private AWSSecretsManager secretsManager;

    @Autowired
    @Qualifier("happyResource")
    private Resource happyResource;

    @Autowired
    @Qualifier("sadResource")
    private Resource sadResource;

    @Test
    public void testHappy() throws IOException {
        GetSecretValueRequest mockedRequest = new GetSecretValueRequest().withSecretId("happy");
        GetSecretValueResult mockedResult = new GetSecretValueResult().withSecretString("awesome");
        Mockito
            .when(secretsManager.getSecretValue(mockedRequest))
            .thenReturn(mockedResult);
        assertThat(
                StreamUtils.copyToString(happyResource.getInputStream(), Charset.defaultCharset()),
                is("awesome"));
    }

    @Test
    public void testSad() {
        GetSecretValueRequest mockedRequest = new GetSecretValueRequest().withSecretId("sad");
        Mockito
            .when(secretsManager.getSecretValue(mockedRequest))
            .thenThrow(new AWSSecretsManagerException("test"));
        assertThrows(AWSSecretsManagerException.class, () ->
            sadResource.getInputStream());
    }
}
