package io.quarkiverse.jimmer.test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.Collection;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.babyfish.jimmer.sql.JSqlClient;
import org.babyfish.jimmer.sql.cache.CacheOperator;
import org.babyfish.jimmer.sql.cache.TransactionCacheOperator;
import org.babyfish.jimmer.sql.cache.UsedCache;
import org.babyfish.jimmer.sql.cache.spi.AbstractCacheOperator;
import org.babyfish.jimmer.sql.kt.KSqlClient;
import org.babyfish.jimmer.sql.runtime.JSqlClientImplementor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.jimmer.runtime.SqlClients;
import io.quarkus.agroal.DataSource;
import io.quarkus.arc.Arc;
import io.quarkus.test.QuarkusUnitTest;
import kotlin.Unit;

class DataSourceCacheOverrideTest {

    @RegisterExtension
    static final QuarkusUnitTest APP = new QuarkusUnitTest()
            .withApplicationRoot(archive -> archive.addClass(CustomOperator.class))
            .overrideConfigKey("quarkus.datasource.devservices.enabled", "false")
            .overrideConfigKey("quarkus.redis.devservices.enabled", "false")
            .overrideConfigKey("quarkus.datasource.jdbc", "false")
            .overrideConfigKey("quarkus.datasource.books.db-kind", "h2")
            .overrideConfigKey("quarkus.datasource.books.jdbc.url", "jdbc:h2:mem:cache-override;DB_CLOSE_DELAY=-1")
            .overrideConfigKey("quarkus.jimmer.books.trigger-type", "TRANSACTION_ONLY");

    @Inject
    @DataSource("books")
    JSqlClient client;

    @Test
    void customOperatorOverridesTheDefaultWithoutAmbiguity() {
        var qualifier = new DataSource.DataSourceLiteral("books");
        CustomOperator custom = Arc.container().select(CustomOperator.class, qualifier).get();
        assertSame(custom, ((JSqlClientImplementor) client).getCacheOperator());
        assertSame(custom, Arc.container().select(CacheOperator.class, qualifier).get());
        assertNotNull(custom.initializedWith);
        // The unused default operator must not attempt to flush without having been initialized by Jimmer.
        Arc.container().select(TransactionCacheOperator.class, qualifier).get().flush();
    }

    @Test
    void manualJavaClientsDoNotInheritTheManagedOperatorButAcceptAnExplicitOne() {
        CustomOperator managed = (CustomOperator) ((JSqlClientImplementor) client).getCacheOperator();
        JSqlClient initializedWith = managed.initializedWith;

        JSqlClientImplementor automatic = (JSqlClientImplementor) SqlClients.java(Arc.container(), null, "books");
        assertNull(automatic.getCacheOperator());

        CustomOperator independent = new CustomOperator();
        JSqlClientImplementor explicit = (JSqlClientImplementor) SqlClients.java(Arc.container(), null, "books",
                builder -> builder.setCacheOperator(independent));
        assertSame(independent, explicit.getCacheOperator());
        assertSame(explicit, independent.initializedWith);
        assertSame(initializedWith, managed.initializedWith);
    }

    @Test
    void manualKotlinClientsDoNotInheritTheManagedOperatorButAcceptAnExplicitOne() {
        CustomOperator managed = (CustomOperator) ((JSqlClientImplementor) client).getCacheOperator();
        JSqlClient initializedWith = managed.initializedWith;

        KSqlClient automatic = SqlClients.kotlin(Arc.container(), null, "books");
        assertNull(automatic.getJavaClient().getCacheOperator());

        CustomOperator independent = new CustomOperator();
        KSqlClient explicit = SqlClients.kotlin(Arc.container(), null, "books", dsl -> {
            dsl.getJavaBuilder().setCacheOperator(independent);
            return Unit.INSTANCE;
        });
        assertSame(independent, explicit.getJavaClient().getCacheOperator());
        assertSame(explicit.getJavaClient(), independent.initializedWith);
        assertSame(initializedWith, managed.initializedWith);
    }

    @Singleton
    @DataSource("books")
    public static class CustomOperator extends AbstractCacheOperator {
        JSqlClient initializedWith;

        @Override
        protected void onInitialize(JSqlClientImplementor sqlClient) {
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
