package io.quarkiverse.jimmer.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.sql.DataSource;

import jakarta.enterprise.event.Event;
import jakarta.enterprise.inject.Default;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.transaction.Status;
import jakarta.transaction.Transaction;
import jakarta.transaction.TransactionManager;

import org.babyfish.jimmer.meta.ImmutableType;
import org.babyfish.jimmer.sql.JSqlClient;
import org.babyfish.jimmer.sql.cache.TransactionCacheOperator;
import org.babyfish.jimmer.sql.event.EntityEvent;
import org.babyfish.jimmer.sql.runtime.JSqlClientImplementor;
import org.babyfish.jimmer.sql.transaction.Propagation;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.jimmer.runtime.SqlClients;
import io.quarkiverse.jimmer.runtime.cache.impl.TransactionCacheOperatorFlusher;
import io.quarkiverse.jimmer.runtime.cfg.support.QuarkusConnectionManager;
import io.quarkiverse.jimmer.test.model.CdiBook;
import io.quarkus.agroal.DataSource.DataSourceLiteral;
import io.quarkus.arc.Arc;
import io.quarkus.test.QuarkusUnitTest;

class CacheMultiDataSourceCompletionTest {

    @RegisterExtension
    static final QuarkusUnitTest APP = new QuarkusUnitTest()
            .withApplicationRoot(archive -> archive.addPackage(CdiBook.class.getPackage())
                    .addClasses(RecordingOperator.class, DefaultOperator.class, BooksOperator.class, Observation.class)
                    .addAsResource(new StringAsset(CdiBook.class.getName() + "\n"), "META-INF/jimmer/entities"))
            .overrideConfigKey("quarkus.datasource.devservices.enabled", "false")
            .overrideConfigKey("quarkus.redis.devservices.enabled", "false")
            .overrideConfigKey("quarkus.scheduler.enabled", "false")
            .overrideConfigKey("quarkus.datasource.db-kind", "h2")
            .overrideConfigKey("quarkus.datasource.jdbc.url", "jdbc:h2:mem:cache-multi-default;DB_CLOSE_DELAY=-1")
            .overrideConfigKey("quarkus.datasource.jdbc.max-size", "1")
            .overrideConfigKey("quarkus.datasource.jdbc.acquisition-timeout", "1S")
            .overrideConfigKey("quarkus.datasource.books.db-kind", "h2")
            .overrideConfigKey("quarkus.datasource.books.jdbc.url", "jdbc:h2:mem:cache-multi-books;DB_CLOSE_DELAY=-1")
            .overrideConfigKey("quarkus.datasource.books.jdbc.max-size", "1")
            .overrideConfigKey("quarkus.datasource.books.jdbc.acquisition-timeout", "1S")
            .overrideConfigKey("quarkus.jimmer.trigger-type", "TRANSACTION_ONLY")
            .overrideConfigKey("quarkus.jimmer.books.trigger-type", "TRANSACTION_ONLY");

    @Inject
    DataSource defaults;

    @Inject
    @io.quarkus.agroal.DataSource("books")
    DataSource books;

    @Inject
    DefaultOperator defaultOperator;

    @Inject
    @io.quarkus.agroal.DataSource("books")
    BooksOperator booksOperator;

    @Inject
    TransactionManager transactionManager;

    @Inject
    Event<EntityEvent<?>> events;

    @Inject
    JSqlClient defaultClient;

    @Inject
    @io.quarkus.agroal.DataSource("books")
    JSqlClient booksClient;

    @Inject
    TransactionCacheOperatorFlusher flusher;

