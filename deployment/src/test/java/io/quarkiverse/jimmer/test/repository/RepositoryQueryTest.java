package io.quarkiverse.jimmer.test.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.babyfish.jimmer.ImmutableObjects;
import org.babyfish.jimmer.Page;
import org.babyfish.jimmer.sql.JSqlClient;
import org.babyfish.jimmer.sql.ast.mutation.SaveMode;
import org.babyfish.jimmer.sql.fetcher.Fetcher;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.jimmer.runtime.repo.support.AbstractJavaRepository;
import io.quarkiverse.jimmer.runtime.repository.DynamicParam;
import io.quarkiverse.jimmer.runtime.repository.JRepository;
import io.quarkiverse.jimmer.runtime.repository.support.Pagination;
import io.quarkiverse.jimmer.test.repository.model.Author;
import io.quarkiverse.jimmer.test.repository.model.Book;
import io.quarkiverse.jimmer.test.repository.model.BookDraft;
import io.quarkiverse.jimmer.test.repository.model.BookFetcher;
import io.quarkiverse.jimmer.test.repository.model.BookStore;
import io.quarkiverse.jimmer.test.repository.model.BookStoreFetcher;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.QuarkusUnitTest;

@SuppressWarnings("deprecation")
class RepositoryQueryTest {

