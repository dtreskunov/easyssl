package com.github.dtreskunov.easyssl.ext;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.charset.Charset;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
    "message=test message",
    "happy=ref:message",
    "sad=ref:missingProperty"
})
public class RefResourceProtocolInConfigurationPropertiesTest {
    @Configuration
    @EnableConfigurationProperties
    @Import(RefProtocolBeans.class)
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

    @Test
    public void testHappy() throws IOException {
        assertThat(
                StreamUtils.copyToString(properties.getHappy().getInputStream(), Charset.defaultCharset()),
                is("test message"));
    }

    @Test
    public void testSad() {
        assertThrows(IOException.class, () ->
            properties.getSad().getInputStream());
    }
}
