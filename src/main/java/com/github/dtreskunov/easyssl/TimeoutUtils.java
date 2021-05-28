package com.github.dtreskunov.easyssl;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.util.Assert;

/**
 * Utility class for putting a timeout on execution of arbitrary code
 */
public class TimeoutUtils {

    public static class Builder {
        private String m_name = TimeoutUtils.class.getSimpleName();
        private boolean m_daemon = true;
        private long m_timeout = 0;
        private TimeUnit m_unit = null;
        private boolean m_cancel = true;
        private boolean m_mayInterruptIfRunning = true;

        private Builder() {}

        /**
         * Used to construct descriptive exception messages (default: "TimeoutUtils")
         */
        public Builder setName(String name) {
            m_name = name;
            return this;
        }

        /**
         * See {@link Thread#setDaemon(boolean)} (default: true)
         */
        public Builder setDaemon(boolean daemon) {
            m_daemon = daemon;
            return this;
        }

        /**
         * Whether to call {@link Future#cancel(boolean)} (default: true)
         */
        public Builder setCancel(boolean cancel) {
            m_cancel = cancel;
            return this;
        }

        /**
         * Parameter to give to {@link Future#cancel(boolean)} (default: true)
         */
        public Builder setMayInterruptIfRunning(boolean mayInterruptIfRunning) {
            m_mayInterruptIfRunning = mayInterruptIfRunning;
            return this;
        }

        /**
         * @param timeout number &gt;= 0 (default: 0)
         * @param unit may be null if timeout is zero (default: null)
         */
        public Builder setTimeout(long timeout, TimeUnit unit) {
            Assert.isTrue(timeout >= 0, "timeout must be greater than or equal to zero");
            Assert.isTrue(unit != null || timeout == 0, "unit must be non-null when timeout is specified");

            m_timeout = timeout;
            m_unit = unit;
            return this;
        }

        /**
         * Runs the task in a separate thread. Current thread is blocked (indefinitely if
         * timeout is zero) until the task completes or times out.
         *
         * @throws ExecutionException if the computation threw an exception
         * @throws InterruptedException if the current thread was interrupted while waiting
         * @throws TimeoutException if the wait timed out
         */
        public void run(Runnable runnable) throws ExecutionException, InterruptedException, TimeoutException {
            call(() -> {
                runnable.run();
                return null;
            });
        }

        /**
         * Calls the function in a separate thread. Current thread is blocked (indefinitely if
         * timeout is zero) until the function returns or times out.
         *
         * @throws ExecutionException if the computation threw an exception
         * @throws InterruptedException if the current thread was interrupted while waiting
         * @throws TimeoutException if the wait timed out
         */
        public <T> T call(Callable<T> callable) throws ExecutionException, InterruptedException, TimeoutException {
            ExecutorService executor = Executors.newSingleThreadExecutor(
                ThreadFactoryFactory.createThreadFactory(m_daemon, m_name + (m_daemon ? " daemon" : " notdaemon")));

            Future<T> future = executor.submit(callable);
            executor.shutdown();

            try {
                return m_timeout > 0 ? future.get(m_timeout, m_unit) : future.get();
            } catch (ExecutionException e) {
                throw new ExecutionException(m_name + " callable threw an exception", e.getCause());
            } catch (InterruptedException e) {
                if (m_cancel) {
                    future.cancel(m_mayInterruptIfRunning);
                }
                throw new InterruptedException(m_name + " was interrupted while waiting");
            } catch (TimeoutException e) {
                // wait timed out
                if (m_cancel) {
                    future.cancel(m_mayInterruptIfRunning);
                }
                throw new TimeoutException(m_name + " timed out");
            }
        }
    }

    /**
     * Returns a builder object that can be used to configure timeouts
     */
    public static Builder builder() {
        return new Builder();
    }
}
