package com.github.dtreskunov.easyssl.ext;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.Charset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.util.StreamUtils;

public abstract class RefResourceProtocolTest {

    @Test
    public void testHappy() throws IOException {
        assertThat(
                StreamUtils.copyToString(getHappyResource().getInputStream(), Charset.defaultCharset()),
                is("happy"));
    }

    @Test
    public void testSad() {
        assertThrows(IOException.class, () ->
            getSadResource().getInputStream());
    }

    abstract Resource getHappyResource();
    abstract Resource getSadResource();

    @ExtendWith(MockitoExtension.class)
    static class LightweightResourceLoaderTest extends RefResourceProtocolTest {
        DefaultResourceLoader resourceLoader;
        
        @Mock
        Environment env;
    
        @BeforeEach
        void beforeEach() {
            resourceLoader = new StaticApplicationContext();
            resourceLoader.addProtocolResolver(new RefProtocolResolver(env));
        }
    
        @Override
        Resource getHappyResource() {
            when(env.getProperty("message")).thenReturn("happy");
            return resourceLoader.getResource("ref:message");
        }

        @Override
        Resource getSadResource() {
            return resourceLoader.getResource("ref:missingProperty");
        }
    }

    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {"message=happy"})
    static class IntegrationTest extends RefResourceProtocolTest {
        @Configuration
        @Import(RefProtocolBeans.class)
        public static class TestConfig {
            @Bean
            public Resource happyResource(@Value("ref:message") Resource resource) {
                return resource;
            }
            @Bean
            public Resource sadResource(@Value("ref:missingProperty") Resource resource) {
                return resource;
            }
        }

        @Autowired
        @Qualifier("happyResource")
        private Resource happyResource;

        @Autowired
        @Qualifier("sadResource")
        private Resource sadResource;

        @Override
        Resource getHappyResource() {
            return happyResource;
        }

        @Override
        Resource getSadResource() {
            return sadResource;
        }
    }
}
