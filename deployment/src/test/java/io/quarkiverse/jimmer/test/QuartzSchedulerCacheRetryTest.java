package io.quarkiverse.jimmer.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import jakarta.inject.Inject;
import jakarta.transaction.Status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.arc.Arc;
import io.quarkus.builder.Version;
import io.quarkus.maven.dependency.Dependency;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.Scheduler;
import io.quarkus.test.QuarkusUnitTest;

class QuartzSchedulerCacheRetryTest {

    @RegisterExtension
    static final QuarkusUnitTest APP = new QuarkusUnitTest()
            .withApplicationRoot(archive -> archive.addClass(ScheduledRetryProbe.class))
            .setForcedDependencies(List.of(Dependency.of("io.quarkus", "quarkus-quartz", Version.getVersion())))
            .overrideConfigKey("quarkus.datasource.devservices.enabled", "false")
            .overrideConfigKey("quarkus.redis.devservices.enabled", "false")
            .overrideConfigKey("quarkus.datasource.db-kind", "h2")
            .overrideConfigKey("quarkus.datasource.jdbc.url", "jdbc:h2:mem:quartz-scheduler-cache")
            .overrideConfigKey("quarkus.jimmer.trigger-type", "TRANSACTION_ONLY")
            .overrideConfigKey("quarkus.jimmer.transaction-cache-operator-fixed-delay", "1s");

    @Inject
    Scheduler scheduler;

    @Inject
    ScheduledRetryProbe operator;

    @Test
    void explicitlyAddedQuartzOwnsTheSingleRetryJob() throws InterruptedException {
        assertEquals(Scheduled.QUARTZ, scheduler.implementation());
        assertEquals(1, Arc.container().listAll(Scheduler.class).size(), "SimpleScheduler must not run alongside Quartz");
        assertTrue(scheduler.isRunning());
        assertNotNull(scheduler.getScheduledJob("jimmer.transaction-cache-operator-job"));
        assertEquals(1, scheduler.getScheduledJobs().size());
        assertTrue(operator.awaitSuccessfulRetry(), "Quartz must execute and retry the same extension job");
        assertTrue(operator.calls() >= 2);
        assertEquals(Set.of(Status.STATUS_ACTIVE), operator.transactionStatuses());
    }
}
