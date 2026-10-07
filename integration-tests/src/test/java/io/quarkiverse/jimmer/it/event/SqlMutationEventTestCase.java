package io.quarkiverse.jimmer.it.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.babyfish.jimmer.sql.JSqlClient;
import org.babyfish.jimmer.sql.ast.mutation.SaveMode;
import org.babyfish.jimmer.sql.event.AssociationEvent;
import org.babyfish.jimmer.sql.event.EntityEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkiverse.jimmer.it.entity.Book;
import io.quarkiverse.jimmer.it.entity.BookDraft;
import io.quarkiverse.jimmer.it.entity.BookProps;
import io.quarkiverse.jimmer.it.entity.BookStoreProps;
import io.quarkiverse.jimmer.it.entity.BookTable;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class SqlMutationEventTestCase {

    private static final long BOOK_ID = 7_320_011L;
    private static final long FIRST_STORE_ID = 7_320_001L;
    private static final long SECOND_STORE_ID = 7_320_002L;

    @Inject
    JSqlClient sqlClient;

    @Inject
    javax.sql.DataSource dataSource;

    @Inject
    EventRecorder events;

    @BeforeEach
    void insertOwnStores() throws SQLException {
        events.clear();
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.executeUpdate("""
                    insert into book_store(id, name, created_time, modified_time) values
                    (7320001, 'Event integration first store', current_timestamp, current_timestamp),
                    (7320002, 'Event integration second store', current_timestamp, current_timestamp)
                    """);
        }
    }

    @AfterEach
    void deleteOwnFixtures() throws SQLException {
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.executeUpdate("delete from book where id = 7320011");
            statement.executeUpdate("delete from book_store where id in (7320001, 7320002)");
        } finally {
            events.clear();
        }
    }

    @Test
    void committedSqlMutationsPublishTypedEntityAndAssociationEvents() throws SQLException {
        Book book = BookDraft.$.produce(draft -> draft.setId(BOOK_ID)
                .setName("Event integration book").setEdition(1).setPrice(new BigDecimal("10.00"))
                .setTenant("a").setStoreId(FIRST_STORE_ID));
        QuarkusTransaction.requiringNew().run(() -> sqlClient.saveCommand(book).setMode(SaveMode.INSERT_ONLY).execute());

        assertEquals(1, events.books.size());
        EntityEvent<Book> inserted = events.books.get(0);
        assertNull(inserted.getOldEntity());
        assertNotNull(inserted.getNewEntity());
        assertEquals(BOOK_ID, inserted.getNewEntity().id());
        assertEquals(FIRST_STORE_ID, inserted.getNewEntity().storeId());
        assertEquals(1, events.bookStores.size());
        assertAssociation(events.bookStores.get(0), BOOK_ID, null, FIRST_STORE_ID);
        assertEquals(1, events.storeBooks.size());
        assertAssociation(events.storeBooks.get(0), FIRST_STORE_ID, null, BOOK_ID);
        assertStoredStore(FIRST_STORE_ID);

        events.clear();
        var table = BookTable.$;
        QuarkusTransaction.requiringNew().run(() -> assertEquals(1, sqlClient.createUpdate(table)
                .set(table.storeId(), SECOND_STORE_ID)
                .where(table.id().eq(BOOK_ID))
                .execute()));

        assertEquals(1, events.books.size());
        EntityEvent<Book> updated = events.books.get(0);
        assertEquals(FIRST_STORE_ID, updated.getOldEntity().storeId());
        assertEquals(SECOND_STORE_ID, updated.getNewEntity().storeId());
        assertEquals(1, events.bookStores.size());
        assertAssociation(events.bookStores.get(0), BOOK_ID, FIRST_STORE_ID, SECOND_STORE_ID);
        assertEquals(2, events.storeBooks.size());
        assertAssociation(events.storeBooks.stream().filter(event -> event.getSourceId().equals(FIRST_STORE_ID))
                .findFirst().orElseThrow(), FIRST_STORE_ID, BOOK_ID, null);
        assertAssociation(events.storeBooks.stream().filter(event -> event.getSourceId().equals(SECOND_STORE_ID))
                .findFirst().orElseThrow(), SECOND_STORE_ID, null, BOOK_ID);
        assertStoredStore(SECOND_STORE_ID);
    }

    private void assertStoredStore(long expectedStoreId) throws SQLException {
        try (var connection = dataSource.getConnection();
                var query = connection.prepareStatement("select store_id from book where id = ?")) {
            query.setLong(1, BOOK_ID);
            try (var result = query.executeQuery()) {
                assertTrue(result.next());
                assertEquals(expectedStoreId, result.getLong(1));
            }
        }
    }

    private static void assertAssociation(AssociationEvent event, long sourceId, Long detachedId, Long attachedId) {
        assertEquals(sourceId, event.getSourceId());
        assertEquals(detachedId, event.getDetachedTargetId());
        assertEquals(attachedId, event.getAttachedTargetId());
    }

    @Singleton
    public static class EventRecorder {
        final List<EntityEvent<Book>> books = new CopyOnWriteArrayList<>();
        final List<AssociationEvent> bookStores = new CopyOnWriteArrayList<>();
        final List<AssociationEvent> storeBooks = new CopyOnWriteArrayList<>();

        void onBook(@Observes EntityEvent<Book> event) {
            if (event.getId().equals(BOOK_ID)) {
                books.add(event);
            }
        }

        void onAssociation(@Observes AssociationEvent event) {
            if (event.isChanged(BookProps.STORE) && event.getSourceId().equals(BOOK_ID)) {
                bookStores.add(event);
            } else if (event.isChanged(BookStoreProps.BOOKS)
                    && (event.getSourceId().equals(FIRST_STORE_ID) || event.getSourceId().equals(SECOND_STORE_ID))) {
                storeBooks.add(event);
            }
        }

        void clear() {
            books.clear();
            bookStores.clear();
            storeBooks.clear();
        }
    }
}
