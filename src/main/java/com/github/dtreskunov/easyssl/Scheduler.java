package com.github.dtreskunov.easyssl;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

class Scheduler {
    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor(
        ThreadFactoryFactory.createThreadFactory(true, Scheduler.class.getSimpleName() + " daemon"));

    public static void runAndSchedule(String name, long timeout, long period, TimeUnit unit, Runnable runnable) {
        if (timeout > 0) {
            runnable = TimeoutUtils
                .builder()
                .setName(name)
                .setTimeout(timeout, unit)
                .wrap(runnable);
        }

        // ensure any initial exception isn't ignored (as would happen if thrown in the executor thread)
        runnable.run();
        if (period > 0) {
            SCHEDULER.scheduleAtFixedRate(runnable, period, period, unit);
        }
    }
}
