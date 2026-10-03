package io.quarkiverse.jimmer.test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.babyfish.jimmer.sql.JSqlClient;
import org.babyfish.jimmer.sql.cache.TransactionCacheOperator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.jimmer.runtime.cache.impl.TransactionCacheOperatorFlusher;
import io.quarkiverse.jimmer.runtime.java.QuarkusJSqlClientContainer;
import io.quarkus.agroal.DataSource.DataSourceLiteral;
import io.quarkus.arc.Arc;
import io.quarkus.test.QuarkusUnitTest;

class DataSourceInactiveTest {

    @RegisterExtension
    static final QuarkusUnitTest APP = new QuarkusUnitTest()
            .withEmptyApplication()
            .overrideConfigKey("quarkus.datasource.devservices.enabled", "false")
            .overrideConfigKey("quarkus.redis.devservices.enabled", "false")
            .overrideConfigKey("quarkus.datasource.jdbc", "false")
            .overrideConfigKey("quarkus.datasource.disabled.db-kind", "h2")
            .overrideConfigKey("quarkus.datasource.disabled.active", "false")
            .overrideConfigKey("quarkus.datasource.disabled.jdbc.url", "jdbc:h2:mem:inactive-data-source")
            .overrideConfigKey("quarkus.jimmer.disabled.trigger-type", "TRANSACTION_ONLY")
            .overrideConfigKey("quarkus.datasource.optout.db-kind", "h2")
            .overrideConfigKey("quarkus.datasource.optout.jdbc.url", "jdbc:h2:mem:inactive-jimmer")
            .overrideConfigKey("quarkus.jimmer.optout.active", "false")
            .overrideConfigKey("quarkus.jimmer.optout.trigger-type", "TRANSACTION_ONLY");

    @Test
    void inactiveBeansDoNotBreakStartupOrScheduledFlushing() {
        for (String name : new String[] { "disabled", "optout" }) {
            var qualifier = new DataSourceLiteral(name);
            for (Class<?> type : new Class<?>[] { JSqlClient.class, QuarkusJSqlClientContainer.class,
                    TransactionCacheOperator.class }) {
                var handle = Arc.container().select(type, qualifier).getHandle();
                assertNotNull(handle.getBean());
                assertFalse(handle.getBean().isActive());
            }
        }
        Arc.container().instance(TransactionCacheOperatorFlusher.class).get().retry();
    }
}
