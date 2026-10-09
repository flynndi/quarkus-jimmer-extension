package io.quarkiverse.jimmer.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.babyfish.jimmer.meta.ImmutableType;
import org.babyfish.jimmer.sql.JSqlClient;
import org.babyfish.jimmer.sql.cache.AbstractCacheFactory;
import org.babyfish.jimmer.sql.cache.Cache;
import org.babyfish.jimmer.sql.cache.FilterState;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.jimmer.runtime.SqlClients;
import io.quarkiverse.jimmer.test.model.CdiBook;
import io.quarkus.arc.Arc;
import io.quarkus.test.QuarkusUnitTest;
import kotlin.Unit;

class CacheFactoryOwnershipTest {

    @RegisterExtension
    static final QuarkusUnitTest APP = new QuarkusUnitTest()
            .withApplicationRoot(archive -> archive.addPackage(CdiBook.class.getPackage())
                    .addClasses(RecordingCacheFactory.class, SharedCacheFactory.class)
                    .addAsResource(new StringAsset(CdiBook.class.getName() + "\n"), "META-INF/jimmer/entities"))
            .overrideConfigKey("quarkus.datasource.devservices.enabled", "false")
            .overrideConfigKey("quarkus.redis.devservices.enabled", "false")
            .overrideConfigKey("quarkus.datasource.db-kind", "h2")
            .overrideConfigKey("quarkus.datasource.jdbc.url", "jdbc:h2:mem:cache-factory-ownership;DB_CLOSE_DELAY=-1");

    @Inject
    JSqlClient managedClient;

    @Inject
    SharedCacheFactory sharedFactory;

    @Test
    void managedClientUsesTheCdiCacheFactory() {
        managedClient.getCaches();
        assertNotNull(sharedFactory.filterState());
        assertTrue(sharedFactory.objectCacheCalls > 0);
    }

    @Test
    void manualJavaClientsInheritTheCdiFactoryAndCanOverrideIt() {
        managedClient.getCaches();
        FilterState managedState = sharedFactory.filterState();
        int managedCalls = sharedFactory.objectCacheCalls;

        SqlClients.java(Arc.container());
        FilterState manualState = sharedFactory.filterState();
        int sharedCalls = sharedFactory.objectCacheCalls;
        assertNotSame(managedState, manualState);
        assertTrue(sharedCalls > managedCalls);

        RecordingCacheFactory dedicatedFactory = new RecordingCacheFactory();
        SqlClients.java(Arc.container(), builder -> builder.setCacheFactory(dedicatedFactory));

        assertSame(manualState, sharedFactory.filterState());
        assertEquals(sharedCalls, sharedFactory.objectCacheCalls);
        assertNotNull(dedicatedFactory.filterState());
        assertNotSame(manualState, dedicatedFactory.filterState());
        assertTrue(dedicatedFactory.objectCacheCalls > 0);
    }

    @Test
    void manualKotlinClientsInheritTheCdiFactoryAndCanOverrideIt() {
        managedClient.getCaches();
        FilterState managedState = sharedFactory.filterState();
        int managedCalls = sharedFactory.objectCacheCalls;

        SqlClients.kotlin(Arc.container());
        FilterState manualState = sharedFactory.filterState();
        int sharedCalls = sharedFactory.objectCacheCalls;
        assertNotSame(managedState, manualState);
        assertTrue(sharedCalls > managedCalls);

        RecordingCacheFactory dedicatedFactory = new RecordingCacheFactory();
        SqlClients.kotlin(Arc.container(), dsl -> {
            dsl.setCacheFactory(dedicatedFactory);
            return Unit.INSTANCE;
        });

        assertSame(manualState, sharedFactory.filterState());
        assertEquals(sharedCalls, sharedFactory.objectCacheCalls);
        assertNotNull(dedicatedFactory.filterState());
        assertNotSame(manualState, dedicatedFactory.filterState());
        assertTrue(dedicatedFactory.objectCacheCalls > 0);
    }

    public static class RecordingCacheFactory extends AbstractCacheFactory {
        int objectCacheCalls;

        FilterState filterState() {
            return getFilterState();
        }

        @Override
        public Cache<?, ?> createObjectCache(ImmutableType type) {
            if (type.getJavaClass() == CdiBook.class) {
                objectCacheCalls++;
            }
            return null;
        }
    }

    @Singleton
    public static class SharedCacheFactory extends RecordingCacheFactory {
    }
}
