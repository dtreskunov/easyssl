package com.github.dtreskunov.easyssl.ext;

public class EnvironmentVariableResource extends AbstractNamedResource {

    public EnvironmentVariableResource(String name) {
        super(name);
    }

    @Override
    String getValue(String name) {
        return System.getenv(name);
    }
}