package io.quarkiverse.jimmer.test.optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.inject.Inject;
import jakarta.transaction.Status;
import jakarta.transaction.TransactionManager;

import org.babyfish.jimmer.sql.JSqlClient;
import org.babyfish.jimmer.sql.dialect.H2Dialect;
import org.babyfish.jimmer.sql.runtime.JSqlClientImplementor;
import org.babyfish.jimmer.sql.transaction.Propagation;
import org.babyfish.jimmer.sql.transaction.TxConnectionManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.scheduler.Scheduler;
import io.quarkus.test.QuarkusUnitTest;

class HeadlessCoreTest {

    @RegisterExtension
    static final QuarkusUnitTest APP = OptionalIntegrationTestSupport.isolateExcludedDependencies(new QuarkusUnitTest(),
            OptionalIntegrationTestSupport.withoutHttp())
            .withApplicationRoot(archive -> archive.addClass(OptionalIntegrationTestSupport.class))
            .overrideConfigKey("quarkus.datasource.devservices.enabled", "false")
            .overrideConfigKey("quarkus.redis.devservices.enabled", "false")
            .overrideConfigKey("quarkus.datasource.db-kind", "h2")
            .overrideConfigKey("quarkus.datasource.jdbc.url", "jdbc:h2:mem:headless-core")
            .overrideConfigKey("quarkus.jimmer.trigger-type", "TRANSACTION_ONLY")
            .overrideConfigKey("quarkus.jimmer.transaction-cache-operator-fixed-delay", "off");

    @Inject
    JSqlClient client;

    @Inject
    TransactionManager transactions;

    @Inject
    Scheduler scheduler;

    @Test
    void dataAccessAndTransactionsWorkWithoutHttpRestClientOrQuartz() {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        for (String type : new String[] {
                "io.quarkus.vertx.http.runtime.VertxHttpRecorder",
                "io.quarkus.resteasy.reactive.server.runtime.QuarkusResteasyReactiveRequestContext",
                "io.quarkus.rest.client.reactive.QuarkusRestClientBuilder",
                "org.quartz.Scheduler" }) {
            assertThrows(ClassNotFoundException.class, () -> Class.forName(type, false, loader), type);
        }
        assertNotNull(scheduler);
        JSqlClientImplementor sqlClient = (JSqlClientImplementor) client;
        assertTrue(sqlClient.getDialect() instanceof H2Dialect);
        TxConnectionManager connections = (TxConnectionManager) sqlClient.getConnectionManager();
        int result = connections.executeTransaction(Propagation.REQUIRED, connection -> {
            try (var statement = connection.createStatement(); var rows = statement.executeQuery("select 42")) {
                assertEquals(Status.STATUS_ACTIVE, transactions.getStatus());
                assertTrue(rows.next());
                return rows.getInt(1);
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        });
        assertEquals(42, result);
    }
}
