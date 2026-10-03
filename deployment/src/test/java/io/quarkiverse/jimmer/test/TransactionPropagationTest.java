package io.quarkiverse.jimmer.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.sql.DataSource;

import jakarta.inject.Inject;
import jakarta.transaction.InvalidTransactionException;
import jakarta.transaction.Status;
import jakarta.transaction.SystemException;
import jakarta.transaction.Transaction;
import jakarta.transaction.TransactionManager;
import jakarta.transaction.TransactionRequiredException;
import jakarta.transaction.TransactionalException;

import org.babyfish.jimmer.sql.transaction.Propagation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.quarkiverse.jimmer.runtime.cfg.support.QuarkusConnectionManager;
import io.quarkus.arc.ArcUndeclaredThrowableException;
import io.quarkus.test.QuarkusUnitTest;
import io.quarkus.transaction.annotations.Rollback;

class TransactionPropagationTest {

    @RegisterExtension
    static final QuarkusUnitTest APP = new QuarkusUnitTest()
            .withApplicationRoot(archive -> archive.addClasses(NonRollbackFailure.class, RollbackCheckedFailure.class))
            .overrideConfigKey("quarkus.datasource.devservices.enabled", "false")
            .overrideConfigKey("quarkus.redis.devservices.enabled", "false")
            .overrideConfigKey("quarkus.datasource.db-kind", "h2")
            .overrideConfigKey("quarkus.datasource.jdbc.url", "jdbc:h2:mem:transaction-propagation;DB_CLOSE_DELAY=-1")
            .overrideConfigKey("quarkus.jimmer.trigger-type", "BINLOG_ONLY");

    @Inject
    DataSource dataSource;

    @Inject
    TransactionManager transactionManager;

    QuarkusConnectionManager connections;

