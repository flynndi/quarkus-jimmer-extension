package io.quarkiverse.jimmer.it.resource;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.quarkiverse.jimmer.it.repository.BookRepository;
import io.quarkiverse.jimmer.it.repository.BookStoreRepository;
import io.quarkus.arc.Arc;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class TestResourceTestCase {

    @Test
    void testRepository() {
        BookRepository bookRepository = Arc.container().instance(BookRepository.class).get();
        BookStoreRepository bookStoreRepository = Arc.container().instance(BookStoreRepository.class).get();
        Assertions.assertNotNull(bookRepository);
        Assertions.assertNotNull(bookStoreRepository);
    }
}
