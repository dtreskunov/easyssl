package com.github.dtreskunov.easyssl.ext;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.charset.Charset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.SetEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

public abstract class EnvResourceProtocolInConfigurationPropertiesTest {

    @Test
    @SetEnvironmentVariable(key = "HAPPY", value = "happy")
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

    static class LightweightResourceLoaderTest extends EnvResourceProtocolInConfigurationPropertiesTest {

        DefaultResourceLoader resourceLoader;
    
        @BeforeEach
        void beforeEach() {
            resourceLoader = new StaticApplicationContext();
            resourceLoader.addProtocolResolver(new EnvProtocolResolver());
        }
    
        @Override
        Resource getHappyResource() {
            return resourceLoader.getResource("env:HAPPY");
        }

        @Override
        Resource getSadResource() {
            return resourceLoader.getResource("env:SAD");
        }
    }

    @SpringBootTest(properties = {"happy=env:HAPPY", "sad=env:SAD"}, webEnvironment = SpringBootTest.WebEnvironment.NONE)
    static class IntegrationTest extends EnvResourceProtocolInConfigurationPropertiesTest {
        @Configuration
        @EnableConfigurationProperties
        @Import(EnvProtocolBeans.class)
        public static class TestConfig {
            @ConfigurationProperties
            @Component
            public static class Properties {
                Resource happy;
                Resource sad;
                public Resource getHappy() {
                    return happy;
                }
                public void setHappy(Resource happy) {
                    this.happy = happy;
                }
                public Resource getSad() {
                    return sad;
                }
                public void setSad(Resource sad) {
                    this.sad = sad;
                }
            }
        }

        @Autowired
        private TestConfig.Properties properties;

        @Override
        Resource getHappyResource() {
            return properties.getHappy();
        }

        @Override
        Resource getSadResource() {
            return properties.getSad();
        }
    }
}