    @BeforeEach
    void prepareTable() throws SQLException {
        assertNull(currentTransaction());
        connections = new QuarkusConnectionManager(dataSource);
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute("create table if not exists TX_PROPAGATION (ID varchar(100) primary key)");
            statement.executeUpdate("delete from TX_PROPAGATION");
        }
    }

    @AfterEach
    void cleanUpTransaction() throws Exception {
        if (transactionManager.getTransaction() != null) {
            transactionManager.rollback();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = { "REQUIRED", "REQUIRES_NEW", "SUPPORTS", "NOT_SUPPORTED", "NEVER" })
    void modesWithoutAnOuterTransactionCommitOrUseAutoCommit(String propagationName) throws SQLException {
        Propagation propagation = Propagation.valueOf(propagationName);
        boolean startsTransaction = propagation == Propagation.REQUIRED || propagation == Propagation.REQUIRES_NEW;
        String result = connections.executeTransaction(propagation, connection -> {
            if (startsTransaction) {
                assertNotNull(currentTransaction());
                assertEquals(Status.STATUS_ACTIVE, transactionStatus());
            } else {
                assertNull(currentTransaction());
            }
            insert(connection, "success");
            return "result";
        });

        assertEquals("result", result);
        assertNull(currentTransaction());
        assertTrue(rowExists("success"));

        for (Throwable failure : failures()) {
            String id = failure.getClass().getSimpleName();
            assertSame(failure,
                    assertThrows(failure.getClass(), () -> connections.executeTransaction(propagation, connection -> {
                        insert(connection, id);
                        return throwFailure(failure);
                    })));
            assertNull(currentTransaction());
            // Only modes that own a transaction can undo the insert on failure.
            assertEquals(!startsTransaction, rowExists(id));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = { "REQUIRED", "SUPPORTS", "MANDATORY" })
    void participatingModesLeaveCompletionToTheOuterTransaction(String propagationName) throws Exception {
        Propagation propagation = Propagation.valueOf(propagationName);
        for (boolean commit : new boolean[] { false, true }) {
            transactionManager.begin();
            Transaction outer = currentTransaction();
            String id = commit ? "committed" : "rolled-back";
            connections.executeTransaction(propagation, connection -> {
                assertSame(outer, currentTransaction());
                insert(connection, id);
                return null;
            });

            assertSame(outer, currentTransaction());
            assertEquals(Status.STATUS_ACTIVE, transactionStatus());
            if (commit) {
                transactionManager.commit();
            } else {
                transactionManager.rollback();
            }
            assertNull(currentTransaction());
            assertEquals(commit, rowExists(id));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = { "REQUIRED", "SUPPORTS", "MANDATORY" })
    void participatingFailuresMarkTheOuterTransactionAndPreserveTheThrowable(String propagationName) throws Exception {
        Propagation propagation = Propagation.valueOf(propagationName);
        for (Throwable failure : failures()) {
            transactionManager.begin();
            Transaction outer = currentTransaction();
            String id = failure.getClass().getSimpleName();

            assertSame(failure,
                    assertThrows(failure.getClass(), () -> connections.executeTransaction(propagation, connection -> {
                        assertSame(outer, currentTransaction());
                        insert(connection, id);
                        return throwFailure(failure);
                    })));

            assertSame(outer, currentTransaction());
            assertEquals(Status.STATUS_MARKED_ROLLBACK, transactionStatus());
            transactionManager.rollback();
            assertFalse(rowExists(id));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = { "REQUIRES_NEW", "NOT_SUPPORTED" })
    void independentWorkSurvivesAnOuterRollbackAndRestoresTheOuterTransaction(String propagationName) throws Exception {
        Propagation propagation = Propagation.valueOf(propagationName);
        transactionManager.begin();
        Transaction outer = currentTransaction();
        connections.execute(connection -> {
            insert(connection, "outer");
            return null;
        });

        connections.executeTransaction(propagation, connection -> {
            assertIndependentContext(propagation, outer);
            insert(connection, "independent");
            return null;
        });

        assertSame(outer, currentTransaction());
        assertEquals(Status.STATUS_ACTIVE, transactionStatus());
        transactionManager.rollback();
        assertFalse(rowExists("outer"));
        assertTrue(rowExists("independent"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "REQUIRES_NEW", "NOT_SUPPORTED" })
    void independentFailuresRestoreTheOuterTransactionWithoutMarkingIt(String propagationName) throws Exception {
        Propagation propagation = Propagation.valueOf(propagationName);
        for (Throwable failure : failures()) {
            String suffix = failure.getClass().getSimpleName();
            transactionManager.begin();
            Transaction outer = currentTransaction();
            connections.execute(connection -> {
                insert(connection, "outer-" + suffix);
                return null;
            });

            assertSame(failure,
                    assertThrows(failure.getClass(), () -> connections.executeTransaction(propagation, connection -> {
                        assertIndependentContext(propagation, outer);
                        insert(connection, "inner-" + suffix);
                        return throwFailure(failure);
                    })));

            assertSame(outer, currentTransaction());
            assertEquals(Status.STATUS_ACTIVE, transactionStatus());
            transactionManager.commit();
            assertTrue(rowExists("outer-" + suffix));
            assertEquals(propagation == Propagation.NOT_SUPPORTED, rowExists("inner-" + suffix));
        }
    }

    @Test
    void invalidPropagationFailsBeforeInvokingTheCallback() throws Exception {
        AtomicBoolean invoked = new AtomicBoolean();
        TransactionalException mandatory = assertThrows(TransactionalException.class,
                () -> connections.executeTransaction(Propagation.MANDATORY, connection -> {
                    invoked.set(true);
                    return null;
                }));
        assertInstanceOf(TransactionRequiredException.class, mandatory.getCause());
        assertFalse(invoked.get());
        assertNull(currentTransaction());

        transactionManager.begin();
        Transaction outer = currentTransaction();
        TransactionalException never = assertThrows(TransactionalException.class,
                () -> connections.executeTransaction(Propagation.NEVER, connection -> {
                    invoked.set(true);
                    return null;
                }));
        assertInstanceOf(InvalidTransactionException.class, never.getCause());
        assertFalse(invoked.get());
        assertSame(outer, currentTransaction());
        assertEquals(Status.STATUS_ACTIVE, transactionStatus());
        transactionManager.rollback();
    }

    @ParameterizedTest
    @ValueSource(strings = { "REQUIRED", "REQUIRES_NEW", "SUPPORTS", "NOT_SUPPORTED", "MANDATORY", "NEVER" })
    void rollbackOnlyStillCountsAsAnExistingTransaction(String propagationName) throws Exception {
        Propagation propagation = Propagation.valueOf(propagationName);
        // Isolate transaction propagation from Agroal rejecting connection acquisition in a doomed transaction.
        QuarkusConnectionManager noJdbc = new QuarkusConnectionManager(noJdbcDataSource());
        transactionManager.begin();
        Transaction outer = currentTransaction();
        transactionManager.setRollbackOnly();
        AtomicBoolean invoked = new AtomicBoolean();

        if (propagation == Propagation.NEVER) {
            TransactionalException failure = assertThrows(TransactionalException.class,
                    () -> noJdbc.executeTransaction(propagation, connection -> {
                        invoked.set(true);
                        return null;
                    }));
            assertInstanceOf(InvalidTransactionException.class, failure.getCause());
            assertFalse(invoked.get());
        } else {
            noJdbc.executeTransaction(propagation, connection -> {
                invoked.set(true);
                if (propagation == Propagation.REQUIRES_NEW || propagation == Propagation.NOT_SUPPORTED) {
                    assertIndependentContext(propagation, outer);
                } else {
                    assertSame(outer, currentTransaction());
                    assertEquals(Status.STATUS_MARKED_ROLLBACK, transactionStatus());
                }
                return null;
            });
            assertTrue(invoked.get());
        }

        assertSame(outer, currentTransaction());
        assertEquals(Status.STATUS_MARKED_ROLLBACK, transactionStatus());
        transactionManager.rollback();
    }

    @Test
    void returningAnIncompleteStageStillCompletesTheJdbcTransactionSynchronously() throws SQLException {
        CompletableFuture<String> pending = new CompletableFuture<>();
        try {
            CompletableFuture<String> returned = connections.executeTransaction(Propagation.REQUIRED, connection -> {
                assertEquals(Status.STATUS_ACTIVE, transactionStatus());
                insert(connection, "synchronous");
                return pending;
            });

            assertSame(pending, returned);
            assertFalse(pending.isDone());
            assertNull(currentTransaction());
            assertTrue(rowExists("synchronous"));
        } finally {
            pending.complete("done");
        }
    }

    @Test
    void honorsQuarkusNonRollbackExceptionsForOwnedAndParticipatingTransactions() throws Exception {
        NonRollbackFailure failure = new NonRollbackFailure();
        assertSame(failure, assertThrows(NonRollbackFailure.class,
                () -> connections.executeTransaction(Propagation.REQUIRED, connection -> {
                    insert(connection, "owned-non-rollback");
                    throw failure;
                })));
        assertNull(currentTransaction());
        assertTrue(rowExists("owned-non-rollback"));

        transactionManager.begin();
        Transaction outer = currentTransaction();
        assertSame(failure, assertThrows(NonRollbackFailure.class,
                () -> connections.executeTransaction(Propagation.SUPPORTS, connection -> {
                    insert(connection, "joined-non-rollback");
                    throw failure;
                })));
        assertSame(outer, currentTransaction());
        assertEquals(Status.STATUS_ACTIVE, transactionStatus());
        transactionManager.commit();
        assertTrue(rowExists("joined-non-rollback"));
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void checkedExceptionsFollowNarayanaRollbackRules(boolean rollback) throws SQLException {
        IOException failure = rollback ? new RollbackCheckedFailure() : new IOException("checked business failure");
        ArcUndeclaredThrowableException thrown = assertThrows(ArcUndeclaredThrowableException.class,
                () -> connections.executeTransaction(Propagation.REQUIRED, connection -> {
                    insert(connection, "checked-exception");
                    // Kotlin callbacks can throw checked exceptions directly; simulate that contract from Java.
                    return sneakyThrow(failure);
                }));

        assertSame(failure, thrown.getCause());
        assertNull(currentTransaction());
        assertEquals(!rollback, rowExists("checked-exception"));
    }

    private void assertIndependentContext(Propagation propagation, Transaction outer) {
        if (propagation == Propagation.REQUIRES_NEW) {
            assertNotNull(currentTransaction());
            assertNotSame(outer, currentTransaction());
            assertEquals(Status.STATUS_ACTIVE, transactionStatus());
        } else {
            assertNull(currentTransaction());
        }
    }

    private Transaction currentTransaction() {
        try {
            return transactionManager.getTransaction();
        } catch (SystemException e) {
            throw new AssertionError(e);
        }
    }

    private int transactionStatus() {
        try {
            return transactionManager.getStatus();
        } catch (SystemException e) {
            throw new AssertionError(e);
        }
    }

    private static void insert(Connection connection, String id) {
        try (var statement = connection.prepareStatement("insert into TX_PROPAGATION (ID) values (?)")) {
            statement.setString(1, id);
            assertEquals(1, statement.executeUpdate());
        } catch (SQLException e) {
            throw new AssertionError(e);
        }
    }

    private boolean rowExists(String id) throws SQLException {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("select count(*) from TX_PROPAGATION where ID = ?")) {
            statement.setString(1, id);
            try (var rows = statement.executeQuery()) {
                assertTrue(rows.next());
                return rows.getInt(1) != 0;
            }
        }
    }

    private static Throwable[] failures() {
        return new Throwable[] { new IllegalArgumentException("business failure"), new AssertionError("business error") };
    }

    private static Object throwFailure(Throwable failure) {
        if (failure instanceof Error error) {
            throw error;
        }
        throw (RuntimeException) failure;
    }

    @SuppressWarnings("unchecked")
    private static <R, E extends Throwable> R sneakyThrow(Throwable failure) throws E {
        throw (E) failure;
    }

    private static DataSource noJdbcDataSource() {
        Connection connection = (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                new Class<?>[] { Connection.class }, (proxy, method, args) -> {
                    if (method.getName().equals("close")) {
                        return null;
                    }
                    throw new AssertionError("Unexpected JDBC access: " + method.getName());
                });
        return (DataSource) Proxy.newProxyInstance(DataSource.class.getClassLoader(), new Class<?>[] { DataSource.class },
                (proxy, method, args) -> {
                    if (method.getName().equals("getConnection")) {
                        return connection;
                    }
                    throw new AssertionError("Unexpected datasource access: " + method.getName());
                });
    }

    @Rollback(false)
    public static class NonRollbackFailure extends RuntimeException {
    }

    @Rollback(true)
    public static class RollbackCheckedFailure extends IOException {
    }
}
