package io.quarkiverse.jimmer.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.babyfish.jimmer.meta.ImmutableType;
import org.babyfish.jimmer.sql.JSqlClient;
import org.babyfish.jimmer.sql.cache.CacheFactory;
import org.babyfish.jimmer.sql.cache.FilterState;
import org.babyfish.jimmer.sql.cache.FilterStateAware;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.jimmer.runtime.SqlClients;
import io.quarkiverse.jimmer.runtime.cache.QuarkusCacheFactory;
import io.quarkiverse.jimmer.test.CacheFactoryOwnershipTest.RecordingCacheFactory;
import io.quarkiverse.jimmer.test.model.CdiBook;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.QuarkusUnitTest;

class CacheFactoryCdiProxyTest {

    @RegisterExtension
    static final QuarkusUnitTest APP = new QuarkusUnitTest()
            .withApplicationRoot(archive -> archive.addPackage(CdiBook.class.getPackage())
                    .addClasses(FactoryBeans.class, RecordingCacheFactory.class)
                    .addAsResource(new StringAsset(CdiBook.class.getName() + "\n"), "META-INF/jimmer/entities"))
            .overrideConfigKey("quarkus.datasource.devservices.enabled", "false")
            .overrideConfigKey("quarkus.redis.devservices.enabled", "false")
            .overrideConfigKey("quarkus.datasource.db-kind", "h2")
            .overrideConfigKey("quarkus.datasource.jdbc.url", "jdbc:h2:mem:cache-factory-proxy;DB_CLOSE_DELAY=-1");

    @Inject
    JSqlClient client;

    @Inject
    CacheFactory factory;

    @Inject
    FactoryBeans beans;

    @Test
    void interfaceTypedProducerReceivesFilterStateAndRetainsItsContextualReference() {
        ClientProxy proxy = assertInstanceOf(ClientProxy.class, factory);
        CacheFactory adapted = QuarkusCacheFactory.adapt(factory);
        assertNull(beans.factory, "Inspecting the proxy must not initialize an unselected factory");
        SqlClients.java(Arc.container(), builder -> builder.addCustomizers(customized -> customized.setCacheFactory(
                new CacheFactory() {
                })));
        assertNull(beans.factory, "A factory replaced by a customizer must remain uninitialized");

        client.getCaches();
        assertTrue(beans.factory.objectCacheCalls > 0);
        assertNotNull(beans.factory.filterState());

        FilterState managedState = beans.factory.filterState();
        int managedCalls = beans.factory.objectCacheCalls;
        SqlClients.java(Arc.container());
        assertNotSame(managedState, beans.factory.filterState());
        assertEquals(managedCalls + 1, beans.factory.objectCacheCalls);

        RecordingCacheFactory original = beans.factory;
        Arc.container().getActiveContext(ApplicationScoped.class).destroy(proxy.arc_bean());

        FilterState replacementState = type -> true;
        ((FilterStateAware) adapted).setFilterState(replacementState);
        adapted.createObjectCache(ImmutableType.get(CdiBook.class));
        assertNotSame(original, beans.factory);
        assertSame(replacementState, beans.factory.filterState());
        assertEquals(1, beans.factory.objectCacheCalls);
    }

    @Singleton
    public static class FactoryBeans {
        RecordingCacheFactory factory;

        @Produces
        @ApplicationScoped
        CacheFactory cacheFactory() {
            return factory = new RecordingCacheFactory();
        }
    }
}
