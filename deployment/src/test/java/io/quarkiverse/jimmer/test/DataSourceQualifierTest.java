package io.quarkiverse.jimmer.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.util.function.Consumer;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.babyfish.jimmer.sql.JSqlClient;
import org.babyfish.jimmer.sql.dialect.Dialect;
import org.babyfish.jimmer.sql.dialect.H2Dialect;
import org.babyfish.jimmer.sql.dialect.MySqlDialect;
import org.babyfish.jimmer.sql.dialect.PostgresDialect;
import org.babyfish.jimmer.sql.runtime.JSqlClientImplementor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.agroal.DataSource;
import io.quarkus.test.QuarkusUnitTest;

class DataSourceQualifierTest {
    @RegisterExtension
    static final QuarkusUnitTest APP = new QuarkusUnitTest()
            .withApplicationRoot(archive -> archive.addClass(Strategies.class))
            .overrideConfigKey("quarkus.datasource.devservices.enabled", "false")
            .overrideConfigKey("quarkus.redis.devservices.enabled", "false")
            .overrideConfigKey("quarkus.datasource.db-kind", "h2")
            .overrideConfigKey("quarkus.datasource.jdbc.url", "jdbc:h2:mem:qualified-default")
            .overrideConfigKey("quarkus.datasource.books.db-kind", "h2")
            .overrideConfigKey("quarkus.datasource.books.jdbc.url", "jdbc:h2:mem:qualified-books")
            .overrideConfigKey("quarkus.datasource.other.db-kind", "h2")
            .overrideConfigKey("quarkus.datasource.other.jdbc.url", "jdbc:h2:mem:qualified-other");

    @Inject
    JSqlClient defaults;

    @Inject
    @DataSource("books")
    JSqlClient books;

    @Inject
    @DataSource("other")
    JSqlClient other;

    @Test
    void namedSingletonAndBuilderOverrideGlobalDefaults() {
        assertInstanceOf(PostgresDialect.class, ((JSqlClientImplementor) books).getDialect());
        assertEquals(17, ((JSqlClientImplementor) books).getDefaultBatchSize());
        assertInstanceOf(H2Dialect.class, ((JSqlClientImplementor) other).getDialect());
        assertEquals(13, ((JSqlClientImplementor) other).getDefaultBatchSize());
        assertInstanceOf(MySqlDialect.class, ((JSqlClientImplementor) defaults).getDialect());
        assertEquals(19, ((JSqlClientImplementor) defaults).getDefaultBatchSize());
    }

    @ApplicationScoped
    public static class Strategies {
        @Produces
        @Singleton
        Dialect globalDialect() {
            return new H2Dialect();
        }

        @Produces
        @Singleton
        @DataSource("books")
        Dialect booksDialect() {
            return new PostgresDialect();
        }

        @Produces
        @Singleton
        @DataSource("<default>")
        Dialect explicitlyQualifiedDefaultDialect() {
            return new MySqlDialect();
        }

        @Produces
        @Singleton
        @DataSource("<default>")
        Consumer<JSqlClient.Builder> explicitlyQualifiedDefaultBuilder() {
            return builder -> builder.setDefaultBatchSize(19);
        }

        @Produces
        @Singleton
        Consumer<JSqlClient.Builder> globalBuilder() {
            return builder -> builder.setDefaultBatchSize(13);
        }

        @Produces
        @Singleton
        @DataSource("books")
        Consumer<JSqlClient.Builder> booksBuilder() {
            return builder -> builder.setDefaultBatchSize(17);
        }
    }
}
