package com.github.dtreskunov.easyssl;

import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * Very enterprise. Much Java.
 */
public class ThreadFactoryFactory {
    /**
     * Creates a {@link ThreadFactory} that executes the runnable in a new thread
     * @param daemon whether the factory will create "daemon" threads (i.e. those not blocking JVM from exiting)
     * @param name the name of the threads created by the factory
     */
    public static ThreadFactory createThreadFactory(boolean daemon, String name) {
        return runnable -> {
            Thread thread = Executors.defaultThreadFactory().newThread(runnable);
            thread.setDaemon(daemon);
            thread.setName(name);
            return thread;
        };
    }
}
