package io.quarkiverse.jimmer.test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.inject.Inject;

import org.babyfish.jimmer.sql.cache.TransactionCacheOperator;
import org.babyfish.jimmer.sql.dialect.H2Dialect;
import org.babyfish.jimmer.sql.kt.KSqlClient;
import org.babyfish.jimmer.sql.runtime.JSqlClientImplementor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.jimmer.runtime.cache.impl.TransactionCacheOperatorFlusher;
import io.quarkiverse.jimmer.runtime.kotlin.QuarkusKSqlClientContainer;
import io.quarkus.agroal.DataSource;
import io.quarkus.arc.Arc;
import io.quarkus.test.QuarkusUnitTest;

class DataSourceKotlinTest {
    @RegisterExtension
    static final QuarkusUnitTest APP = new QuarkusUnitTest()
            .withEmptyApplication()
            .overrideConfigKey("quarkus.jimmer.language", "kotlin")
            .overrideConfigKey("quarkus.datasource.devservices.enabled", "false")
            .overrideConfigKey("quarkus.redis.devservices.enabled", "false")
            .overrideConfigKey("quarkus.datasource.jdbc", "false")
            .overrideConfigKey("quarkus.datasource.books.db-kind", "h2")
            .overrideConfigKey("quarkus.datasource.books.jdbc.url", "jdbc:h2:mem:kotlin-client;DB_CLOSE_DELAY=-1")
            .overrideConfigKey("quarkus.datasource.books.jdbc.max-size", "1")
            .overrideConfigKey("quarkus.jimmer.books.trigger-type", "TRANSACTION_ONLY")
            .overrideConfigKey("quarkus.datasource.disabled.db-kind", "h2")
            .overrideConfigKey("quarkus.datasource.disabled.active", "false")
            .overrideConfigKey("quarkus.jimmer.disabled.trigger-type", "TRANSACTION_ONLY");

    @Inject
    @DataSource("books")
    KSqlClient client;

    @Test
    void kotlinClientsShareTheDatasourceLifecycleAndCacheBinding() {
        JSqlClientImplementor javaClient = client.getJavaClient();
        assertTrue(javaClient.getDialect() instanceof H2Dialect);
        assertSame(javaClient.getCacheOperator(),
                Arc.container().select(TransactionCacheOperator.class, new DataSource.DataSourceLiteral("books")).get());
        for (Class<?> type : new Class<?>[] { KSqlClient.class, QuarkusKSqlClientContainer.class,
                TransactionCacheOperator.class }) {
            assertFalse(Arc.container().select(type, new DataSource.DataSourceLiteral("disabled"))
                    .getHandle().getBean().isActive());
        }
        Arc.container().instance(TransactionCacheOperatorFlusher.class).get().retry();
    }
}
