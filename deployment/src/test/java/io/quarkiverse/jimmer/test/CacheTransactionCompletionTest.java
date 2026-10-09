package io.quarkiverse.jimmer.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.util.TypeLiteral;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.transaction.RollbackException;
import jakarta.transaction.Status;
import jakarta.transaction.Transaction;
import jakarta.transaction.TransactionManager;

import org.babyfish.jimmer.meta.ImmutableProp;
import org.babyfish.jimmer.meta.ImmutableType;
import org.babyfish.jimmer.sql.JSqlClient;
import org.babyfish.jimmer.sql.cache.Cache;
import org.babyfish.jimmer.sql.cache.CacheEnvironment;
import org.babyfish.jimmer.sql.cache.CacheFactory;
import org.babyfish.jimmer.sql.cache.TransactionCacheOperator;
import org.babyfish.jimmer.sql.event.AssociationEvent;
import org.babyfish.jimmer.sql.event.DatabaseEvent;
import org.babyfish.jimmer.sql.event.EntityEvent;
import org.babyfish.jimmer.sql.transaction.Propagation;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import io.quarkiverse.jimmer.runtime.cache.impl.TransactionCacheOperatorFlusher;
import io.quarkiverse.jimmer.runtime.cfg.support.QuarkusConnectionManager;
import io.quarkiverse.jimmer.test.model.CdiBook;
import io.quarkiverse.jimmer.test.model.CdiBookDraft;
import io.quarkiverse.jimmer.test.model.sort.LegacySortBook;
import io.quarkus.agroal.DataSource.DataSourceLiteral;
import io.quarkus.arc.Arc;
import io.quarkus.test.QuarkusUnitTest;

class CacheTransactionCompletionTest {

    @RegisterExtension
    static final QuarkusUnitTest APP = new QuarkusUnitTest()
            .withApplicationRoot(archive -> archive.addPackage(CdiBook.class.getPackage())
                    .addPackage(LegacySortBook.class.getPackage())
                    .addClasses(RecordingOperator.class, RecordingCacheFactory.class, TrackingCache.class, Observation.class,
                            DefaultEventObserver.class)
                    .addAsResource(new StringAsset(CdiBook.class.getName() + "\n"), "META-INF/jimmer/entities"))
            .overrideConfigKey("quarkus.datasource.devservices.enabled", "false")
            .overrideConfigKey("quarkus.redis.devservices.enabled", "false")
            .overrideConfigKey("quarkus.scheduler.enabled", "false")
            .overrideConfigKey("quarkus.datasource.db-kind", "h2")
            .overrideConfigKey("quarkus.datasource.jdbc.url", "jdbc:h2:mem:cache-transaction-completion;DB_CLOSE_DELAY=-1")
            .overrideConfigKey("quarkus.datasource.jdbc.max-size", "1")
            .overrideConfigKey("quarkus.datasource.jdbc.acquisition-timeout", "2S")
            .overrideConfigKey("quarkus.jimmer.trigger-type", "TRANSACTION_ONLY");

    @Inject
    DataSource dataSource;

    @Inject
    TransactionManager transactionManager;

    @Inject
    Event<EntityEvent<CdiBook>> events;

    @Inject
    Event<AssociationEvent> associationEvents;

    @Inject
    JSqlClient sqlClient;

    @Inject
    TransactionCacheOperatorFlusher flusher;

    @Inject
    RecordingOperator operator;

    @Inject
    RecordingCacheFactory cacheFactory;

    @Inject
    DefaultEventObserver defaultEventObserver;

    @BeforeEach
    void prepareTables() throws Exception {
        assertNull(transactionManager.getTransaction());
        executeSql("create table if not exists CACHE_TX_WORK (ID varchar(100) primary key)");
        executeSql("create table if not exists CDI_BOOK (ID bigint primary key, NAME varchar(100) not null)");
        // Initialize the real Jimmer operator and its durable invalidation table outside a business transaction.
        sqlClient.getCaches();
        executeSql("delete from CACHE_TX_WORK");
        executeSql("delete from CDI_BOOK");
        executeSql("delete from " + TransactionCacheOperator.TABLE_NAME);
        operator.observations.clear();
        operator.successes.set(0);
        cacheFactory.cache.deletions.clear();
        cacheFactory.cache.failNext.set(false);
        defaultEventObserver.count.set(0);
    }

