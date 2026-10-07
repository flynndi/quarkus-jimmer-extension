package io.quarkiverse.jimmer.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.spi.EventMetadata;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.babyfish.jimmer.sql.JSqlClient;
import org.babyfish.jimmer.sql.event.EntityEvent;
import org.babyfish.jimmer.sql.runtime.JSqlClientImplementor;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.jimmer.runtime.SqlClients;
import io.quarkiverse.jimmer.runtime.cdi.QuarkusEventDispatcher;
import io.quarkiverse.jimmer.runtime.cfg.JimmerBuildTimeConfig;
import io.quarkiverse.jimmer.runtime.cfg.JimmerRuntimeConfig;
import io.quarkiverse.jimmer.test.model.CdiBook;
import io.quarkiverse.jimmer.test.model.CdiBookDraft;
import io.quarkus.agroal.DataSource;
import io.quarkus.arc.Arc;
import io.quarkus.test.QuarkusUnitTest;

class DisabledJimmerManualClientTest {

    @RegisterExtension
    static final QuarkusUnitTest APP = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addPackage(CdiBook.class.getPackage())
                    .addClass(EventObserver.class)
                    .addAsResource(new StringAsset(CdiBook.class.getName() + "\n"), "META-INF/jimmer/entities"))
            .overrideConfigKey("quarkus.datasource.devservices.enabled", "false")
            .overrideConfigKey("quarkus.redis.devservices.enabled", "false")
            .overrideConfigKey("quarkus.datasource.db-kind", "h2")
            .overrideConfigKey("quarkus.datasource.jdbc.url", "jdbc:h2:mem:disabled-jimmer-manual")
            .overrideConfigKey("quarkus.jimmer.default-batch-size", "19")
            .overrideConfigKey("quarkus.jimmer.enable", "false");

    @Inject
    EventObserver observer;

    // Without auto-configuration, the application explicitly retains the configuration used by its manual factory.
    @Inject
    JimmerRuntimeConfig runtimeConfig;

    @Inject
    JimmerBuildTimeConfig buildTimeConfig;

    @Test
    void manualClientsStillAccessTheDatabaseAndPublishWildcardEventsWithoutGeneratedBeans() {
        assertFalse(buildTimeConfig.enable());
        assertTrue(Arc.container().select(JSqlClient.class).isUnsatisfied());
        assertTrue(Arc.container().select(QuarkusEventDispatcher.class).isUnsatisfied());

        JSqlClient client = SqlClients.java(Arc.container());
        assertEquals(runtimeConfig.dataSources().get("<default>").defaultBatchSize().orElseThrow(),
                ((JSqlClientImplementor) client).getDefaultBatchSize());
        int value = ((JSqlClientImplementor) client).getConnectionManager().execute(connection -> {
            try (var statement = connection.createStatement(); var rows = statement.executeQuery("select 42")) {
                assertTrue(rows.next());
                return rows.getInt(1);
            } catch (java.sql.SQLException ex) {
                throw new AssertionError(ex);
            }
        });
        assertEquals(42, value);

        CdiBook book = CdiBookDraft.$.produce(draft -> draft.setId(1L).setName("Manual"));
        client.getTriggers().fireEntityTableChange(null, book, null);

        assertEquals(1, observer.events.size());
        assertSame(book, observer.events.get(0).getNewEntity());
        assertTrue(observer.qualifiers.contains(Default.Literal.INSTANCE));
        assertTrue(observer.qualifiers.contains(new DataSource.DataSourceLiteral("<default>")));
        assertTrue(Arc.container().select(JSqlClient.class).isUnsatisfied());
    }

    @Singleton
    public static class EventObserver {
        final List<EntityEvent<?>> events = new ArrayList<>();
        Set<Annotation> qualifiers;

        void onEntity(@Observes EntityEvent<?> event, EventMetadata metadata) {
            events.add(event);
            qualifiers = Set.copyOf(metadata.getQualifiers());
        }
    }
}
