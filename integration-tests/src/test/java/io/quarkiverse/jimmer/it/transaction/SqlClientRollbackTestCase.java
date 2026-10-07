package io.quarkiverse.jimmer.it.transaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.sql.SQLException;

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

@QuarkusTest
class SqlClientRollbackTestCase {

    private static final long NEW_TRANSACTION_BOOK_ID = 7_330_001L;
    private static final long JOINED_TRANSACTION_BOOK_ID = 7_330_002L;

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
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.executeUpdate("delete from book where id in (7330001, 7330002)");
        }
    }

    @Test
    void failedSaveRollsBackTheInsertedRow() throws Exception {
        assertEquals(0, storedCount(NEW_TRANSACTION_BOOK_ID));
        assertThrows(ArithmeticException.class, () -> QuarkusTransaction.requiringNew()
                .run(() -> saveAndVerifyFailure(NEW_TRANSACTION_BOOK_ID)));

        assertEquals(Status.STATUS_NO_TRANSACTION, transactions.getStatus());
        assertEquals(0, storedCount(NEW_TRANSACTION_BOOK_ID));
    }

    @Test
    void joiningExistingMarksRollbackOnlyAndTheRowDisappearsAfterRollback() throws Exception {
        assertEquals(0, storedCount(JOINED_TRANSACTION_BOOK_ID));
        transactions.begin();
        try {
            assertThrows(ArithmeticException.class, () -> QuarkusTransaction.joiningExisting()
                    .run(() -> saveAndVerifyFailure(JOINED_TRANSACTION_BOOK_ID)));
            assertEquals(Status.STATUS_MARKED_ROLLBACK, transactions.getStatus());
        } finally {
            transactions.rollback();
        }

        assertEquals(Status.STATUS_NO_TRANSACTION, transactions.getStatus());
        assertEquals(0, storedCount(JOINED_TRANSACTION_BOOK_ID));
    }

    private void saveAndVerifyFailure(long id) {
        Book book = BookDraft.$.produce(draft -> draft.setId(id).setName("Rollback integration " + id)
                .setEdition(1).setPrice(BigDecimal.ONE).setStoreId(null).setTenant("rollback-test"));
        try {
            books.save(book);
        } catch (ArithmeticException failure) {
            // Prove the SQL insert happened before the service failed, then let Narayana roll it back.
            assertEquals(1, storedCount(id));
            throw failure;
        }
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
