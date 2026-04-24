package io.quarkiverse.jimmer.it.graphql;

import java.util.List;

import org.babyfish.jimmer.meta.TypedProp;
import org.babyfish.jimmer.sql.JSqlClient;
import org.babyfish.jimmer.sql.cache.Cache;
import org.junit.jupiter.api.AfterEach;

import io.quarkiverse.jimmer.it.entity.Author;
import io.quarkiverse.jimmer.it.entity.Book;
import io.quarkiverse.jimmer.it.entity.BookStore;
import io.quarkiverse.jimmer.it.entity.BookStoreProps;
import io.quarkiverse.jimmer.it.entity.Tables;
import io.quarkiverse.jimmer.runtime.Jimmer;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class JimmerGraphQLApiTestCase {

    @AfterEach
    void clearGraphQLCaches() {
        JSqlClient sqlClient = Jimmer.getDefaultJSqlClient();
        List<Long> bookIds = sqlClient.createQuery(Tables.BOOK_TABLE)
                .select(Tables.BOOK_TABLE.id())
                .execute();
        List<Long> authorIds = sqlClient.createQuery(Tables.AUTHOR_TABLE)
                .select(Tables.AUTHOR_TABLE.id())
                .execute();
        List<Long> bookStoreIds = sqlClient.createQuery(Tables.BOOK_STORE_TABLE)
                .select(Tables.BOOK_STORE_TABLE.id())
                .execute();

        clearObjectCache(sqlClient, Book.class, bookIds);
        clearObjectCache(sqlClient, Author.class, authorIds);
        clearObjectCache(sqlClient, BookStore.class, bookStoreIds);
        clearPropertyCache(sqlClient, BookStoreProps.NEWEST_BOOKS, bookStoreIds);
        clearPropertyCache(sqlClient, BookStoreProps.AVG_PRICE, bookStoreIds);
    }

    private static <T> void clearObjectCache(JSqlClient sqlClient, Class<T> type, List<Long> ids) {
        if (ids.isEmpty()) {
            return;
        }
        Cache<Long, T> cache = sqlClient.getCaches().getObjectCache(type);
        if (cache != null) {
            cache.deleteAll(ids);
        }
    }

    private static void clearPropertyCache(JSqlClient sqlClient, TypedProp<?, ?> prop, List<Long> ids) {
        if (ids.isEmpty()) {
            return;
        }
        Cache<Long, ?> cache = sqlClient.getCaches().getPropertyCache(prop);
        if (cache != null) {
            cache.deleteAll(ids);
        }
    }
}
