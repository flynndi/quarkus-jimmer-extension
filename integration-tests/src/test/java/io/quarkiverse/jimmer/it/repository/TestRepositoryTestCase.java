package io.quarkiverse.jimmer.it.repository;

import java.math.BigDecimal;
import java.util.List;

import jakarta.inject.Inject;

import org.babyfish.jimmer.Page;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.quarkiverse.jimmer.it.Constant;
import io.quarkiverse.jimmer.it.entity.Book;
import io.quarkiverse.jimmer.it.entity.Fetchers;
import io.quarkiverse.jimmer.it.entity.UserRole;
import io.quarkiverse.jimmer.runtime.repository.support.Pagination;
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

    @Test
    void testBookRepositoryFindByNameLike() {
        List<Book> books = bookRepository.findByNameLike("Learning GraphQL", Fetchers.BOOK_FETCHER.allTableFields());
        Assertions.assertEquals(2, books.size());
        Assertions.assertEquals("Learning GraphQL", books.get(0).name());
        Assertions.assertEquals("Learning GraphQL", books.get(1).name());
    }

    @Test
    void testBookRepositoryFindByStoreId() {
        List<Book> books = bookRepository.findByStoreId(1L, Fetchers.BOOK_FETCHER.allTableFields());
        Assertions.assertEquals(5, books.size());
        Assertions.assertEquals(1L, books.get(0).storeId());
        Assertions.assertEquals(1L, books.get(1).storeId());
        Assertions.assertEquals(1L, books.get(2).storeId());
        Assertions.assertEquals(1L, books.get(3).storeId());
        Assertions.assertEquals(1L, books.get(4).storeId());
    }

    @Test
    void testBookRepositoryFindByNameLikeOrderByName() {
        Page<Book> bookPage = bookRepository.findByNameLikeOrderByName("Learning GraphQL", new Pagination(0, 10),
                Fetchers.BOOK_FETCHER.allTableFields());
        Assertions.assertEquals(2, bookPage.getRows().size());
        Assertions.assertEquals("Learning GraphQL", bookPage.getRows().get(0).name());
        Assertions.assertEquals("Learning GraphQL", bookPage.getRows().get(1).name());
        Assertions.assertEquals(1, bookPage.getTotalPageCount());
        Assertions.assertEquals(2, bookPage.getTotalRowCount());
    }

    @Test
    void testUserRoleRepositoryFindByUserId() {
        UserRole userRole = userRoleRepository.findByUserId(Constant.USER_ID);
        Assertions.assertEquals(Constant.USER_ID, userRole.userId());
    }

    @Test
    void testUserRoleRepositoryFindByRoleId() {
        UserRole userRole = userRoleRepository.findByRoleId(Constant.ROLE_ID);
        Assertions.assertEquals(Constant.ROLE_ID, userRole.roleId());
    }

    @Test
    void testUserRoleRepositoryFindByUserIdAndRoleId() {
        UserRole userRole = userRoleRepository.findByUserIdAndRoleId(Constant.USER_ID, "2c6a06d8-8e10-49c4-88fe-7d2f05dd073b");
        Assertions.assertEquals(Constant.USER_ID, userRole.userId());
        Assertions.assertEquals("2c6a06d8-8e10-49c4-88fe-7d2f05dd073b", userRole.roleId());
    }
}
