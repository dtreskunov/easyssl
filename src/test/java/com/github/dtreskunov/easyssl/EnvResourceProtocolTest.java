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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.Resource;
import org.springframework.util.StreamUtils;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
public class EnvResourceProtocolTest {
    @Configuration
    @Import(EnvProtocolBeans.class)
    public static class TestConfig {
        @Bean
        public Resource resource(@Value("env:TEST_KEY") Resource resource) {
            return resource;
        }
    }

    @Autowired
    private Resource resource;

    @Test
    @SetEnvironmentVariable(key = "TEST_KEY", value = "happy")
    public void testHappy() throws IOException {
        assertThat(
                StreamUtils.copyToString(resource.getInputStream(), Charset.defaultCharset()),
                is("happy"));
    }

    @Test
    public void testSad() throws IOException {
        assertThrows(EnvironmentVariableNotSetException.class, () ->
            resource.getInputStream());
    }
}
