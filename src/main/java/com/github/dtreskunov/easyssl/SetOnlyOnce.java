package com.github.dtreskunov.easyssl;

import org.springframework.util.Assert;

class SetOnlyOnce<T> {
    private T value;

    public void set(T value) {
        Assert.notNull(value, "cannot set null value");
        Assert.isTrue(this.value == null || this.value == value, "cannot set this.value twice");
        this.value = value;
    }

    public T get() {
        if (value == null) {
            throw new IllegalStateException("value has not yet been set");
        }
        return value;
    }
}
