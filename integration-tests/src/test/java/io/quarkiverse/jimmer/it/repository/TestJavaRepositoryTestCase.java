package io.quarkiverse.jimmer.it.repository;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.quarkiverse.jimmer.it.entity.Book;
import io.quarkus.arc.Arc;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class TestJavaRepositoryTestCase {

    @Inject
    BookJavaRepository bookJavaRepository;

    @Inject
    UserRoleJavaRepository userRoleJavaRepository;

    @Test
    void testJavaRepositoryBean() {
        BookJavaRepository bookJavaRepositoryFromArc = Arc.container().instance(BookJavaRepository.class).get();
        UserRoleJavaRepository userRoleJavaRepositoryFromArc = Arc.container().instance(UserRoleJavaRepository.class).get();
        Assertions.assertEquals(bookJavaRepository, bookJavaRepositoryFromArc);
        Assertions.assertEquals(userRoleJavaRepository, userRoleJavaRepositoryFromArc);
    }

    @Test
    void testJavaRepositoryFindById() {
        Book book = bookJavaRepository.findById(1L);
        Assertions.assertNotNull(book);
        Assertions.assertEquals(1L, book.id());
    }
}
