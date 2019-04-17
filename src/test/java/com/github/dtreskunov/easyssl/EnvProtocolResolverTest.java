package com.github.dtreskunov.easyssl;

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
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.StreamUtils;

import com.github.dtreskunov.easyssl.EnvProtocolResolver.EnvironmentVariableResource.EnvironmentVariableNotSetException;

@RunWith(SpringRunner.class)
@SpringBootTest
public class EnvProtocolResolverTest {
    @Rule
    public final EnvironmentVariables environmentVariables = new EnvironmentVariables();

    @Configuration
    public static class TestConfig {
        @Bean
        @Scope(value = "prototype", proxyMode = ScopedProxyMode.INTERFACES)
        public Resource resource(@Value("env:TEST_KEY") Resource resource) {
            return resource;
        }
    }

    @Autowired
    private Resource resource;

    @Autowired
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public void registerEnvProtocol(ConfigurableApplicationContext context) {
        context.addProtocolResolver(new EnvProtocolResolver());
    }

    @Test
    public void testHappy() throws IOException {
        environmentVariables.set("TEST_KEY", "happy");
        Assert.assertThat(StreamUtils.copyToString(resource.getInputStream(), Charset.defaultCharset()), Matchers.is("happy"));
    }

    @Test(expected = EnvironmentVariableNotSetException.class)
    public void testSad() throws IOException {
        resource.getInputStream();
    }
}