    @RegisterExtension
    static final QuarkusUnitTest APP = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addPackage(Book.class.getPackage())
                    .addClasses(CurrentBooks.class, LegacyBooks.class)
                    .addAsResource(new StringAsset(String.join("\n", Book.class.getName(), BookStore.class.getName(),
                            Author.class.getName()) + "\n"), "META-INF/jimmer/entities"))
            .overrideConfigKey("quarkus.datasource.devservices.enabled", "false")
            .overrideConfigKey("quarkus.redis.devservices.enabled", "false")
            .overrideConfigKey("quarkus.datasource.db-kind", "h2")
            .overrideConfigKey("quarkus.datasource.jdbc.url", "jdbc:h2:mem:repository-queries;DB_CLOSE_DELAY=-1");

    @Inject
    LegacyBooks legacy;

    @Inject
    CurrentBooks current;

    @Inject
    DataSource dataSource;

    @BeforeEach
    void prepareData() throws SQLException {
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement
                    .execute("create table if not exists REPOSITORY_STORE (ID bigint primary key, NAME varchar(100) not null)");
            statement.execute("create table if not exists REPOSITORY_BOOK (ID bigint primary key, NAME varchar(100) not null, "
                    + "EDITION int not null, PRICE decimal(10, 2) not null, STORE_ID bigint)");
            statement.execute("create table if not exists REPOSITORY_AUTHOR (ID bigint primary key, FIRST_NAME varchar(100))");
            statement.execute("create table if not exists REPOSITORY_BOOK_AUTHOR (BOOK_ID bigint, AUTHOR_ID bigint)");
            statement.executeUpdate("delete from REPOSITORY_BOOK_AUTHOR");
            statement.executeUpdate("delete from REPOSITORY_AUTHOR");
            statement.executeUpdate("delete from REPOSITORY_BOOK");
            statement.executeUpdate("delete from REPOSITORY_STORE");
            statement.executeUpdate("insert into REPOSITORY_STORE values (1, 'Manning'), (2, 'Other')");
            statement.executeUpdate("insert into REPOSITORY_BOOK values "
                    + "(1, 'Alpha', 1, 50, 1), (2, 'Alpha', 2, 60, 1), (3, 'alpha', 3, 70, 1), "
                    + "(4, 'Beta', 1, 80, 1), (5, 'Elsewhere', 1, 90, 2), (6, 'Unassigned', 1, 100, null)");
        }
    }

    @Test
    void combinesScalarPredicatesAndUsesTheSuppliedFetcher() {
        Book book = legacy.findByNameAndEditionAndPrice("Alpha", 1, new BigDecimal("50"),
                BookFetcher.$.allScalarFields().store(BookStoreFetcher.$.name()));

        assertEquals(1L, book.id());
        assertEquals("Alpha", book.name());
        assertEquals(1, book.edition());
        assertEquals(new BigDecimal("50.00"), book.price());
        assertEquals("Manning", book.store().name());
    }

    @Test
    void derivedListsHonorTheFetcherSelection() {
        List<Book> books = legacy.findByNameLike("Alpha", BookFetcher.$.name());

        assertEquals(Set.of(1L, 2L), books.stream().map(Book::id).collect(Collectors.toSet()));
        assertEquals(2, books.size());
        for (Book book : books) {
            assertEquals("Alpha", book.name());
            assertFalse(ImmutableObjects.isLoaded(book, "price"));
        }
    }

    @Test
    void resolvesAssociationIdsInDerivedQueries() {
        List<Book> books = legacy.findByStoreId(1L, BookFetcher.$.allTableFields());

        assertEquals(Set.of(1L, 2L, 3L, 4L), books.stream().map(Book::id).collect(Collectors.toSet()));
        assertEquals(4, books.size());
        assertTrue(books.stream().allMatch(book -> Long.valueOf(1L).equals(book.storeId())));
    }

    @Test
    void derivedPagesKeepOrderingTotalsAndFetcherSelection() {
        Page<Book> page = legacy.findByNameLikeOrderByNameAscIdAsc("a", Pagination.of(1, 2), BookFetcher.$.name());

        assertEquals(List.of(4L, 6L), page.getRows().stream().map(Book::id).toList());
        assertEquals(List.of("Beta", "Unassigned"), page.getRows().stream().map(Book::name).toList());
        assertEquals(5, page.getTotalRowCount());
        assertEquals(3, page.getTotalPageCount());
        assertTrue(page.getRows().stream().noneMatch(book -> ImmutableObjects.isLoaded(book, "price")));
    }

    @Test
    void combinesIgnoreCaseWithAssociationFiltersAndDescendingEditions() {
        Page<Book> page = legacy.findByNameLikeIgnoreCaseAndStoreNameOrderByNameAscEditionDesc(
                Pagination.of(0, 10), BookFetcher.$.allTableFields(), "ALPHA", "Manning");

        assertEquals(List.of(2L, 1L, 3L), page.getRows().stream().map(Book::id).toList());
        assertEquals(List.of(2, 1, 3), page.getRows().stream().map(Book::edition).toList());
        assertEquals(3, page.getTotalRowCount());
        assertEquals(1, page.getTotalPageCount());
    }

    @Test
    void skipsOnlyTheDynamicNullPredicate() {
        Page<Book> page = legacy.findByNameLikeIgnoreCaseAndStoreNameOrderByNameAscEditionDesc(
                Pagination.of(0, 10), null, null, "Manning");

        assertEquals(List.of(2L, 1L, 4L, 3L), page.getRows().stream().map(Book::id).toList());
        assertEquals(4, page.getTotalRowCount());
        assertEquals(1, page.getTotalPageCount());
    }

    @Test
    void rejectsNullForANonDynamicParameterEvenWhenItIsNullable() {
        NullPointerException exception = assertThrows(NullPointerException.class,
                () -> legacy.findByNameLikeIgnoreCaseAndStoreNameOrderByNameAscEditionDesc(
                        Pagination.of(0, 10), null, null, null));

        assertTrue(exception.getMessage().contains("parameters[3]"));
        assertTrue(exception.getMessage().contains("@" + DynamicParam.class.getName()));
    }

    @Test
    void applicationRepositoriesCanUseTheirProtectedSqlClient() {
        Book book = current.findWithSql(1L);

        assertEquals(1L, book.id());
        assertEquals("Alpha", book.name());
    }

    @Test
    void saveCommandsRemainDeferredUntilExecutedInATransaction() {
        Book first = BookDraft.$.produce(draft -> draft.setId(1L).setName("Current"));
        Book second = BookDraft.$.produce(draft -> draft.setId(2L).setName("Legacy"));
        var currentCommand = current.saveCommand(first).setMode(SaveMode.UPDATE_ONLY);
        var legacyCommand = legacy.saveCommand(second).setMode(SaveMode.UPDATE_ONLY);

        assertEquals("Alpha", current.findById(1L).name());
        assertEquals("Alpha", legacy.findById(2L).orElseThrow().name());
        QuarkusTransaction.requiringNew().run(() -> {
            assertEquals("Current", currentCommand.execute().getModifiedEntity().name());
            assertEquals("Legacy", legacyCommand.execute().getModifiedEntity().name());
        });
        assertEquals("Current", current.findById(1L).name());
        assertEquals("Legacy", legacy.findById(2L).orElseThrow().name());
        assertEquals(new BigDecimal("50.00"), current.findById(1L).price());
        assertEquals(2, legacy.findById(2L).orElseThrow().edition());
    }

    public interface LegacyBooks extends JRepository<Book, Long> {
        Book findByNameAndEditionAndPrice(String name, int edition, BigDecimal price, Fetcher<Book> fetcher);

        List<Book> findByNameLike(String name, Fetcher<Book> fetcher);

        List<Book> findByStoreId(Long storeId, Fetcher<Book> fetcher);

        Page<Book> findByNameLikeOrderByNameAscIdAsc(String name, Pagination pagination, Fetcher<Book> fetcher);

        Page<Book> findByNameLikeIgnoreCaseAndStoreNameOrderByNameAscEditionDesc(Pagination pagination,
                @Nullable Fetcher<Book> fetcher, @DynamicParam @Nullable String name, @Nullable String storeName);
    }

    @Singleton
    public static class CurrentBooks extends AbstractJavaRepository<Book, Long> {
        public CurrentBooks(JSqlClient sql) {
            super(sql);
        }

        Book findWithSql(long id) {
            return sql.findById(Book.class, id);
        }
    }
}
