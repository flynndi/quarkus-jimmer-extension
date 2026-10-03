package io.quarkiverse.jimmer.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.SQLException;
import java.util.List;

import javax.sql.DataSource;

import jakarta.inject.Inject;

import org.babyfish.jimmer.View;
import org.babyfish.jimmer.sql.fetcher.DtoMetadata;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.jimmer.runtime.repository.JRepository;
import io.quarkiverse.jimmer.runtime.repository.common.Sort;
import io.quarkiverse.jimmer.test.model.sort.LegacySortBook;
import io.quarkiverse.jimmer.test.model.sort.LegacySortBookDraft;
import io.quarkiverse.jimmer.test.model.sort.LegacySortBookFetcher;
import io.quarkus.test.QuarkusUnitTest;

@SuppressWarnings("deprecation")
class LegacySortIgnoreCaseTest {

    @RegisterExtension
    static final QuarkusUnitTest APP = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addPackage(LegacySortBook.class.getPackage())
                    .addClasses(Books.class, BookView.class)
                    .addAsResource(new StringAsset(LegacySortBook.class.getName() + "\n"), "META-INF/jimmer/entities"))
            .overrideConfigKey("quarkus.datasource.devservices.enabled", "false")
            .overrideConfigKey("quarkus.redis.devservices.enabled", "false")
            .overrideConfigKey("quarkus.datasource.db-kind", "h2")
            .overrideConfigKey("quarkus.datasource.jdbc.url", "jdbc:h2:mem:legacy-sort-ignore-case;DB_CLOSE_DELAY=-1");

    @Inject
    Books books;

    @Inject
    DataSource dataSource;

    @BeforeEach
    void prepareData() throws SQLException {
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute("create table if not exists LEGACY_SORT_BOOK "
                    + "(ID bigint primary key, NAME varchar(100), PARENT_ID bigint)");
            statement.execute("delete from LEGACY_SORT_BOOK");
            statement.execute("insert into LEGACY_SORT_BOOK values "
                    + "(1, 'alpha', 2), (2, 'Zulu', null), (3, 'Beta', 1), "
                    + "(4, 'ALPHA', 6), (5, null, null), (6, 'beta', 4)");
        }
    }

    @Test
    void foldsOnlyRequestedStringOrdersAndPreservesDirectionNullsAndTieBreakers() {
        Sort sensitive = Sort.by(Sort.Order.asc("name").nullsLast(), Sort.Order.desc("id"));
        Sort insensitive = Sort.by(Sort.Order.asc("name").ignoreCase().nullsLast(), Sort.Order.desc("id"));
        assertEquals(List.of(4L, 3L, 2L, 1L, 6L, 5L), books.findAll(sensitive).stream().map(LegacySortBook::id).toList());
        assertEquals(List.of(4L, 1L, 6L, 3L, 2L, 5L), books.findAll(insensitive).stream().map(LegacySortBook::id).toList());

        Sort descending = Sort.by(Sort.Order.desc("name").ignoreCase().nullsFirst(), Sort.Order.asc("id"));
        assertEquals(List.of(5L, 2L, 3L, 6L, 1L, 4L), books.findAll(descending).stream().map(LegacySortBook::id).toList());
        assertEquals(List.of(6L, 5L, 4L, 3L, 2L, 1L),
                books.findAll(Sort.by(Sort.Order.desc("id").ignoreCase())).stream().map(LegacySortBook::id).toList());
    }

    @Test
    void appliesToFetcherPagesViewersAndGeneratedDerivedQueries() {
        Sort sort = Sort.by(Sort.Order.asc("name").ignoreCase().nullsLast(), Sort.Order.desc("id"));
        var fetcher = LegacySortBookFetcher.$.name();
        List<Long> expected = List.of(4L, 1L, 6L, 3L, 2L, 5L);
        assertEquals(expected, books.findAll(fetcher, sort).stream().map(LegacySortBook::id).toList());
        assertEquals(List.of(6L, 3L), books.findAll(1, 2, sort).getRows().stream().map(LegacySortBook::id).toList());
        assertEquals(List.of(6L, 3L),
                books.findAll(1, 2, fetcher, sort).getRows().stream().map(LegacySortBook::id).toList());
        assertEquals(expected, books.viewer(BookView.class).findAll(sort).stream().map(BookView::id).toList());
        assertEquals(List.of(6L, 3L),
                books.viewer(BookView.class).findAll(1, 2, sort).getRows().stream().map(BookView::id).toList());
        assertEquals(expected, books.findByIdGreaterThan(0L, sort).stream().map(LegacySortBook::id).toList());
    }

    @Test
    void foldsNestedStringPathsWithoutDroppingRowsWithNoParent() {
        Sort sort = Sort.by(Sort.Order.asc("parent.name").ignoreCase().nullsLast(), Sort.Order.asc("id"));
        assertEquals(List.of(3L, 6L, 4L, 1L, 2L, 5L), books.findAll(sort).stream().map(LegacySortBook::id).toList());
    }

    public interface Books extends JRepository<LegacySortBook, Long> {
        List<LegacySortBook> findByIdGreaterThan(long id, Sort sort);
    }

    public record BookView(long id, String name) implements View<LegacySortBook> {
        public static final DtoMetadata<LegacySortBook, BookView> METADATA = new DtoMetadata<>(
                BookView.class, LegacySortBookFetcher.$.name(), book -> new BookView(book.id(), book.name()));

        @Override
        public LegacySortBook toEntity() {
            return LegacySortBookDraft.$.produce(draft -> draft.setId(id).setName(name));
        }
    }
}
