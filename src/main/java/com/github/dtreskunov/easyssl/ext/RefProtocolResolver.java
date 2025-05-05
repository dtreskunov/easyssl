package com.github.dtreskunov.easyssl.ext;

import org.springframework.core.env.Environment;
import org.springframework.core.io.ProtocolResolver;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.Assert;

public class RefProtocolResolver implements ProtocolResolver {
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