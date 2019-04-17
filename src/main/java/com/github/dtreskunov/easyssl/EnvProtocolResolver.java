package com.github.dtreskunov.easyssl;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.ProtocolResolver;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.Assert;

/**
 * Allows specifying Spring {@link Resource}s as literals from environment variables. For example,
 * {@code env:PRIVATE_KEY} will result in a resource that will read from environment variable {@code PRIVATE_KEY}
 * when an {@link InputStream} is requested (if {@code PRIVATE_KEY} is not set, an {@link IOException} is thrown).
 */
public class EnvProtocolResolver implements ProtocolResolver {

    private static final String ENV_PROTOCOL_PREFIX = "env:";

    public static class EnvironmentVariableResource extends AbstractResource {

        public static class EnvironmentVariableNotSetException extends IOException {
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

    @Override
    public Resource resolve(String location, ResourceLoader resourceLoader) {
        if (!location.startsWith(ENV_PROTOCOL_PREFIX)) {
            return null;
        }
        String environmentVariableName = location.substring(ENV_PROTOCOL_PREFIX.length());
        return new EnvironmentVariableResource(environmentVariableName);
    }
}
