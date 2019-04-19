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
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.StreamUtils;

import com.github.dtreskunov.easyssl.spring.EnvResourceProtocol.EnvironmentVariableResource.EnvironmentVariableNotSetException;

@RunWith(SpringRunner.class)
@SpringBootTest(properties = {"happy=env:HAPPY", "sad=env:SAD"}, webEnvironment = SpringBootTest.WebEnvironment.NONE)
public class EnvResourceProtocolInConfigurationPropertiesTest {
    @Rule
    public final EnvironmentVariables environmentVariables = new EnvironmentVariables();

    @Configuration
    @EnableConfigurationProperties
    @Import(EnvResourceProtocol.class)
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
        environmentVariables.set("HAPPY", "happy");
        Assert.assertThat(
        		StreamUtils.copyToString(properties.getHappy().getInputStream(), Charset.defaultCharset()),
        		Matchers.is("happy"));
    }

    @Test(expected = EnvironmentVariableNotSetException.class)
    public void testSad() throws IOException {
        properties.getSad().getInputStream();
    }
}
