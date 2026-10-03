package io.quarkiverse.jimmer.test;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.babyfish.jimmer.sql.JSqlClient;
import org.babyfish.jimmer.sql.event.EntityEvent;
import org.babyfish.jimmer.sql.kt.KSqlClient;
import org.babyfish.jimmer.sql.kt.cfg.KCustomizer;
import org.babyfish.jimmer.sql.kt.cfg.KInitializer;
import org.babyfish.jimmer.sql.kt.cfg.KSqlClientDsl;
import org.babyfish.jimmer.sql.runtime.Customizer;
import org.babyfish.jimmer.sql.runtime.JSqlClientImplementor;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.jimmer.runtime.Jimmer;
import io.quarkiverse.jimmer.runtime.kotlin.QuarkusKSqlClientContainer;
import io.quarkiverse.jimmer.test.model.CdiBook;
import io.quarkiverse.jimmer.test.model.CdiBookDraft;
import io.quarkus.agroal.DataSource;
import io.quarkus.arc.ClientProxy;
import io.quarkus.datasource.common.runtime.DataSourceUtil;
import io.quarkus.test.QuarkusUnitTest;

class CdiKotlinSqlClientLifecycleTest {

    @RegisterExtension
    static final QuarkusUnitTest APP = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addPackage(CdiBook.class.getPackage())
                    .addClasses(Probe.class, ClientDependentCustomizer.class, ClientDependentInitializer.class,
                            OtherDataSourceCustomizer.class, UnselectedJavaCustomizer.class)
                    .addAsResource(new StringAsset(CdiBook.class.getName() + "\n"), "META-INF/jimmer/entities"))
            .overrideConfigKey("quarkus.jimmer.language", "kotlin")
            .overrideConfigKey("quarkus.datasource.devservices.enabled", "false")
            .overrideConfigKey("quarkus.redis.devservices.enabled", "false")
            .overrideConfigKey("quarkus.datasource.db-kind", "h2")
            .overrideConfigKey("quarkus.datasource.jdbc.url", "jdbc:h2:mem:cdi-kotlin-client-lifecycle;DB_CLOSE_DELAY=-1")
            .overrideConfigKey("quarkus.jimmer.trigger-type", "BINLOG_ONLY");

    @Inject
    KSqlClient client;

    @Inject
    QuarkusKSqlClientContainer container;

    @Inject
    Probe probe;

    @Test
    void kotlinClientUsesTheSameArcLifecycleAndRegistersTheEventBridgeOnce() {
        assertInstanceOf(ClientProxy.class, client);
        assertSame(client, container.getKSqlClient());
        assertSame(client, Jimmer.getDefaultKSqlClient());
        assertSame(client, Jimmer.getKSqlClient(DataSourceUtil.DEFAULT_DATASOURCE_NAME));
        assertSame(container, Jimmer.getKSqlClientContainer(DataSourceUtil.DEFAULT_DATASOURCE_NAME));
        assertEquals(0, probe.customizations);
        assertEquals(0, probe.initializations);

        JSqlClientImplementor javaClient = client.getJavaClient();
        assertEquals(29, javaClient.getDefaultBatchSize());
        assertSame(client, probe.customizerClient);
        assertSame(client, probe.initializerClient);
        assertSame(javaClient, probe.initializedClient);
        assertSame(javaClient, container.getKSqlClient().getJavaClient());
        assertSame(javaClient, Jimmer.getDefaultKSqlClient().getJavaClient());
        assertEquals(1, probe.customizations);
        assertEquals(1, probe.initializations);

        javaClient.getCaches();
        client.getJavaClient().getTriggers();
        assertEquals(1, probe.customizations);
        assertEquals(1, probe.initializations);

        CdiBook book = CdiBookDraft.$.produce(draft -> draft.setId(2L).setName("Beta"));
        javaClient.getTriggers().fireEntityTableChange(null, book, null);
        assertEquals(1, probe.events);
        assertEquals(2L, probe.lastEvent.getId());
        assertSame(book, probe.lastEvent.getNewEntity());
    }

    @Singleton
    public static class Probe {
        int customizations;
        int initializations;
        int events;
        KSqlClient customizerClient;
        KSqlClient initializerClient;
        JSqlClientImplementor initializedClient;
        EntityEvent<?> lastEvent;

        void onEntityChange(@Observes EntityEvent<?> event) {
            events++;
            lastEvent = event;
        }
    }

    @Singleton
    public static class ClientDependentCustomizer implements KCustomizer {
        private final Probe probe;

        public ClientDependentCustomizer(KSqlClient client, Probe probe) {
            this.probe = probe;
            probe.customizerClient = client;
        }

        @Override
        public void customize(KSqlClientDsl dsl) {
            probe.customizations++;
            dsl.getJavaBuilder().setDefaultBatchSize(29);
        }
    }

    @Singleton
    public static class ClientDependentInitializer implements KInitializer {
        private final Probe probe;

        public ClientDependentInitializer(KSqlClient client, Probe probe) {
            this.probe = probe;
            probe.initializerClient = client;
        }

        @Override
        public void initialize(KSqlClient sqlClient) {
            assertEquals(29, sqlClient.getJavaClient().getDefaultBatchSize());
            probe.initializedClient = sqlClient.getJavaClient();
            probe.initializations++;
        }
    }

    @Singleton
    @DataSource("other")
    public static class OtherDataSourceCustomizer implements KCustomizer {
        public OtherDataSourceCustomizer() {
            throw new AssertionError("A datasource-specific SPI must not be instantiated by another datasource");
        }

        @Override
        public void customize(KSqlClientDsl dsl) {
        }
    }

    @Singleton
    public static class UnselectedJavaCustomizer implements Customizer {
        public UnselectedJavaCustomizer() {
            throw new AssertionError("Kotlin mode must not instantiate Java customizers");
        }

        @Override
        public void customize(JSqlClient.Builder builder) {
        }
    }
}
