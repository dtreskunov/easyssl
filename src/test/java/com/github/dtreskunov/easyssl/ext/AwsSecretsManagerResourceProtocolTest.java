package com.github.dtreskunov.easyssl.ext;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.charset.Charset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.util.StreamUtils;

import com.amazonaws.services.secretsmanager.AWSSecretsManager;
import com.amazonaws.services.secretsmanager.model.AWSSecretsManagerException;
import com.amazonaws.services.secretsmanager.model.GetSecretValueRequest;
import com.amazonaws.services.secretsmanager.model.GetSecretValueResult;

public abstract class AwsSecretsManagerResourceProtocolTest {

    @Test
    public void testHappy() throws IOException {
        assertThat(
            StreamUtils.copyToString(getHappyResource().getInputStream(), Charset.defaultCharset()),
            is("awesome"));
    }

    @Test
    public void testSad() {
        assertThrows(AWSSecretsManagerException.class, () ->
            getSadResource().getInputStream());
    }

    abstract Resource getHappyResource();
    abstract Resource getSadResource();

    static void setupHappy(AWSSecretsManager secretsManager) {
        GetSecretValueRequest mockedRequest = new GetSecretValueRequest().withSecretId("happy");
        GetSecretValueResult mockedResult = new GetSecretValueResult().withSecretString("awesome");
        Mockito
            .when(secretsManager.getSecretValue(mockedRequest))
            .thenReturn(mockedResult);
    }

    static void setupSad(AWSSecretsManager secretsManager) {
        GetSecretValueRequest mockedRequest = new GetSecretValueRequest().withSecretId("sad");
        Mockito
            .when(secretsManager.getSecretValue(mockedRequest))
            .thenThrow(new AWSSecretsManagerException("test"));
    }

    @ExtendWith(MockitoExtension.class)
    static class LightweightResourceLoaderTest extends AwsSecretsManagerResourceProtocolTest {

        DefaultResourceLoader resourceLoader;
    
        @Mock
        private AWSSecretsManager secretsManager;

        @BeforeEach
        void beforeEach() {
            resourceLoader = new StaticApplicationContext();
            resourceLoader.addProtocolResolver(new AwsSecretsManagerProtocolBeans.AwsSecretsManagerProtocolResolver(secretsManager));
        }
    
        @Override
        Resource getHappyResource() {
            setupHappy(secretsManager);
            return resourceLoader.getResource("aws-secrets-manager:happy");
        }

        @Override
        Resource getSadResource() {
            setupSad(secretsManager);
            return resourceLoader.getResource("aws-secrets-manager:sad");
        }
    }

    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
    static class IntegrationTest extends AwsSecretsManagerResourceProtocolTest {
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

        @Override
        Resource getHappyResource() {
            setupHappy(secretsManager);
            return happyResource;
        }

        @Override
        Resource getSadResource() {
            setupSad(secretsManager);
            return sadResource;
        }
    }
}
