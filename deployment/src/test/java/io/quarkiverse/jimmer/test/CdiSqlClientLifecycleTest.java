package io.quarkiverse.jimmer.test;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Collection;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.babyfish.jimmer.sql.JSqlClient;
import org.babyfish.jimmer.sql.cache.CacheOperator;
import org.babyfish.jimmer.sql.cache.UsedCache;
import org.babyfish.jimmer.sql.event.EntityEvent;
import org.babyfish.jimmer.sql.filter.Filter;
import org.babyfish.jimmer.sql.filter.FilterArgs;
import org.babyfish.jimmer.sql.kt.cfg.KCustomizer;
import org.babyfish.jimmer.sql.kt.cfg.KSqlClientDsl;
import org.babyfish.jimmer.sql.runtime.Customizer;
import org.babyfish.jimmer.sql.runtime.Initializer;
import org.babyfish.jimmer.sql.runtime.JSqlClientImplementor;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.jimmer.runtime.Jimmer;
import io.quarkiverse.jimmer.test.model.CdiBook;
import io.quarkiverse.jimmer.test.model.CdiBookDraft;
import io.quarkiverse.jimmer.test.model.CdiBookProps;
import io.quarkus.agroal.DataSource;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ClientProxy;
import io.quarkus.datasource.common.runtime.DataSourceUtil;
import io.quarkus.test.QuarkusUnitTest;

class CdiSqlClientLifecycleTest {

