package io.quarkiverse.jimmer.it.repository;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.quarkus.arc.Arc;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class TestRepositoryTestCase {

    @Inject
    BookRepository bookRepository;

    @Test
    void testRepositoryBean() {
        BookRepository bookRepositoryFromArc = Arc.container().instance(BookRepository.class).get();
        Assertions.assertEquals(bookRepository, bookRepositoryFromArc);
    }
}