    @BeforeEach
    void prepareTables() throws Exception {
        assertNull(transactionManager.getTransaction());
        for (DataSource dataSource : List.of(defaults, books)) {
            try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
                statement.execute("create table if not exists CACHE_SOURCE_WORK (ID varchar(100) primary key)");
                statement.executeUpdate("delete from CACHE_SOURCE_WORK");
            }
        }
        // Initialize clients before either size-one datasource pool is enlisted in a transaction.
        defaultClient.getCaches();
        booksClient.getCaches();
        defaultOperator.attempts.set(0);
        defaultOperator.observations.clear();
        booksOperator.attempts.set(0);
        booksOperator.observations.clear();
    }

    @AfterEach
    void cleanUpTransaction() throws Exception {
        if (transactionManager.getTransaction() != null) {
            transactionManager.rollback();
        }
    }

    @Test
    void innerCommitFlushesOnlyItsDatasourceWhileTheOuterConnectionRemainsEnlisted() throws Exception {
        JSqlClient manualBooks = SqlClients.java(Arc.container(), books, "books");
        assertNull(((JSqlClientImplementor) manualBooks).getCacheOperator());
        transactionManager.begin();
        Transaction outer = transactionManager.getTransaction();
        try (var connection = defaults.getConnection()) {
            insert(connection, "default-row");
        }
        defaultClient.getTriggers(true).fireEntityEvict(ImmutableType.get(CdiBook.class), 1L, null,
                "datasource-completion-test");
        events.select(new DataSourceLiteral("<default>"))
                .fire(EntityEvent.evict(ImmutableType.get(CdiBook.class), 1L, null, "application-event"));
        AtomicReference<Transaction> inner = new AtomicReference<>();

        new QuarkusConnectionManager(books).executeTransaction(Propagation.REQUIRES_NEW, connection -> {
            try {
                inner.set(transactionManager.getTransaction());
            } catch (Exception e) {
                throw new AssertionError(e);
            }
            insert(connection, "books-row");
            manualBooks.getTriggers(true).fireEntityEvict(ImmutableType.get(CdiBook.class), 1L, null, "manual-client");
            manualBooks.getTriggers(true).fireEntityEvict(ImmutableType.get(CdiBook.class), 2L, null, "manual-client");
            return null;
        });

        assertSame(outer, transactionManager.getTransaction());
        assertEquals(Status.STATUS_ACTIVE, transactionManager.getStatus());
        // Count before acquisition as well: a caught A-pool timeout must not make this assertion pass.
        assertEquals(0, defaultOperator.attempts.get());
        assertEquals(1, booksOperator.attempts.get());
        assertEquals(1, booksOperator.observations.size());
        Observation booksFlush = booksOperator.observations.get(0);
        assertEquals(Status.STATUS_ACTIVE, booksFlush.status());
        assertEquals(Set.of("books-row"), booksFlush.rows());
        assertNotSame(outer, booksFlush.transaction());
        assertNotSame(inner.get(), booksFlush.transaction());

        transactionManager.commit();

        assertNull(transactionManager.getTransaction());
        assertEquals(1, defaultOperator.attempts.get());
        assertEquals(1, booksOperator.attempts.get());
        assertEquals(1, defaultOperator.observations.size());
        Observation defaultFlush = defaultOperator.observations.get(0);
        assertEquals(Status.STATUS_ACTIVE, defaultFlush.status());
        assertEquals(Set.of("default-row"), defaultFlush.rows());
        assertNotSame(outer, defaultFlush.transaction());
        assertNotSame(booksFlush.transaction(), defaultFlush.transaction());

        flusher.retry();

        assertEquals(2, defaultOperator.attempts.get());
        assertEquals(2, booksOperator.attempts.get());
        assertEquals(2, defaultOperator.observations.size());
        assertEquals(2, booksOperator.observations.size());
        Observation defaultRetry = defaultOperator.observations.get(1);
        Observation booksRetry = booksOperator.observations.get(1);
        assertEquals(Status.STATUS_ACTIVE, defaultRetry.status());
        assertEquals(Status.STATUS_ACTIVE, booksRetry.status());
        assertEquals(Set.of("default-row"), defaultRetry.rows());
        assertEquals(Set.of("books-row"), booksRetry.rows());
        assertNotSame(defaultFlush.transaction(), defaultRetry.transaction());
        assertNotSame(booksFlush.transaction(), booksRetry.transaction());
        assertNotSame(defaultRetry.transaction(), booksRetry.transaction());
        assertNull(transactionManager.getTransaction());
    }

    private static void insert(Connection connection, String id) {
        try (var statement = connection.prepareStatement("insert into CACHE_SOURCE_WORK (ID) values (?)")) {
            statement.setString(1, id);
            assertEquals(1, statement.executeUpdate());
        } catch (SQLException e) {
            throw new AssertionError(e);
        }
    }

    public record Observation(Transaction transaction, int status, Set<String> rows) {
    }

    public abstract static class RecordingOperator extends TransactionCacheOperator {
        final AtomicInteger attempts = new AtomicInteger();
        final List<Observation> observations = new CopyOnWriteArrayList<>();
        private final DataSource dataSource;
        private final TransactionManager transactionManager;

        RecordingOperator(DataSource dataSource, TransactionManager transactionManager) {
            this.dataSource = dataSource;
            this.transactionManager = transactionManager;
        }

        @Override
        public void flush() {
            attempts.incrementAndGet();
            try (var connection = dataSource.getConnection();
                    var statement = connection.createStatement();
                    var rows = statement.executeQuery("select ID from CACHE_SOURCE_WORK")) {
                Set<String> visibleRows = new HashSet<>();
                while (rows.next()) {
                    visibleRows.add(rows.getString(1));
                }
                observations.add(new Observation(transactionManager.getTransaction(), transactionManager.getStatus(),
                        Set.copyOf(visibleRows)));
            } catch (Exception e) {
                throw new IllegalStateException("Cannot read datasource during cache flush", e);
            }
        }
    }

    @Singleton
    @Default
    @io.quarkus.agroal.DataSource("<default>")
    public static class DefaultOperator extends RecordingOperator {
        @Inject
        public DefaultOperator(DataSource dataSource, TransactionManager transactionManager) {
            super(dataSource, transactionManager);
        }
    }

    @Singleton
    @io.quarkus.agroal.DataSource("books")
    public static class BooksOperator extends RecordingOperator {
        @Inject
        public BooksOperator(@io.quarkus.agroal.DataSource("books") DataSource dataSource,
                TransactionManager transactionManager) {
            super(dataSource, transactionManager);
        }
    }
}