    @RegisterExtension
    static final QuarkusUnitTest APP = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addPackage(CdiBook.class.getPackage())
                    .addClasses(Probe.class, ClientDependentFilter.class, ClientDependentCustomizer.class,
                            ClientDependentInitializer.class, OtherDataSourceCustomizer.class,
                            UnselectedKotlinCustomizer.class, TrackingCacheOperator.class)
                    .addAsResource(new StringAsset(CdiBook.class.getName() + "\n"), "META-INF/jimmer/entities"))
            .overrideConfigKey("quarkus.datasource.devservices.enabled", "false")
            .overrideConfigKey("quarkus.redis.devservices.enabled", "false")
            .overrideConfigKey("quarkus.datasource.db-kind", "h2")
            .overrideConfigKey("quarkus.datasource.jdbc.url", "jdbc:h2:mem:cdi-client-lifecycle;DB_CLOSE_DELAY=-1")
            .overrideConfigKey("quarkus.jimmer.trigger-type", "BINLOG_ONLY");

    @Inject
    JSqlClient client;

    @Inject
    Probe probe;

    @Test
    void arcOwnsLazyCreationAndAllManagedEntrypointsShareTheSameProxy() {
        assertInstanceOf(ClientProxy.class, client);
        assertInstanceOf(JSqlClientImplementor.class, client);
        assertSame(client, Jimmer.getDefaultJSqlClient());
        assertSame(client, Jimmer.getJSqlClient(DataSourceUtil.DEFAULT_DATASOURCE_NAME));
        assertSame(client, Arc.container().select(JSqlClient.class).get());
        assertSame(client, Arc.container().select(JSqlClientImplementor.class).get());
        assertEquals(0, probe.customizations);
        assertEquals(0, probe.initializations);
        assertEquals(0, probe.filterConstructions);
        assertEquals(0, probe.cacheOperatorCreations);

        client.getCaches();
        assertEquals(23, ((JSqlClientImplementor) client).getDefaultBatchSize());
        assertNotNull(client.getFilters().getFilter(CdiBook.class));
        assertSame(client, probe.filterClient);
        assertSame(client, probe.customizerClient);
        assertSame(client, probe.initializerClient);
        assertSame(ClientProxy.unwrap(client), probe.initializedClient);
        assertEquals(1, probe.filterConstructions);
        assertEquals(1, probe.customizations);
        assertEquals(1, probe.initializations);
        assertEquals(1, probe.cacheOperatorCreations);
        assertSame(probe.cacheOperator, ((JSqlClientImplementor) client).getCacheOperator());
        assertSame(probe.initializedClient, probe.cacheOperator.initializedClient);

        client.getCaches();
        Arc.container().select(JSqlClient.class).get().getTriggers();
        Jimmer.getDefaultJSqlClient().getFilters();
        assertEquals(1, probe.customizations);
        assertEquals(1, probe.initializations);
        assertEquals(1, probe.cacheOperatorCreations);

        CdiBook book = CdiBookDraft.$.produce(draft -> draft.setId(1L).setName("Alpha"));
        client.getTriggers().fireEntityTableChange(null, book, null);
        assertEquals(1, probe.events);
        assertEquals(1L, probe.lastEvent.getId());
        assertSame(book, probe.lastEvent.getNewEntity());
    }

    @Singleton
    public static class Probe {
        int filterConstructions;
        int customizations;
        int initializations;
        int events;
        int cacheOperatorCreations;
        TrackingCacheOperator cacheOperator;
        JSqlClient filterClient;
        JSqlClient customizerClient;
        JSqlClient initializerClient;
        JSqlClient initializedClient;
        EntityEvent<?> lastEvent;

        @Produces
        @Dependent
        CacheOperator cacheOperator() {
            cacheOperatorCreations++;
            cacheOperator = new TrackingCacheOperator();
            return cacheOperator;
        }

        void onEntityChange(@Observes EntityEvent<?> event) {
            events++;
            lastEvent = event;
        }
    }

    @Singleton
    public static class ClientDependentFilter implements Filter<CdiBookProps> {
        public ClientDependentFilter(JSqlClient client, Probe probe) {
            probe.filterConstructions++;
            probe.filterClient = client;
        }

        @Override
        public void filter(FilterArgs<CdiBookProps> args) {
        }
    }

    @Singleton
    public static class ClientDependentCustomizer implements Customizer {
        private final Probe probe;

        public ClientDependentCustomizer(JSqlClient client, Probe probe) {
            this.probe = probe;
            probe.customizerClient = client;
        }

        @Override
        public void customize(JSqlClient.Builder builder) {
            probe.customizations++;
            builder.setDefaultBatchSize(23);
        }
    }

    @Singleton
    public static class ClientDependentInitializer implements Initializer {
        private final Probe probe;

        public ClientDependentInitializer(JSqlClient client, Probe probe) {
            this.probe = probe;
            probe.initializerClient = client;
        }

        @Override
        public void initialize(JSqlClient sqlClient) {
            // Initializers use the provided, constructed client; invoking the injected proxy here would re-enter creation.
            assertEquals(23, ((JSqlClientImplementor) sqlClient).getDefaultBatchSize());
            probe.initializedClient = sqlClient;
            probe.initializations++;
        }
    }

    @Singleton
    @DataSource("other")
    public static class OtherDataSourceCustomizer implements Customizer {
        public OtherDataSourceCustomizer() {
            throw new AssertionError("A datasource-specific SPI must not be instantiated by another datasource");
        }

        @Override
        public void customize(JSqlClient.Builder builder) {
        }
    }

    @Singleton
    public static class UnselectedKotlinCustomizer implements KCustomizer {
        public UnselectedKotlinCustomizer() {
            throw new AssertionError("Java mode must not instantiate Kotlin customizers");
        }

        @Override
        public void customize(KSqlClientDsl dsl) {
        }
    }

    public static class TrackingCacheOperator implements CacheOperator {
        JSqlClient initializedClient;

        @Override
        public void initialize(JSqlClient sqlClient) {
            initializedClient = sqlClient;
        }

        @Override
        public void delete(UsedCache<Object, ?> cache, Object key, Object reason) {
        }

        @Override
        public void deleteAll(UsedCache<Object, ?> cache, Collection<Object> keys, Object reason) {
        }
    }
}
