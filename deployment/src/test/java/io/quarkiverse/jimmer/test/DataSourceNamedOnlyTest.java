package io.quarkiverse.jimmer.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.inject.Inject;

import org.babyfish.jimmer.sql.JSqlClient;
import org.babyfish.jimmer.sql.dialect.H2Dialect;
import org.babyfish.jimmer.sql.runtime.JSqlClientImplementor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.agroal.DataSource;
import io.quarkus.test.QuarkusUnitTest;

class DataSourceNamedOnlyTest {

    @RegisterExtension
    static final QuarkusUnitTest APP = new QuarkusUnitTest()
            .withEmptyApplication()
            .overrideConfigKey("quarkus.datasource.devservices.enabled", "false")
            .overrideConfigKey("quarkus.redis.devservices.enabled", "false")
            .overrideConfigKey("quarkus.datasource.jdbc", "false")
            .overrideConfigKey("quarkus.datasource.books.db-kind", "h2")
            .overrideConfigKey("quarkus.datasource.books.jdbc.url", "jdbc:h2:mem:named-only;DB_CLOSE_DELAY=-1")
            .overrideConfigKey("quarkus.datasource.books.jdbc.max-size", "1")
            .overrideConfigKey("quarkus.datasource.books.jdbc.acquisition-timeout", "1s");

    @Inject
    @DataSource("books")
    JSqlClient client;

    @Test
    void namedClientNeedsNoDefaultDataSourceAndDetectsDialectWithOneConnection() {
        JSqlClientImplementor implementation = (JSqlClientImplementor) client;
        assertTrue(implementation.getDialect() instanceof H2Dialect);
        int value = implementation.getConnectionManager().execute(connection -> {
            try (var statement = connection.createStatement(); var result = statement.executeQuery("select 1")) {
                assertTrue(result.next());
                return result.getInt(1);
            } catch (java.sql.SQLException e) {
                throw new AssertionError(e);
            }
        });
        assertEquals(1, value);
        assertNotNull(client.getCaches());
    }
}
