package io.quarkiverse.jimmer.it.transaction;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.transaction.Status;
import jakarta.transaction.TransactionManager;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.quarkiverse.jimmer.it.entity.Book;
import io.quarkiverse.jimmer.it.entity.BookDraft;
import io.quarkiverse.jimmer.it.service.IBook;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

@QuarkusTest
class BookSaveTest {

    private static final long NEW_TRANSACTION_BOOK_ID = 7_330_001L;
    private static final long JOINED_TRANSACTION_BOOK_ID = 7_330_002L;
    private static final long SAVED_BOOK_ID = 7_330_003L;

    @Inject
    IBook books;

    @Inject
    TransactionManager transactions;

    @Inject
    javax.sql.DataSource dataSource;

    @AfterEach
    void deleteOwnFixtures() throws Exception {
        if (transactions.getTransaction() != null) {
            transactions.rollback();
        }
        try (var connection = dataSource.getConnection();
                var delete = connection.prepareStatement("delete from book where id in (?, ?, ?)")) {
            delete.setLong(1, NEW_TRANSACTION_BOOK_ID);
            delete.setLong(2, JOINED_TRANSACTION_BOOK_ID);
            delete.setLong(3, SAVED_BOOK_ID);
            delete.executeUpdate();
        }
    }

    @Test
    void savedBookCanBeReadBackFromTheResource() {
        assertEquals(0, storedCount(SAVED_BOOK_ID));
        String name = "Book " + UUID.randomUUID();
        long savedId = given().contentType(ContentType.JSON)
                .body(Map.of("id", SAVED_BOOK_ID, "name", name, "edition", 1, "price", 15, "tenant", "a"))
                .post("/bookResource/book")
                .then().statusCode(200).contentType(ContentType.JSON)
                .body("name", equalTo(name))
                .extract().jsonPath().getLong("id");

        assertEquals(SAVED_BOOK_ID, savedId);
        assertEquals(1, storedCount(savedId));
        given().queryParam("id", savedId)
                .get("/bookResource/book")
                .then().statusCode(200).contentType(ContentType.JSON)
                .body("name", equalTo(name))
                .body("edition", equalTo(1))
                .body("tenant", equalTo("a"));
    }

    @Test
    void failureAfterSaveRollsBackTheInsertedRow() throws Exception {
        assertEquals(0, storedCount(NEW_TRANSACTION_BOOK_ID));
        assertThrows(FailureAfterSave.class, () -> QuarkusTransaction.requiringNew()
                .run(() -> saveThenFail(NEW_TRANSACTION_BOOK_ID)));

        assertEquals(Status.STATUS_NO_TRANSACTION, transactions.getStatus());
        assertEquals(0, storedCount(NEW_TRANSACTION_BOOK_ID));
    }

    @Test
    void joiningExistingMarksRollbackOnlyAndTheRowDisappearsAfterRollback() throws Exception {
        assertEquals(0, storedCount(JOINED_TRANSACTION_BOOK_ID));
        transactions.begin();
        try {
            assertThrows(FailureAfterSave.class, () -> QuarkusTransaction.joiningExisting()
                    .run(() -> saveThenFail(JOINED_TRANSACTION_BOOK_ID)));
            assertEquals(Status.STATUS_MARKED_ROLLBACK, transactions.getStatus());
        } finally {
            transactions.rollback();
        }

        assertEquals(Status.STATUS_NO_TRANSACTION, transactions.getStatus());
        assertEquals(0, storedCount(JOINED_TRANSACTION_BOOK_ID));
    }

    private void saveThenFail(long id) {
        Book book = BookDraft.$.produce(draft -> draft.setId(id).setName("Rollback integration " + id)
                .setEdition(1).setPrice(BigDecimal.ONE).setStoreId(null).setTenant("rollback-test"));
        books.save(book);
        assertEquals(1, storedCount(id));
        throw new FailureAfterSave();
    }

    private static class FailureAfterSave extends RuntimeException {
    }

    private int storedCount(long id) {
        // A tenant-filtered findById could return null even when a failed rollback left the row behind.
        try (var connection = dataSource.getConnection();
                var query = connection.prepareStatement("select count(*) from book where id = ?")) {
            query.setLong(1, id);
            try (var result = query.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        } catch (SQLException exception) {
            throw new AssertionError("Cannot verify the transaction's database contents", exception);
        }
    }
}
