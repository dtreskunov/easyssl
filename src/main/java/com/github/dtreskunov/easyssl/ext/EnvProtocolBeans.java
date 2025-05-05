package com.github.dtreskunov.easyssl.ext;

import java.io.IOException;
import java.io.InputStream;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

/**
 * Allows specifying Spring {@link Resource}s as literals from environment
 * variables. For example, {@code env:PRIVATE_KEY} will result in a resource
 * that will read from environment variable {@code PRIVATE_KEY} when an
 * {@link InputStream} is requested (if {@code PRIVATE_KEY} is not set, an
 * {@link IOException} is thrown).
 */
@Configuration
public class EnvProtocolBeans {

    @Bean
    ProtocolResolverRegistrar envProtocolResolverRegistrar() {
        return new ProtocolResolverRegistrar(new EnvProtocolResolver());
    }
}
