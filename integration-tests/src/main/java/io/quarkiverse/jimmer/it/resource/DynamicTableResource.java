package io.quarkiverse.jimmer.it.resource;

import java.util.Map;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.babyfish.jimmer.sql.JSqlClient;
import org.babyfish.jimmer.sql.ast.Predicate;
import org.babyfish.jimmer.sql.ast.impl.query.Queries;
import org.babyfish.jimmer.sql.ast.impl.table.TableProxies;
import org.babyfish.jimmer.sql.ast.table.WeakJoin;
import org.babyfish.jimmer.sql.runtime.JSqlClientImplementor;
import org.jboss.resteasy.reactive.RestQuery;

import io.quarkiverse.jimmer.it.entity.BookStoreTable;
import io.quarkiverse.jimmer.it.entity.BookTable;
import io.quarkiverse.jimmer.it.entity.BookTableEx;

@Path("/diagnostics/dynamic-tables")
@Produces(MediaType.APPLICATION_JSON)
public class DynamicTableResource {

    private final JSqlClient sqlClient;

    public DynamicTableResource(JSqlClient sqlClient) {
        this.sqlClient = sqlClient;
    }

    @GET
    public Map<String, Object> query(@RestQuery long id) {
        var type = TableProxies.tableType(BookTable.class);
        BookTableEx root = TableProxies.fluent(type.getJavaClass());
        boolean tableTypesMatch = type == TableProxies.tableType(BookTableEx.class)
                && root.getImmutableType() == type;

        // Entity-based queries instantiate the generated TableEx(TableImplementor) constructor.
        String bookName = Queries.createQuery((JSqlClientImplementor) sqlClient, type, (query, table) -> {
            BookTableEx book = (BookTableEx) table;
            return query.where(book.id().eq(id)).select(book.name());
        }).fetchOne();

        BookTable book = BookTable.$;
        // Class-based weak joins instantiate both the WeakJoin and TableEx(DelayedOperation).
        BookStoreTable store = book.asTableEx().weakJoin(BookToStoreWeakJoin.class);
        String storeName = sqlClient.createQuery(book)
                .where(book.id().eq(id))
                .select(store.name())
                .fetchOne();

        return Map.of("tableTypesMatch", tableTypesMatch, "bookName", bookName, "storeName", storeName);
    }

    public static class BookToStoreWeakJoin implements WeakJoin<BookTable, BookStoreTable> {

        @Override
        public Predicate on(BookTable source, BookStoreTable target) {
            return source.storeId().eq(target.id());
        }
    }
}
