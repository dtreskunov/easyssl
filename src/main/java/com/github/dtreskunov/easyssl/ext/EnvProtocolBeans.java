package com.github.dtreskunov.easyssl.ext;

import java.io.IOException;
import java.io.InputStream;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ProtocolResolver;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

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

    static class EnvProtocolResolver implements ProtocolResolver {
        public static final String ENV_PROTOCOL_PREFIX = "env:";

        @Override
        public Resource resolve(String location, ResourceLoader resourceLoader) {
            if (!location.startsWith(ENV_PROTOCOL_PREFIX)) {
                return null;
            }
            String environmentVariableName = location.substring(ENV_PROTOCOL_PREFIX.length());
            return new EnvironmentVariableResource(environmentVariableName);
        }
    }

    static class EnvironmentVariableResource extends AbstractNamedResource {

        public EnvironmentVariableResource(String name) {
            super(name);
        }

        @Override
        String getValue(String name) {
            return System.getenv(name);
        }
    }
}
