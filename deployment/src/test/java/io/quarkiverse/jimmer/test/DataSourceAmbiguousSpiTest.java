package io.quarkiverse.jimmer.test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.function.Consumer;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.AmbiguousResolutionException;
import jakarta.enterprise.inject.CreationException;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.babyfish.jimmer.sql.JSqlClient;
import org.babyfish.jimmer.sql.cache.CacheOperator;
import org.babyfish.jimmer.sql.dialect.Dialect;
import org.babyfish.jimmer.sql.dialect.H2Dialect;
import org.babyfish.jimmer.sql.exception.ExecutionException;
import org.babyfish.jimmer.sql.runtime.JSqlClientImplementor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.agroal.DataSource;
import io.quarkus.test.QuarkusUnitTest;

class DataSourceAmbiguousSpiTest {
    @RegisterExtension
    static final QuarkusUnitTest APP = new QuarkusUnitTest()
            .withApplicationRoot(archive -> archive.addClass(Strategies.class))
            .overrideConfigKey("quarkus.datasource.devservices.enabled", "false")
            .overrideConfigKey("quarkus.redis.devservices.enabled", "false")
            .overrideConfigKey("quarkus.datasource.db-kind", "h2")
            .overrideConfigKey("quarkus.datasource.jdbc.url", "jdbc:h2:mem:ambiguous-operators")
            .overrideConfigKey("quarkus.datasource.dialects.db-kind", "h2")
            .overrideConfigKey("quarkus.datasource.dialects.jdbc.url", "jdbc:h2:mem:ambiguous-dialects")
            .overrideConfigKey("quarkus.datasource.builders.db-kind", "h2")
            .overrideConfigKey("quarkus.datasource.builders.jdbc.url", "jdbc:h2:mem:ambiguous-builders");

    @Inject
    JSqlClient defaults;

    @Inject
    @DataSource("dialects")
    JSqlClient dialects;

    @Inject
    @DataSource("builders")
    JSqlClient builders;

    @Test
    void ambiguousSingletonDoesNotSilentlyFallBackToDefault() {
        CreationException failure = assertThrows(CreationException.class,
                () -> ((JSqlClientImplementor) dialects).getDialect());
        // Dialect fallback runs in Jimmer's final customizer, which preserves the CDI resolution failure as its cause.
        ExecutionException customizationFailure = assertInstanceOf(ExecutionException.class, failure.getCause());
        assertInstanceOf(AmbiguousResolutionException.class, customizationFailure.getCause());
    }

    @Test
    void ambiguousBuilderDoesNotSelectAnArbitraryFirstBean() {
        CreationException failure = assertThrows(CreationException.class,
                () -> ((JSqlClientImplementor) builders).getDialect());
        assertInstanceOf(AmbiguousResolutionException.class, failure.getCause());
    }

    @Test
    void operatorsUnderBothDefaultQualifiersAreResolvedTogether() {
        CreationException failure = assertThrows(CreationException.class,
                () -> ((JSqlClientImplementor) defaults).getCacheOperator());
        assertInstanceOf(AmbiguousResolutionException.class, failure.getCause());
    }

    @ApplicationScoped
    public static class Strategies {
        @Produces
        @Singleton
        CacheOperator defaultOperator() {
            throw new AssertionError("An ambiguous operator must not be instantiated");
        }

        @Produces
        @Singleton
        @DataSource("<default>")
        CacheOperator legacyDefaultOperator() {
            throw new AssertionError("An ambiguous operator must not be instantiated");
        }

        @Produces
        @Singleton
        Dialect globalDialect() {
            return new H2Dialect();
        }

        @Produces
        @Singleton
        @DataSource("dialects")
        Dialect firstDialect() {
            return new H2Dialect();
        }

        @Produces
        @Singleton
        @DataSource("dialects")
        Dialect secondDialect() {
            return new H2Dialect();
        }

        @Produces
        @Singleton
        @DataSource("builders")
        Consumer<JSqlClient.Builder> firstBuilder() {
            return builder -> builder.setDefaultBatchSize(3);
        }

        @Produces
        @Singleton
        @DataSource("builders")
        Consumer<JSqlClient.Builder> secondBuilder() {
            return builder -> builder.setDefaultBatchSize(5);
        }
    }
}
