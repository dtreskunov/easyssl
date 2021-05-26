package com.github.dtreskunov.easyssl.spring;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.ProtocolResolver;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.Assert;

import com.github.dtreskunov.easyssl.ProtocolResolverRegistrar;

/**
 * Allows specifying Spring {@link Resource}s as literals from environment
 * variables. For example, {@code env:PRIVATE_KEY} will result in a resource
 * that will read from environment variable {@code PRIVATE_KEY} when an
 * {@link InputStream} is requested (if {@code PRIVATE_KEY} is not set, an
 * {@link IOException} is thrown).
 */
@Configuration
public class EnvResourceProtocol {

    public static class EnvironmentVariableResource extends AbstractResource {

        public static class EnvironmentVariableNotSetException extends IOException {
            private static final long serialVersionUID = 1L;

            public EnvironmentVariableNotSetException(String message) {
                super(message);
            }
        }

        private final String m_name;

        public EnvironmentVariableResource(String name) {
            Assert.notNull(name, "name cannot be null");
            m_name = name;
        }

        @Override
        public String getDescription() {
            return "Contents of environment variable " + m_name;
        }

        @Override
        public InputStream getInputStream() throws IOException {
            String value = System.getenv(m_name);
            if (value == null) {
                throw new EnvironmentVariableNotSetException(m_name);
            }
            return new ByteArrayInputStream(value.getBytes());
        }
    }

    public static class Resolver implements ProtocolResolver {
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

    @Bean
    public ProtocolResolverRegistrar registrar() {
        return new ProtocolResolverRegistrar(new Resolver());
    }

}
