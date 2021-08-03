package com.github.dtreskunov.easyssl;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.charset.Charset;

import com.github.dtreskunov.easyssl.ext.EnvProtocolBeans;
import com.github.dtreskunov.easyssl.ext.EnvProtocolBeans.EnvironmentVariableNotSetException;

import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.SetEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

@SpringBootTest(properties = {"happy=env:HAPPY", "sad=env:SAD"}, webEnvironment = SpringBootTest.WebEnvironment.NONE)
public class EnvResourceProtocolInConfigurationPropertiesTest {
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

    @Test
    @SetEnvironmentVariable(key = "HAPPY", value = "happy")
    public void testHappy() throws IOException {
        assertThat(
                StreamUtils.copyToString(properties.getHappy().getInputStream(), Charset.defaultCharset()),
                is("happy"));
    }

    @Test
    public void testSad() throws IOException {
        assertThrows(EnvironmentVariableNotSetException.class, () ->
            properties.getSad().getInputStream());
    }
}
