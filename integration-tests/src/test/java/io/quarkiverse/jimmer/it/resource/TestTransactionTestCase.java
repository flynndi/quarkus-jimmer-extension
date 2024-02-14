package io.quarkiverse.jimmer.it.resource;

import java.math.BigDecimal;

import jakarta.inject.Inject;
import jakarta.transaction.*;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.quarkiverse.jimmer.it.entity.Book;
import io.quarkiverse.jimmer.it.entity.Objects;
import io.quarkiverse.jimmer.it.service.IBook;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class TestTransactionTestCase {

    @Inject
    IBook iBook;

    @Inject
    TransactionManager transactionManager;

    @Test
    @TestTransaction
    public void testUserTransaction() throws Exception {
        Assertions.assertEquals(Status.STATUS_ACTIVE, transactionManager.getStatus());
    }

    @Test
    @TestTransaction
    public void testTransactionRollBack() {
        int id = 23;
        Book book = Objects.createBook(draft -> {
            draft.setId(id);
            draft.setName("Transactional");
            draft.setPrice(new BigDecimal("1"));
            draft.setEdition(1);
            draft.setStoreId(2L);
        });
        Assertions.assertThrows(ArithmeticException.class, () -> iBook.save(book));
    }

    @Test
    public void testTransactionIsRollBack() throws SystemException, NotSupportedException {
        int id = 23;
        Book book = Objects.createBook(draft -> {
            draft.setId(id);
            draft.setName("Transactional");
            draft.setPrice(new BigDecimal("1"));
            draft.setEdition(1);
            draft.setStoreId(2L);
        });
        transactionManager.begin();
        try {
            iBook.save(book);
        } catch (Exception e) {
            transactionManager.rollback();
        }
        try {
            transactionManager.commit();
        } catch (Exception commitException) {
            // Handle commit exception if needed
        }
        Assertions.assertNull(iBook.findById(id));
    }

    @Test
    @Transactional(rollbackOn = Exception.class)
    public void testTransactionIsRollBack2() throws SystemException {
        int id = 23;
        Book book = Objects.createBook(draft -> {
            draft.setId(id);
            draft.setName("Transactional");
            draft.setPrice(new BigDecimal("1"));
            draft.setEdition(1);
            draft.setStoreId(2L);
        });
        Assertions.assertThrows(RuntimeException.class,
                () -> QuarkusTransaction.joiningExisting().run(() -> {
                    iBook.save(book);
                }));
        Assertions.assertEquals(Status.STATUS_MARKED_ROLLBACK, transactionManager.getStatus());
    }
}
