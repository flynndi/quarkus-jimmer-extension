package io.quarkiverse.jimmer.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.Scheduler;
import io.quarkus.test.QuarkusUnitTest;

class DisabledCacheRetryScheduleTest {

    @RegisterExtension
    static final QuarkusUnitTest APP = new QuarkusUnitTest()
            .withApplicationRoot(archive -> archive.addClasses(ScheduledRetryProbe.class, Heartbeat.class))
            .overrideConfigKey("quarkus.datasource.devservices.enabled", "false")
            .overrideConfigKey("quarkus.redis.devservices.enabled", "false")
            .overrideConfigKey("quarkus.datasource.db-kind", "h2")
            .overrideConfigKey("quarkus.datasource.jdbc.url", "jdbc:h2:mem:disabled-cache-retry")
            .overrideConfigKey("quarkus.jimmer.trigger-type", "TRANSACTION_ONLY")
            .overrideConfigKey("quarkus.jimmer.transaction-cache-operator-fixed-delay", "off");

    @Inject
    Scheduler scheduler;

    @Inject
    ScheduledRetryProbe operator;

    @Inject
    Heartbeat heartbeat;

    @Test
    void offDisablesOnlyTheCacheRetryJob() throws InterruptedException {
        assertEquals(Scheduled.SIMPLE, scheduler.implementation());
        assertTrue(scheduler.isRunning());
        assertNull(scheduler.getScheduledJob("jimmer.transaction-cache-operator-job"));
        assertTrue(heartbeat.ticks.await(10, TimeUnit.SECONDS), "Unrelated application jobs must continue running");
        assertEquals(0, operator.calls());
    }

    @Singleton
    public static class Heartbeat {
        final CountDownLatch ticks = new CountDownLatch(2);

        @Scheduled(every = "1s", identity = "application-heartbeat")
        void tick() {
            ticks.countDown();
        }
    }
}
