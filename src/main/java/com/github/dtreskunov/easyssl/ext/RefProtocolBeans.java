package com.github.dtreskunov.easyssl.ext;

import java.io.IOException;
import java.io.InputStream;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ProtocolResolver;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.Assert;

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

    static class RefProtocolResolver implements ProtocolResolver {
        public static final String PROTOCOL_PREFIX = "ref:";
        private final Environment environment;

        public RefProtocolResolver(Environment environment) {
            Assert.notNull(environment, "environment cannot be null");
            this.environment = environment;
        }

        @Override
        public Resource resolve(String location, ResourceLoader resourceLoader) {
            if (!location.startsWith(PROTOCOL_PREFIX)) {
                return null;
            }
            String name = location.substring(PROTOCOL_PREFIX.length());
            return new ApplicationPropertyResource(name, environment);
        }
    }

    static class ApplicationPropertyResource extends AbstractNamedResource {
        private final Environment environment;

        public ApplicationPropertyResource(String name, Environment environment) {
            super(name);
            Assert.notNull(environment, "environment cannot be null");
            this.environment = environment;
        }

        @Override
        String getValue(String name) {
            return environment.getProperty(name);
        }
    }
}