    @AfterEach
    void cleanUpTransaction() throws Exception {
        if (transactionManager.getTransaction() != null) {
            transactionManager.rollback();
        }
    }

    @Test
    void mixedTriggersAndExplicitNotificationsFlushOnceAfterCommitWithASizeOneConnectionPool() throws Exception {
        transactionManager.begin();
        Transaction business = transactionManager.getTransaction();
        insertWork("committed");
        fireEvent();
        fireApplicationEvent(true, false);
        fireApplicationEvent(true, true);
        assertEquals(0, operator.observations.size());

        transactionManager.commit();

        assertEquals(1, operator.successes.get());
        assertEquals(1, operator.observations.size());
        Observation flush = operator.observations.get(0);
        assertEquals(Status.STATUS_ACTIVE, flush.status());
        assertNotSame(business, flush.transaction());
        assertEquals(Set.of("committed"), flush.visibleRows());
        assertNull(transactionManager.getTransaction());
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void rollbackAndRollbackOnlyNeverFlush(boolean rollbackOnly) throws Exception {
        transactionManager.begin();
        insertWork("rolled-back");
        fireEvent();
        fireApplicationEvent(true, false);
        if (rollbackOnly) {
            transactionManager.setRollbackOnly();
            // An event first encountered in a doomed transaction must not schedule another callback.
            fireEvent();
            fireApplicationEvent(true, true);
            assertThrows(RollbackException.class, transactionManager::commit);
        } else {
            transactionManager.rollback();
        }

        assertEquals(0, operator.observations.size());
        assertEquals(0, countRows("CACHE_TX_WORK"));
        transactionManager.begin();
        transactionManager.commit();
        assertEquals(0, operator.observations.size());
    }

    @Test
    void noTransactionAndNoEventDoNotLeaveStateForALaterTransaction() throws Exception {
        fireEvent();
        fireApplicationEvent(true, false);
        fireApplicationEvent(true, true, true);
        assertEquals(0, operator.observations.size());

        transactionManager.begin();
        insertWork("no-event");
        transactionManager.commit();
        assertEquals(0, operator.observations.size());

        transactionManager.begin();
        fireEvent();
        transactionManager.commit();
        assertEquals(1, operator.successes.get());
        assertEquals(Set.of("no-event"), operator.observations.get(0).visibleRows());
    }

    @ParameterizedTest
    @CsvSource({ "false, false, false", "false, true, false", "true, false, false", "true, true, false",
            "false, false, true", "false, true, true", "true, false, true", "true, true, true" })
    void applicationCdiEventsScheduleFlushingOnlyWhenDatasourceQualified(boolean qualified, boolean association,
            boolean programmatic)
            throws Exception {
        transactionManager.begin();
        insertWork("application-event");
        fireApplicationEvent(qualified, association, programmatic);
        assertEquals(0, operator.observations.size());
        transactionManager.commit();

        assertEquals(qualified ? 1 : 0, operator.observations.size());
        if (!qualified) {
            flusher.retry();
        }
        assertEquals(1, operator.successes.get());
        assertEquals(Set.of("application-event"), operator.observations.get(0).visibleRows());
    }

    @Test
    void requiresNewKeepsItsFlushSeparateFromTheSuspendedOuterTransaction() throws Exception {
        transactionManager.begin();
        Transaction outer = transactionManager.getTransaction();
        // Keep the single connection available until the inner transaction has completed.
        fireEvent();
        new QuarkusConnectionManager(dataSource).executeTransaction(Propagation.REQUIRES_NEW, connection -> {
            insertWork(connection, "inner");
            fireEvent();
            fireEvent();
            return null;
        });

        assertSame(outer, transactionManager.getTransaction());
        assertEquals(1, operator.successes.get());
        assertEquals(Set.of("inner"), operator.observations.get(0).visibleRows());
        insertWork("outer");
        fireEvent();
        transactionManager.commit();

        assertEquals(2, operator.successes.get());
        assertEquals(Set.of("inner", "outer"), operator.observations.get(1).visibleRows());
        assertNotSame(operator.observations.get(0).transaction(), operator.observations.get(1).transaction());
        assertNull(transactionManager.getTransaction());
    }

    @Test
    @EnabledForJreRange(min = JRE.JAVA_21)
    void aTransactionResumedOnAnotherThreadStillFlushesAfterCommit() throws Exception {
        transactionManager.begin();
        insertWork("migrated");
        fireEvent();
        Transaction suspended = transactionManager.suspend();
        Thread originalThread = Thread.currentThread();
        // Keep the Java 17 source baseline while exercising real virtual threads when running on Java 21+.
        var worker = (ExecutorService) Executors.class.getMethod("newVirtualThreadPerTaskExecutor").invoke(null);
        try {
            worker.submit(() -> {
                transactionManager.resume(suspended);
                try {
                    transactionManager.commit();
                } finally {
                    if (transactionManager.getTransaction() != null) {
                        transactionManager.rollback();
                    }
                }
                return null;
            }).get(10, TimeUnit.SECONDS);
        } finally {
            worker.shutdownNow();
        }

        assertEquals(1, operator.successes.get());
        assertEquals(Set.of("migrated"), operator.observations.get(0).visibleRows());
        assertNotSame(originalThread, operator.observations.get(0).thread());
        assertEquals(Boolean.TRUE, Thread.class.getMethod("isVirtual").invoke(operator.observations.get(0).thread()));
        assertNotSame(suspended, operator.observations.get(0).transaction());
        assertNull(transactionManager.getTransaction());
    }

    @Test
    void failedInvalidationLeavesTheCommittedJimmerQueueAvailableForRetry() throws Exception {
        cacheFactory.cache.failNext.set(true);
        CdiBook book = CdiBookDraft.$.produce(draft -> draft.setId(1L).setName("Committed book"));
        transactionManager.begin();
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.executeUpdate("insert into CDI_BOOK (ID, NAME) values (1, 'Committed book')");
            // This traverses Jimmer's real cache listener and persists the delayed deletion in the same transaction.
            sqlClient.getTriggers(true).fireEntityTableChange(null, book, connection);
        }
        assertEquals(0, operator.observations.size());
        assertTrue(defaultEventObserver.count.get() > 0, "Datasource-qualified events must retain existing @Default observers");
        transactionManager.commit();

        assertNull(transactionManager.getTransaction());
        assertEquals(1, countRows("CDI_BOOK"));
        assertEquals(1, operator.observations.size());
        assertEquals(0, operator.successes.get());
        assertTrue(cacheFactory.cache.deletions.isEmpty());
        assertEquals(1, countRows(TransactionCacheOperator.TABLE_NAME));

        flusher.retry();

        assertEquals(2, operator.observations.size());
        assertEquals(1, operator.successes.get());
        assertEquals(Status.STATUS_ACTIVE, operator.observations.get(1).status());
        assertNotSame(operator.observations.get(0).transaction(), operator.observations.get(1).transaction());
        assertEquals(List.of(1L), cacheFactory.cache.deletions);
        assertEquals(0, countRows(TransactionCacheOperator.TABLE_NAME));
        assertEquals(1, countRows("CDI_BOOK"));
        assertNull(transactionManager.getTransaction());
    }

