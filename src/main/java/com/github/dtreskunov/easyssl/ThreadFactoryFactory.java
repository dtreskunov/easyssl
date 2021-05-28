package com.github.dtreskunov.easyssl;

import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * Very enterprise. Much Java.
 */
public class ThreadFactoryFactory {
    public static ThreadFactory createThreadFactory(boolean daemon, String name) {
        return runnable -> {
            Thread thread = Executors.defaultThreadFactory().newThread(runnable);
            thread.setDaemon(daemon);
            thread.setName(name);
            return thread;
        };
    }
}
