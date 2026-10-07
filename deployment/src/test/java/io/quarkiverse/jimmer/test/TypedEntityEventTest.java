package io.quarkiverse.jimmer.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.spi.EventMetadata;
import jakarta.enterprise.util.TypeLiteral;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.transaction.TransactionManager;

import org.babyfish.jimmer.meta.ImmutableType;
import org.babyfish.jimmer.sql.JSqlClient;
import org.babyfish.jimmer.sql.event.AssociationEvent;
import org.babyfish.jimmer.sql.event.DatabaseEvent;
import org.babyfish.jimmer.sql.event.EntityEvent;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.jimmer.test.model.CdiBook;
import io.quarkiverse.jimmer.test.model.CdiBookDraft;
import io.quarkiverse.jimmer.test.model.ParserEntity;
import io.quarkiverse.jimmer.test.model.ParserEntityDraft;
import io.quarkiverse.jimmer.test.model.sort.LegacySortBook;
import io.quarkus.agroal.DataSource;
import io.quarkus.test.QuarkusUnitTest;

class TypedEntityEventTest {

    @RegisterExtension
    static final QuarkusUnitTest APP = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addPackage(CdiBook.class.getPackage())
                    .addPackage(LegacySortBook.class.getPackage())
                    .addClasses(Observers.class, Delivery.class)
                    .addAsResource(new StringAsset(CdiBook.class.getName() + "\n" + ParserEntity.class.getName() + "\n"
                            + LegacySortBook.class.getName() + "\n"),
                            "META-INF/jimmer/entities"))
            .overrideConfigKey("quarkus.datasource.devservices.enabled", "false")
            .overrideConfigKey("quarkus.redis.devservices.enabled", "false")
            .overrideConfigKey("quarkus.datasource.db-kind", "h2")
            .overrideConfigKey("quarkus.datasource.jdbc.url", "jdbc:h2:mem:typed-events-default;DB_CLOSE_DELAY=-1")
            .overrideConfigKey("quarkus.datasource.books.db-kind", "h2")
            .overrideConfigKey("quarkus.datasource.books.jdbc.url", "jdbc:h2:mem:typed-events-books;DB_CLOSE_DELAY=-1")
            .overrideConfigKey("quarkus.jimmer.trigger-type", "BOTH")
            .overrideConfigKey("quarkus.jimmer.books.trigger-type", "BOTH")
            .overrideConfigKey("quarkus.jimmer.transaction-cache-operator-fixed-delay", "off");

    @Inject
    JSqlClient defaultClient;

    @Inject
    @DataSource("books")
    JSqlClient namedClient;

    @Inject
    Observers observers;

    @Inject
    TransactionManager transactions;

    @BeforeEach
    void resetObservers() {
        observers.books.clear();
        observers.otherEntities.clear();
        observers.wildcard.clear();
        observers.databaseEvents.clear();
        observers.defaultSource.clear();
        observers.namedSource.clear();
        observers.afterSuccess.clear();
        observers.associations.clear();
    }

    @Test
    void routesConcreteEntityTypesAndPreservesTheOriginalEvent() {
        CdiBook oldBook = CdiBookDraft.$.produce(draft -> draft.setId(1L).setName("Before"));
        CdiBook newBook = CdiBookDraft.$.produce(draft -> draft.setId(1L).setName("After"));
        defaultClient.getTriggers().fireEntityTableChange(oldBook, newBook, null);

        assertEquals(1, observers.books.size());
        assertTrue(observers.otherEntities.isEmpty());
        assertEquals(1, observers.wildcard.size());
        assertEquals(1, observers.databaseEvents.size());
        EntityEvent<CdiBook> bookEvent = observers.books.get(0);
        assertSame(oldBook, bookEvent.getOldEntity());
        assertSame(newBook, bookEvent.getNewEntity());
        assertSame(bookEvent, observers.wildcard.get(0).event());
        assertSame(bookEvent, observers.databaseEvents.get(0));
        assertMetadata(observers.wildcard.get(0), new TypeLiteral<EntityEvent<CdiBook>>() {
        }.getType(), "<default>");

        ParserEntity other = ParserEntityDraft.$.produce(draft -> draft.setId(2L).setName("Other")
                .setNameAndromeda("Andromeda").setChildOrganization("Organization"));
        defaultClient.getTriggers().fireEntityTableChange(null, other, null);

        assertEquals(1, observers.books.size(), "The CdiBook observer must not receive another entity type");
        assertEquals(1, observers.otherEntities.size());
        assertSame(other, observers.otherEntities.get(0).getNewEntity());
        assertEquals(2, observers.wildcard.size());
        assertEquals(2, observers.databaseEvents.size());
        assertSame(observers.otherEntities.get(0), observers.databaseEvents.get(1));
        assertMetadata(observers.wildcard.get(1), new TypeLiteral<EntityEvent<ParserEntity>>() {
        }.getType(), "<default>");
    }

    @Test
    void evictionsResolveTheEntityTypeWithoutAnOldOrNewEntity() {
        defaultClient.getTriggers().fireEntityEvict(ImmutableType.get(CdiBook.class), 3L, null, "typed-eviction");

        assertEquals(1, observers.books.size());
        assertTrue(observers.otherEntities.isEmpty());
        assertEquals(1, observers.wildcard.size());
        assertEquals(1, observers.databaseEvents.size());
        EntityEvent<CdiBook> event = observers.books.get(0);
        assertTrue(event.isEvict());
        assertEquals(3L, event.getId());
        assertEquals("typed-eviction", event.getReason());
        assertThrows(IllegalStateException.class, event::getOldEntity);
        assertThrows(IllegalStateException.class, event::getNewEntity);
        assertMetadata(observers.wildcard.get(0), new TypeLiteral<EntityEvent<CdiBook>>() {
        }.getType(), "<default>");
    }

    @Test
    void preservesDefaultObserversWhileIsolatingDatasourceQualifiedObservers() {
        CdiBook book = CdiBookDraft.$.produce(draft -> draft.setId(4L).setName("Qualified"));
        namedClient.getTriggers().fireEntityTableChange(null, book, null);

        assertEquals(1, observers.books.size());
        assertTrue(observers.defaultSource.isEmpty());
        assertEquals(1, observers.namedSource.size());
        assertEquals(1, observers.databaseEvents.size(), "@Default observers still receive named-source events");
        assertMetadata(observers.wildcard.get(0), new TypeLiteral<EntityEvent<CdiBook>>() {
        }.getType(), "books");

        defaultClient.getTriggers().fireEntityTableChange(null, book, null);
        assertEquals(2, observers.books.size());
        assertEquals(1, observers.defaultSource.size());
        assertEquals(1, observers.namedSource.size());
        assertEquals(2, observers.wildcard.size());
        assertEquals(2, observers.databaseEvents.size());
        assertMetadata(observers.wildcard.get(1), new TypeLiteral<EntityEvent<CdiBook>>() {
        }.getType(), "<default>");
    }

    @Test
    void bothTriggerChannelsPublishEachCallbackOnlyOnce() {
        var binlog = defaultClient.getTriggers();
        var transaction = defaultClient.getTriggers(true);
        assertNotSame(binlog, transaction);
        CdiBook book = CdiBookDraft.$.produce(draft -> draft.setId(5L).setName("Both"));

        binlog.fireEntityTableChange(null, book, null, "binlog");
        assertEquals(1, observers.books.size());
        assertEquals(1, observers.wildcard.size());
        assertEquals(1, observers.databaseEvents.size());

        transaction.fireEntityTableChange(null, book, null, "transaction");
        assertEquals(2, observers.books.size());
        assertEquals(2, observers.wildcard.size());
        assertEquals(2, observers.databaseEvents.size());
        assertEquals(List.of("binlog", "transaction"), observers.books.stream().map(EntityEvent::getReason).toList());
        assertEquals(2, observers.defaultSource.size());
        assertTrue(observers.namedSource.isEmpty());
    }

    @Test
    void typedAfterSuccessObserversReceiveCommittedEventsOnly() throws Exception {
        var triggers = defaultClient.getTriggers(true);
        CdiBook book = CdiBookDraft.$.produce(draft -> draft.setId(6L).setName("Transactional"));
        try {
            transactions.begin();
            triggers.fireEntityTableChange(null, book, null, "committed");
            assertEquals(1, observers.books.size());
            assertTrue(observers.afterSuccess.isEmpty());
            transactions.commit();

            assertEquals(1, observers.afterSuccess.size());
            Delivery committed = observers.afterSuccess.get(0);
            assertSame(observers.books.get(0), committed.event());
            assertEquals("committed", committed.event().getReason());
            assertMetadata(committed, new TypeLiteral<EntityEvent<CdiBook>>() {
            }.getType(), "<default>");

            transactions.begin();
            triggers.fireEntityTableChange(null, book, null, "rolled-back");
            assertEquals(2, observers.books.size());
            assertEquals(1, observers.afterSuccess.size());
            transactions.rollback();

            assertEquals(1, observers.afterSuccess.size());
            assertSame(committed, observers.afterSuccess.get(0));
        } finally {
            if (transactions.getTransaction() != null) {
                transactions.rollback();
            }
        }
    }

    @Test
    void associationEventsKeepTheirExistingTypeAndQualifiers() {
        var parent = ImmutableType.get(LegacySortBook.class).getProp("parent");
        namedClient.getTriggers().fireAssociationEvict(parent, 7L, null, "association");

        assertEquals(1, observers.associations.size());
        Delivery delivery = observers.associations.get(0);
        AssociationEvent event = assertInstanceOf(AssociationEvent.class, delivery.event());
        assertSame(parent, event.getImmutableProp());
        assertEquals(7L, event.getSourceId());
        assertTrue(event.isEvict());
        assertEquals(1, observers.databaseEvents.size());
        assertSame(event, observers.databaseEvents.get(0));
        assertTrue(observers.wildcard.isEmpty());
        assertTrue(observers.books.isEmpty());
        assertMetadata(delivery, AssociationEvent.class, "books");
    }

    private static void assertMetadata(Delivery delivery, Type type, String dataSource) {
        assertEquals(type, delivery.type());
        assertTrue(delivery.qualifiers().contains(Default.Literal.INSTANCE));
        assertTrue(delivery.qualifiers().contains(new DataSource.DataSourceLiteral(dataSource)));
    }

    public record Delivery(DatabaseEvent event, Type type, Set<Annotation> qualifiers) {
    }

    @Singleton
    public static class Observers {
        final List<EntityEvent<CdiBook>> books = new ArrayList<>();
        final List<EntityEvent<ParserEntity>> otherEntities = new ArrayList<>();
        final List<Delivery> wildcard = new ArrayList<>();
        final List<DatabaseEvent> databaseEvents = new ArrayList<>();
        final List<EntityEvent<CdiBook>> defaultSource = new ArrayList<>();
        final List<EntityEvent<CdiBook>> namedSource = new ArrayList<>();
        final List<Delivery> afterSuccess = new ArrayList<>();
        final List<Delivery> associations = new ArrayList<>();

        void onBook(@Observes EntityEvent<CdiBook> event) {
            books.add(event);
        }

        void onOtherEntity(@Observes EntityEvent<ParserEntity> event) {
            otherEntities.add(event);
        }

        void onWildcard(@Observes EntityEvent<?> event, EventMetadata metadata) {
            wildcard.add(new Delivery(event, metadata.getType(), Set.copyOf(metadata.getQualifiers())));
        }

        void onDatabaseEvent(@Observes @Default DatabaseEvent event) {
            databaseEvents.add(event);
        }

        void onDefaultSource(@Observes @DataSource("<default>") EntityEvent<CdiBook> event) {
            defaultSource.add(event);
        }

        void onNamedSource(@Observes @DataSource("books") EntityEvent<CdiBook> event) {
            namedSource.add(event);
        }

        void onAfterSuccess(@Observes(during = TransactionPhase.AFTER_SUCCESS) EntityEvent<CdiBook> event,
                EventMetadata metadata) {
            afterSuccess.add(new Delivery(event, metadata.getType(), Set.copyOf(metadata.getQualifiers())));
        }

        void onAssociation(@Observes AssociationEvent event, EventMetadata metadata) {
            associations.add(new Delivery(event, metadata.getType(), Set.copyOf(metadata.getQualifiers())));
        }
    }
}
