package io.quarkiverse.jimmer.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Set;

import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.spi.EventMetadata;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.transaction.TransactionManager;

import org.babyfish.jimmer.meta.ImmutableType;
import org.babyfish.jimmer.sql.JSqlClient;
import org.babyfish.jimmer.sql.cache.TransactionCacheOperator;
import org.babyfish.jimmer.sql.event.AssociationEvent;
import org.babyfish.jimmer.sql.event.EntityEvent;
import org.babyfish.jimmer.sql.event.Triggers;
import org.babyfish.jimmer.sql.runtime.JSqlClientImplementor;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.jimmer.runtime.SqlClients;
import io.quarkiverse.jimmer.test.model.CdiBook;
import io.quarkiverse.jimmer.test.model.sort.LegacySortBook;
import io.quarkus.agroal.DataSource.DataSourceLiteral;
import io.quarkus.arc.Arc;
import io.quarkus.test.QuarkusUnitTest;

class ManualClientCacheLifecycleTest {

    @RegisterExtension
    static final QuarkusUnitTest APP = new QuarkusUnitTest()
            .withApplicationRoot(archive -> archive.addPackage(CdiBook.class.getPackage())
                    .addPackage(LegacySortBook.class.getPackage())
                    .addClasses(Probe.class, TrackingOperator.class)
                    .addAsResource(new StringAsset(CdiBook.class.getName() + "\n" + LegacySortBook.class.getName() + "\n"),
                            "META-INF/jimmer/entities"))
            .overrideConfigKey("quarkus.datasource.devservices.enabled", "false")
            .overrideConfigKey("quarkus.redis.devservices.enabled", "false")
            .overrideConfigKey("quarkus.scheduler.enabled", "false")
            .overrideConfigKey("quarkus.datasource.db-kind", "h2")
            .overrideConfigKey("quarkus.datasource.jdbc.url", "jdbc:h2:mem:manual-cache-lifecycle;DB_CLOSE_DELAY=-1")
            .overrideConfigKey("quarkus.jimmer.trigger-type", "BOTH");

    @Inject
    JSqlClient managedClient;

    @Inject
    TransactionManager transactions;

    @Inject
    Event<EntityEvent<CdiBook>> events;

    @Inject
    Probe probe;

    @AfterEach
    void cleanUpTransaction() throws Exception {
        if (transactions.getTransaction() != null) {
            transactions.rollback();
        }
    }

    @Test
    void manualClientEventsRemainObservableWhileManagedTriggersAndExplicitNotificationsFlush() throws Exception {
        JSqlClient manualJava = SqlClients.java(Arc.container());
        JSqlClient manualKotlin = SqlClients.kotlin(Arc.container()).getJavaClient();
        assertEquals(0, probe.operatorCreations);

        for (JSqlClient client : List.of(manualJava, manualKotlin)) {
            transactions.begin();
            client.getTriggers(true).fireEntityEvict(ImmutableType.get(CdiBook.class), 1L, null, "manual-client");
            client.getTriggers(true).fireAssociationEvict(ImmutableType.get(LegacySortBook.class).getProp("parent"), 1L,
                    null, "manual-client");
            transactions.commit();
        }
        assertEquals(2, probe.bookEvents);
        assertEquals(2, probe.associationEvents);
        assertEquals(0, probe.operatorCreations, "Manual events must not initialize the CDI client or its operator");
        assertEquals(0, probe.flushes);

        transactions.begin();
        events.select(new DataSourceLiteral("<default>"))
                .fire(EntityEvent.evict(ImmutableType.get(CdiBook.class), 1L, null, "application-event"));
        assertEquals(0, probe.flushes);
        transactions.commit();
        assertEquals(3, probe.bookEvents);
        assertEquals(1, probe.operatorCreations, "An explicit datasource-qualified notification selects its operator");
        assertEquals(1, probe.flushes);

        Triggers binlog = managedClient.getTriggers();
        Triggers transaction = managedClient.getTriggers(true);
        assertEquals(1, probe.operatorCreations);
        assertEquals(1, probe.operatorInitializations);
        int committed = 1;
        for (Triggers channel : List.of(binlog, transaction)) {
            transactions.begin();
            channel.fireEntityEvict(ImmutableType.get(CdiBook.class), 1L, null, "managed-client");
            channel.fireEntityEvict(ImmutableType.get(CdiBook.class), 2L, null, "managed-client");
            assertEquals(committed, probe.flushes);
            transactions.commit();
            assertEquals(++committed, probe.flushes, "Each channel flushes once per committed transaction");
        }
        assertEquals(7, probe.bookEvents);
    }

    @Singleton
    public static class Probe {
        int operatorCreations;
        int operatorInitializations;
        int flushes;
        int bookEvents;
        int associationEvents;

        @Produces
        @Singleton
        TransactionCacheOperator operator() {
            operatorCreations++;
            return new TrackingOperator(this);
        }

        void onBook(@Observes EntityEvent<CdiBook> event, EventMetadata metadata) {
            assertPublicQualifiers(metadata);
            bookEvents++;
        }

        void onAssociation(@Observes AssociationEvent event, EventMetadata metadata) {
            assertPublicQualifiers(metadata);
            associationEvents++;
        }

        private static void assertPublicQualifiers(EventMetadata metadata) {
            assertEquals(Set.of(Any.Literal.INSTANCE, Default.Literal.INSTANCE, new DataSourceLiteral("<default>")),
                    metadata.getQualifiers());
        }
    }

    public static class TrackingOperator extends TransactionCacheOperator {
        private final Probe probe;

        TrackingOperator(Probe probe) {
            this.probe = probe;
        }

        @Override
        protected void onInitialize(JSqlClientImplementor sqlClient) {
            super.onInitialize(sqlClient);
            probe.operatorInitializations++;
        }

        @Override
        public void flush() {
            probe.flushes++;
        }
    }
}
