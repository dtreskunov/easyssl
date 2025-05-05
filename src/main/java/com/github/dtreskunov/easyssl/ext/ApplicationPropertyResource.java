package com.github.dtreskunov.easyssl.ext;

import org.springframework.core.env.Environment;
import org.springframework.util.Assert;

public class ApplicationPropertyResource extends AbstractNamedResource {
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