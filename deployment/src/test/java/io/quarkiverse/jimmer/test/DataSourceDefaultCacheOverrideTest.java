package io.quarkiverse.jimmer.test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.Collection;

import jakarta.enterprise.inject.Default;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.babyfish.jimmer.sql.JSqlClient;
import org.babyfish.jimmer.sql.cache.CacheOperator;
import org.babyfish.jimmer.sql.cache.UsedCache;
import org.babyfish.jimmer.sql.runtime.JSqlClientImplementor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.agroal.DataSource;
import io.quarkus.arc.Arc;
import io.quarkus.arc.DefaultBean;
import io.quarkus.test.QuarkusUnitTest;

class DataSourceDefaultCacheOverrideTest {
    @RegisterExtension
    static final QuarkusUnitTest APP = new QuarkusUnitTest()
            .withApplicationRoot(archive -> archive.addClass(UserOperator.class))
            .overrideConfigKey("quarkus.datasource.devservices.enabled", "false")
            .overrideConfigKey("quarkus.redis.devservices.enabled", "false")
            .overrideConfigKey("quarkus.datasource.db-kind", "h2")
            .overrideConfigKey("quarkus.datasource.jdbc.url",
                    "jdbc:h2:mem:DataSourceDefaultCacheOverrideTest;DB_CLOSE_DELAY=-1")
            .overrideConfigKey("quarkus.jimmer.trigger-type", "TRANSACTION_ONLY");

    @Inject
    JSqlClient client;

    @Test
    void resolvesADefaultBeanWithBothDefaultQualifiersOnlyOnce() {
        UserOperator expected = Arc.container().instance(UserOperator.class).get();
        assertSame(expected, ((JSqlClientImplementor) client).getCacheOperator());
        assertSame(expected, Arc.container().instance(UserOperator.class, new DataSource.DataSourceLiteral("<default>"))
                .get());
        assertNotNull(expected.initializedWith);
    }

    @Singleton
    @DefaultBean
    @Default
    @DataSource("<default>")
    public static class UserOperator implements CacheOperator {
        JSqlClient initializedWith;

        @Override
        public void initialize(JSqlClient sqlClient) {
            initializedWith = sqlClient;
        }

        @Override
        public void delete(UsedCache<Object, ?> cache, Object key, Object reason) {
        }

        @Override
        public void deleteAll(UsedCache<Object, ?> cache, Collection<Object> keys, Object reason) {
        }
    }
}