    private void fireEvent() {
        sqlClient.getTriggers(true).fireEntityEvict(ImmutableType.get(CdiBook.class), 1L, null, "completion-test");
    }

    private void fireApplicationEvent(boolean qualified, boolean association) {
        fireApplicationEvent(qualified, association, false);
    }

    private void fireApplicationEvent(boolean qualified, boolean association, boolean programmatic) {
        if (association) {
            var source = programmatic
                    ? Arc.container().beanManager().getEvent().select(AssociationEvent.class, Default.Literal.INSTANCE)
                    : associationEvents;
            var publisher = qualified ? source.select(new DataSourceLiteral("<default>")) : source;
            publisher.fire(new AssociationEvent(ImmutableType.get(LegacySortBook.class).getProp("parent"), 1L, null,
                    "application-event"));
        } else {
            var source = programmatic
                    ? Arc.container().beanManager().getEvent().select(new TypeLiteral<EntityEvent<CdiBook>>() {
                    }, Default.Literal.INSTANCE)
                    : events;
            var publisher = qualified ? source.select(new DataSourceLiteral("<default>")) : source;
            publisher.fire(EntityEvent.evict(ImmutableType.get(CdiBook.class), 1L, null, "application-event"));
        }
    }

    private void insertWork(String id) throws SQLException {
        try (var connection = dataSource.getConnection()) {
            insertWork(connection, id);
        }
    }

