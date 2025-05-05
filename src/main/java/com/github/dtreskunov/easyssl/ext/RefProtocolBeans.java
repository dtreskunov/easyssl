package com.github.dtreskunov.easyssl.ext;

import java.io.IOException;
import java.io.InputStream;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;

/**
 * Allows specifying Spring {@link Resource}s as literals referencing application
 * properties. For example, {@code ref:message} will result in a resource
 * that will read from application property {@code message} when an
 * {@link InputStream} is requested (if {@code message} is not set, an
 * {@link IOException} is thrown).
 */
@Configuration
public class RefProtocolBeans {

    @Bean
    ProtocolResolverRegistrar refProtocolResolverRegistrar(Environment environment) {
        return new ProtocolResolverRegistrar(new RefProtocolResolver(environment));
    }
}
