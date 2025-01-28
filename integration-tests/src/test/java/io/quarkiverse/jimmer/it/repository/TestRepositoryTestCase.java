package io.quarkiverse.jimmer.it.repository;

import java.math.BigDecimal;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.quarkiverse.jimmer.it.Constant;
import io.quarkiverse.jimmer.it.entity.Book;
import io.quarkiverse.jimmer.it.entity.Fetchers;
import io.quarkus.agroal.DataSource;
import io.quarkus.arc.Arc;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class TestRepositoryTestCase {

    @Inject
    BookRepository bookRepository;

    @Inject
    @DataSource(Constant.DATASOURCE2)
    UserRoleRepository userRoleRepository;

    @Test
    void testRepositoryBean() {
        BookRepository bookRepositoryFromArc = Arc.container().instance(BookRepository.class).get();
        Assertions.assertEquals(bookRepository, bookRepositoryFromArc);
        UserRoleRepository userRoleRepositoryFromArc = Arc.container()
                .instance(UserRoleRepository.class, new DataSource.DataSourceLiteral(Constant.DATASOURCE2)).get();
        Assertions.assertEquals(userRoleRepository, userRoleRepositoryFromArc);
    }

    @Test
    void testBookRepositoryFindByNameAndEditionAndPrice() {
        Book book = bookRepository.findByNameAndEditionAndPrice("Learning GraphQL", 1, new BigDecimal(50),
                Fetchers.BOOK_FETCHER.allTableFields());
        Assertions.assertEquals("Learning GraphQL", book.name());
        Assertions.assertEquals(1, book.edition());
        Assertions.assertEquals(new BigDecimal("50.00"), book.price());
    }
}
