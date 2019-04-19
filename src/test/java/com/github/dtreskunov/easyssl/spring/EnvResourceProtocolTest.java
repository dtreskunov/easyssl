package com.github.dtreskunov.easyssl.spring;

import java.io.IOException;
import java.nio.charset.Charset;

import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.Resource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.StreamUtils;

import com.github.dtreskunov.easyssl.spring.EnvResourceProtocol.EnvironmentVariableResource.EnvironmentVariableNotSetException;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
public class EnvResourceProtocolTest {
    @Rule
    public final EnvironmentVariables environmentVariables = new EnvironmentVariables();

    @Configuration
    @Import(EnvResourceProtocol.class)
    public static class TestConfig {
        @Bean
        public Resource resource(@Value("env:TEST_KEY") Resource resource) {
            return resource;
        }
    }

    @Autowired
    private Resource resource;

    @Test
    public void testHappy() throws IOException {
        environmentVariables.set("TEST_KEY", "happy");
        Assert.assertThat(
        		StreamUtils.copyToString(resource.getInputStream(), Charset.defaultCharset()),
        		Matchers.is("happy"));
    }

    @Test(expected = EnvironmentVariableNotSetException.class)
    public void testSad() throws IOException {
        resource.getInputStream();
    }
}
