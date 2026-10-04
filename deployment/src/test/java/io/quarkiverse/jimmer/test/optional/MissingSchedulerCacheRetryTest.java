package io.quarkiverse.jimmer.test.optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.sql.DataSource;

import jakarta.enterprise.event.Event;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Default;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.transaction.Status;
import jakarta.transaction.Transaction;
import jakarta.transaction.TransactionManager;

import org.babyfish.jimmer.meta.ImmutableType;
import org.babyfish.jimmer.sql.cache.TransactionCacheOperator;
import org.babyfish.jimmer.sql.event.EntityEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.jimmer.runtime.cache.impl.TransactionCacheOperatorFlusher;
import io.quarkiverse.jimmer.test.model.CdiBook;
import io.quarkus.agroal.DataSource.DataSourceLiteral;
import io.quarkus.arc.Arc;
import io.quarkus.test.QuarkusUnitTest;

class MissingSchedulerCacheRetryTest {

    @RegisterExtension
    static final QuarkusUnitTest APP = OptionalIntegrationTestSupport.isolateExcludedDependencies(new QuarkusUnitTest(),
            OptionalIntegrationTestSupport.withoutScheduler())
            .withApplicationRoot(archive -> archive.addPackage(CdiBook.class.getPackage())
                    .addClasses(OptionalIntegrationTestSupport.class, RecordingOperator.class))
            .overrideConfigKey("quarkus.datasource.devservices.enabled", "false")
            .overrideConfigKey("quarkus.redis.devservices.enabled", "false")
            .overrideConfigKey("quarkus.datasource.db-kind", "h2")
            .overrideConfigKey("quarkus.datasource.jdbc.url", "jdbc:h2:mem:cache-without-scheduler")
            .overrideConfigKey("quarkus.datasource.jdbc.max-size", "1")
            .overrideConfigKey("quarkus.datasource.jdbc.acquisition-timeout", "2s")
            .overrideConfigKey("quarkus.jimmer.trigger-type", "TRANSACTION_ONLY");

    @Inject
    TransactionManager transactions;

    @Inject
    Event<EntityEvent<?>> events;

    @Inject
    RecordingOperator operator;

    @Inject
    TransactionCacheOperatorFlusher flusher;

    @Test
    void successfulCommitStillFlushesWithoutAnySchedulerApiOrImplementation() throws Exception {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        assertThrows(ClassNotFoundException.class, () -> Class.forName("io.quarkus.scheduler.Scheduler", false, loader));
        assertThrows(ClassNotFoundException.class, () -> Class.forName("io.quarkus.scheduler.Scheduled", false, loader));
        assertTrue(Arc.container().beanManager().getBeans(Object.class, Any.Literal.INSTANCE).stream()
                .noneMatch(bean -> bean.getBeanClass().getName().endsWith("TransactionCacheOperatorRetryJob")));

        transactions.begin();
        Transaction business = transactions.getTransaction();
        try {
            events.select(new DataSourceLiteral("<default>"))
                    .fire(EntityEvent.evict(ImmutableType.get(CdiBook.class), 1L, null, "without-scheduler"));
            assertEquals(0, operator.calls);
            transactions.commit();
        } finally {
            if (transactions.getTransaction() != null) {
                transactions.rollback();
            }
        }
        assertEquals(1, operator.calls);
        assertEquals(Status.STATUS_ACTIVE, operator.status);
        assertNotSame(business, operator.transaction);
        assertEquals(42, operator.queryResult);

        transactions.begin();
        try {
            events.select(new DataSourceLiteral("<default>"))
                    .fire(EntityEvent.evict(ImmutableType.get(CdiBook.class), 2L, null, "rollback-without-scheduler"));
        } finally {
            transactions.rollback();
        }
        assertEquals(1, operator.calls, "Rollback must not flush");
        flusher.retry();
        assertEquals(2, operator.calls, "Applications can still request a manual retry");
    }

    @Singleton
    @Default
    @io.quarkus.agroal.DataSource("<default>")
    public static class RecordingOperator extends TransactionCacheOperator {

        @Inject
        TransactionManager transactions;

        @Inject
        DataSource dataSource;

        int calls;
        int status;
        int queryResult;
        Transaction transaction;

        @Override
        public void flush() {
            try (var connection = dataSource.getConnection();
                    var statement = connection.createStatement();
                    var rows = statement.executeQuery("select 42")) {
                transaction = transactions.getTransaction();
                status = transactions.getStatus();
                rows.next();
                queryResult = rows.getInt(1);
                calls++;
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        }
    }
}