    private static void insertWork(Connection connection, String id) {
        try (var statement = connection.prepareStatement("insert into CACHE_TX_WORK (ID) values (?)")) {
            statement.setString(1, id);
            assertEquals(1, statement.executeUpdate());
        } catch (SQLException e) {
            throw new AssertionError(e);
        }
    }

    private void executeSql(String sql) throws SQLException {
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private int countRows(String table) throws SQLException {
        try (var connection = dataSource.getConnection();
                var statement = connection.createStatement();
                var rows = statement.executeQuery("select count(*) from " + table)) {
            assertTrue(rows.next());
            return rows.getInt(1);
        }
    }

    public record Observation(Transaction transaction, int status, Set<String> visibleRows, Thread thread) {
    }

    @Singleton
    public static class DefaultEventObserver {
        final AtomicInteger count = new AtomicInteger();

        void onEvent(@Observes @Default DatabaseEvent event) {
            count.incrementAndGet();
        }
    }

    @Singleton
    @Default
    @io.quarkus.agroal.DataSource("<default>")
    public static class RecordingOperator extends TransactionCacheOperator {
        final List<Observation> observations = new CopyOnWriteArrayList<>();
        final AtomicInteger successes = new AtomicInteger();

        @Inject
        DataSource dataSource;

        @Inject
        TransactionManager transactionManager;

        @Override
        public void flush() {
            try (var connection = dataSource.getConnection();
                    var statement = connection.createStatement();
                    var rows = statement.executeQuery("select ID from CACHE_TX_WORK")) {
                Set<String> visibleRows = new HashSet<>();
                while (rows.next()) {
                    visibleRows.add(rows.getString(1));
                }
                observations.add(new Observation(transactionManager.getTransaction(), transactionManager.getStatus(),
                        Set.copyOf(visibleRows), Thread.currentThread()));
            } catch (Exception e) {
                throw new IllegalStateException("Cannot read committed rows during cache flush", e);
            }
            super.flush();
            successes.incrementAndGet();
        }
    }

    @Singleton
    public static class RecordingCacheFactory implements CacheFactory {
        final TrackingCache cache = new TrackingCache();

        @Override
        public Cache<?, ?> createObjectCache(ImmutableType type) {
            return type.getJavaClass() == CdiBook.class ? cache : null;
        }
    }

    public static class TrackingCache implements Cache<Object, CdiBook> {
        final AtomicBoolean failNext = new AtomicBoolean();
        final List<Object> deletions = new CopyOnWriteArrayList<>();

        @Override
        public ImmutableType type() {
            return ImmutableType.get(CdiBook.class);
        }

        @Override
        public ImmutableProp prop() {
            return null;
        }

        @Override
        public Map<Object, CdiBook> getAll(Collection<Object> keys, CacheEnvironment<Object, CdiBook> env) {
            return Map.of();
        }

        @Override
        public void deleteAll(Collection<Object> keys, Object reason) {
            if (failNext.getAndSet(false)) {
                throw new IllegalStateException("Simulated cache outage");
            }
            deletions.addAll(keys);
        }
    }
}
