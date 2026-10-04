package io.quarkiverse.jimmer.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.jimmer.runtime.cache.impl.TransactionCacheOperatorFlusher;
import io.quarkus.arc.Arc;
import io.quarkus.builder.Version;
import io.quarkus.maven.dependency.Dependency;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.Scheduler;
import io.quarkus.test.QuarkusUnitTest;

class BinlogOnlyCacheScheduleTest {

    @RegisterExtension
    static final QuarkusUnitTest APP = new QuarkusUnitTest()
            .setForcedDependencies(List.of(Dependency.of("io.quarkus", "quarkus-scheduler", Version.getVersion())))
            .withApplicationRoot(archive -> archive.addClass(ApplicationJob.class))
            .overrideConfigKey("quarkus.datasource.devservices.enabled", "false")
            .overrideConfigKey("quarkus.redis.devservices.enabled", "false")
            .overrideConfigKey("quarkus.datasource.db-kind", "h2")
            .overrideConfigKey("quarkus.datasource.jdbc.url", "jdbc:h2:mem:binlog-cache-schedule")
            .overrideConfigKey("quarkus.jimmer.trigger-type", "BINLOG_ONLY");

    @Inject
    Scheduler scheduler;

    @Test
    void schedulerPresenceDoesNotEnableTransactionCacheRecoveryForBinlogOnlyClients() {
        assertTrue(scheduler.isRunning());
        assertNotNull(scheduler.getScheduledJob("application-job"));
        assertNull(scheduler.getScheduledJob("jimmer.transaction-cache-operator-job"));
        assertEquals(1, scheduler.getScheduledJobs().size());
        assertTrue(Arc.container().select(TransactionCacheOperatorFlusher.class).isUnsatisfied());
    }

    @Singleton
    public static class ApplicationJob {
        @Scheduled(every = "1h", identity = "application-job")
        void tick() {
        }
    }
}
