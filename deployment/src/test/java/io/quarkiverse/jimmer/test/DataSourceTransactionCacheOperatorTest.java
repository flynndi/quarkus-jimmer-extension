package io.quarkiverse.jimmer.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.babyfish.jimmer.sql.JSqlClient;
import org.babyfish.jimmer.sql.cache.CacheOperator;
import org.babyfish.jimmer.sql.cache.TransactionCacheOperator;
import org.babyfish.jimmer.sql.runtime.Initializer;
import org.babyfish.jimmer.sql.runtime.JSqlClientImplementor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.jimmer.runtime.SqlClients;
import io.quarkus.agroal.DataSource;
import io.quarkus.agroal.runtime.AgroalDataSourceUtil;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.QuarkusUnitTest;

class DataSourceTransactionCacheOperatorTest {

    @RegisterExtension
    static final QuarkusUnitTest APP = new QuarkusUnitTest()
            .withApplicationRoot(archive -> archive.addClass(InitializationProbe.class))
            .overrideConfigKey("quarkus.datasource.devservices.enabled", "false")
            .overrideConfigKey("quarkus.redis.devservices.enabled", "false")
            .overrideConfigKey("quarkus.datasource.jdbc", "false")
            .overrideConfigKey("quarkus.datasource.books.db-kind", "h2")
            .overrideConfigKey("quarkus.datasource.books.jdbc.url", "jdbc:h2:mem:transaction-cache;DB_CLOSE_DELAY=-1")
            .overrideConfigKey("quarkus.jimmer.books.trigger-type", "TRANSACTION_ONLY");

    @Inject
    @DataSource("books")
    JSqlClient client;

    @Inject
    @DataSource("books")
    InitializationProbe probe;

    @Test
    void bindsTheOperatorToTheRealClientWithoutRecursiveInitialization() {
        assertInstanceOf(ClientProxy.class, client);
        assertEquals(0, probe.initializations);
        var operator = Arc.container().select(TransactionCacheOperator.class, new DataSource.DataSourceLiteral("books")).get();
        assertEquals(0, probe.initializations);
        operator.flush();
        assertEquals(1, probe.initializations);
        CacheOperator selected = ((JSqlClientImplementor) client).getCacheOperator();
        assertNotNull(selected);
        assertSame(operator, selected);
        assertSame(operator, Arc.container().select(CacheOperator.class, new DataSource.DataSourceLiteral("books")).get());
        boolean tableExists = ((JSqlClientImplementor) client).getConnectionManager().execute(connection -> {
            try (var result = connection.getMetaData().getTables(null, null, TransactionCacheOperator.TABLE_NAME, null)) {
                return result.next();
            } catch (java.sql.SQLException e) {
                throw new AssertionError(e);
            }
        });
        assertTrue(tableExists);
        operator.flush();
        assertEquals(1, probe.initializations);
        JSqlClientImplementor manuallyCreated = (JSqlClientImplementor) SqlClients.java(Arc.container(),
                AgroalDataSourceUtil.dataSourceInstance("books").get(), "books");
        assertNull(manuallyCreated.getCacheOperator());
        assertSame(operator, ((JSqlClientImplementor) client).getCacheOperator());
        operator.flush();
    }

    @Singleton
    @DataSource("books")
    public static class InitializationProbe implements Initializer {
        int initializations;

        @Override
        public void initialize(JSqlClient sqlClient) {
            initializations++;
        }
    }
}
