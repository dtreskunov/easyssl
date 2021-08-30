package com.github.dtreskunov.easyssl.ext;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.charset.Charset;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.Resource;
import org.springframework.util.StreamUtils;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {"message=happy"})
public class RefResourceProtocolTest {
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

    @Test
    public void testHappy() throws IOException {
        assertThat(
                StreamUtils.copyToString(happyResource.getInputStream(), Charset.defaultCharset()),
                is("happy"));
    }

    @Test
    public void testSad() {
        assertThrows(IOException.class, () ->
            sadResource.getInputStream());
    }
}
