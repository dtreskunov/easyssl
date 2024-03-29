package com.github.dtreskunov.easyssl.ext;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.springframework.core.io.AbstractResource;
import org.springframework.util.Assert;

abstract class AbstractNamedResource extends AbstractResource {

    private final String name;

    public AbstractNamedResource(String name) {
        Assert.notNull(name, "name cannot be null");
        this.name = name;
    }

    abstract String getValue(String name);

    @Override
    public String getDescription() {
        return "Contents of " + getClass().getSimpleName() + " named " + name;
    }

    @Override
    public InputStream getInputStream() throws IOException {
        String value = getValue(name);
        if (value == null) {
            throw new IOException(getDescription() + " is null");
        }
        return new ByteArrayInputStream(value.getBytes());
    }
}