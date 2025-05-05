package com.github.dtreskunov.easyssl.ext;

import org.springframework.core.io.ProtocolResolver;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

public class EnvProtocolResolver implements ProtocolResolver {
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