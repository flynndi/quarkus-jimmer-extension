package io.quarkiverse.jimmer.it.resolver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;

import jakarta.inject.Inject;

import org.babyfish.jimmer.sql.JSqlClient;
import org.babyfish.jimmer.sql.cache.CacheDisableConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkiverse.jimmer.it.config.TenantFilter;
import io.quarkiverse.jimmer.it.entity.Book;
import io.quarkiverse.jimmer.it.entity.BookFetcher;
import io.quarkiverse.jimmer.it.entity.BookStore;
import io.quarkiverse.jimmer.it.entity.BookStoreFetcher;
import io.quarkiverse.jimmer.it.entity.BookTable;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class TransientResolverQueryTestCase {

    private static final long STORE_ID = 7_310_001L;
    private static final List<Long> BOOK_IDS = List.of(7_310_011L, 7_310_012L, 7_310_013L);

    @Inject
    JSqlClient sqlClient;

    @Inject
    javax.sql.DataSource dataSource;

    @BeforeEach
    void insertOwnFixtures() throws SQLException {
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.executeUpdate("""
                    insert into book_store(id, name, created_time, modified_time)
                    values (7310001, 'Resolver integration store', current_timestamp, current_timestamp)
                    """);
            statement.executeUpdate("""
                    insert into book(id, name, edition, price, store_id, tenant, created_time, modified_time) values
                    (7310011, 'Resolver integration book', 1, 10, 7310001, 'a', current_timestamp, current_timestamp),
                    (7310012, 'Resolver integration book', 2, 30, 7310001, 'a', current_timestamp, current_timestamp),
                    (7310013, 'Resolver integration book', 3, 90, 7310001, 'b', current_timestamp, current_timestamp)
                    """);
        }
    }

    @AfterEach
    void deleteOwnFixtures() throws SQLException {
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.executeUpdate("delete from book where id in (7310011, 7310012, 7310013)");
            statement.executeUpdate("delete from book_store where id = 7310001");
        }
    }

    @Test
    void tenantFilterChangesTheActualQueryResults() {
        var table = BookTable.$;
        assertEquals(BOOK_IDS.subList(0, 2), sqlClient.createQuery(table)
                .where(table.id().in(BOOK_IDS))
                .orderBy(table.id())
                .select(table.id())
                .execute());
        assertEquals(BOOK_IDS, sqlClient.filters(filters -> filters.disableByTypes(TenantFilter.class))
                .createQuery(table)
                .where(table.id().in(BOOK_IDS))
                .orderBy(table.id())
                .select(table.id())
                .execute());
    }

    @Test
    void fetcherInvokesBothClassAndNamedResolversWithTheTenantFilter() {
        BookStore store = sqlClient.caches(CacheDisableConfig::disableAll)
                .findById(BookStoreFetcher.$.avgPrice().newestBooks(BookFetcher.$.allScalarFields()), STORE_ID);

        assertNotNull(store);
        assertEquals(0, new BigDecimal("20.00").compareTo(store.avgPrice()));
        assertEquals(List.of(7_310_012L), store.newestBooks().stream().map(Book::id).toList());
        assertEquals(2, store.newestBooks().get(0).edition());
        assertEquals("a", store.newestBooks().get(0).tenant());
    }
}
