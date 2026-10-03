package io.quarkiverse.jimmer.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import javax.sql.DataSource;

import jakarta.inject.Singleton;

import org.babyfish.jimmer.sql.JSqlClient;
import org.babyfish.jimmer.sql.dialect.DefaultDialect;
import org.babyfish.jimmer.sql.dialect.Dialect;
import org.babyfish.jimmer.sql.dialect.H2Dialect;
import org.babyfish.jimmer.sql.dialect.MySqlDialect;
import org.babyfish.jimmer.sql.runtime.ConnectionManager;
import org.babyfish.jimmer.sql.runtime.Customizer;
import org.babyfish.jimmer.sql.runtime.JSqlClientImplementor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.jimmer.runtime.SqlClients;
import io.quarkiverse.jimmer.runtime.dialect.DialectDetector;
import io.quarkus.arc.Arc;
import io.quarkus.test.QuarkusUnitTest;

class SqlClientConfigurationOrderTest {

    @RegisterExtension
    static final QuarkusUnitTest APP = new QuarkusUnitTest()
            .withApplicationRoot(archive -> archive.addClasses(ExplicitDialectCustomizer.class, UnusedDialectDetector.class))
            .overrideConfigKey("quarkus.datasource.devservices.enabled", "false")
            .overrideConfigKey("quarkus.redis.devservices.enabled", "false")
            .overrideConfigKey("quarkus.datasource.db-kind", "h2")
            .overrideConfigKey("quarkus.datasource.jdbc.url", "jdbc:h2:mem:client-configuration-order;DB_CLOSE_DELAY=-1")
            .overrideConfigKey("quarkus.datasource.books.db-kind", "h2")
            .overrideConfigKey("quarkus.datasource.books.jdbc.url", "jdbc:h2:mem:client-configuration-books;DB_CLOSE_DELAY=-1")
            .overrideConfigKey("quarkus.jimmer.books.show-sql", "false")
            .overrideConfigKey("quarkus.jimmer.books.trigger-type", "BINLOG_ONLY");

    @Test
    void anExplicitDialectFromACdiCustomizerAvoidsConnectionProbing() {
        AtomicInteger connectionAttempts = new AtomicInteger();
        DataSource unavailableDataSource = (DataSource) Proxy.newProxyInstance(DataSource.class.getClassLoader(),
                new Class<?>[] { DataSource.class }, (proxy, method, args) -> {
                    if (method.getName().equals("getConnection")) {
                        connectionAttempts.incrementAndGet();
                    }
                    throw new AssertionError("The explicit dialect must avoid datasource access: " + method.getName());
                });

        JSqlClientImplementor client = (JSqlClientImplementor) SqlClients.java(Arc.container(), unavailableDataSource, "books");

        assertInstanceOf(H2Dialect.class, client.getDialect());
        assertEquals(0, connectionAttempts.get());
    }

    @Test
    void dialectDetectionUsesTheConnectionManagerChosenByTheLastCustomizer() {
        AtomicInteger managerExecutions = new AtomicInteger();
        AtomicInteger customizations = new AtomicInteger();
        DatabaseMetaData metadata = (DatabaseMetaData) Proxy.newProxyInstance(DatabaseMetaData.class.getClassLoader(),
                new Class<?>[] { DatabaseMetaData.class }, (proxy, method, args) -> {
                    if (method.getName().equals("getDatabaseProductName")) {
                        return "MySQL";
                    }
                    throw new AssertionError("Unexpected metadata access: " + method.getName());
                });
        Connection mysqlConnection = (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                new Class<?>[] { Connection.class }, (proxy, method, args) -> {
                    if (method.getName().equals("getMetaData")) {
                        return metadata;
                    }
                    throw new AssertionError("Unexpected connection access: " + method.getName());
                });
        ConnectionManager replacement = new ConnectionManager() {
            @Override
            public <R> R execute(Connection connection, Function<Connection, R> block) {
                managerExecutions.incrementAndGet();
                return block.apply(connection != null ? connection : mysqlConnection);
            }
        };

        JSqlClientImplementor client = (JSqlClientImplementor) SqlClients.java(Arc.container(), builder -> builder
                .addCustomizers(customizedBuilder -> {
                    customizations.incrementAndGet();
                    // This customizer is appended by the caller after the CDI customizer.
                    customizedBuilder.setDialect(DefaultDialect.INSTANCE);
                    customizedBuilder.setConnectionManager(replacement);
                }));

        assertSame(replacement, client.getConnectionManager());
        assertInstanceOf(MySqlDialect.class, client.getDialect());
        assertEquals(1, customizations.get());
        assertEquals(1, managerExecutions.get());
    }

    @Singleton
    public static class ExplicitDialectCustomizer implements Customizer {
        @Override
        public void customize(JSqlClient.Builder builder) {
            builder.setDialect(new H2Dialect());
        }
    }

    @Singleton
    @io.quarkus.agroal.DataSource("books")
    public static class UnusedDialectDetector implements DialectDetector {
        public UnusedDialectDetector() {
            throw new AssertionError("An explicit dialect must not instantiate a dialect detector");
        }

        @Override
        public Dialect detectDialect(Connection connection) {
            throw new AssertionError("An explicit dialect must not invoke dialect detection");
        }
    }
}
