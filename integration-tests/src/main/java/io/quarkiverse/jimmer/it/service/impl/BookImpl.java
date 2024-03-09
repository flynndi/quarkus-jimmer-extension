package io.quarkiverse.jimmer.it.service.impl;

import static io.quarkiverse.jimmer.it.entity.Tables.BOOK_TABLE;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import org.babyfish.jimmer.sql.ast.mutation.SimpleSaveResult;
import org.babyfish.jimmer.sql.ast.tuple.Tuple2;
import org.babyfish.jimmer.sql.fetcher.Fetcher;
import org.jetbrains.annotations.Nullable;

import io.quarkiverse.jimmer.it.entity.*;
import io.quarkiverse.jimmer.it.service.IBook;
import io.quarkiverse.jimmer.runtime.Jimmer;

@ApplicationScoped
public class BookImpl implements IBook {

    BookTable table = BOOK_TABLE;

    @Override
    public Book findById(int id) {
        return Jimmer.getDefaultJSqlClient().findById(Book.class, id);
    }

    @Override
    public Book findById(int id, Fetcher<Book> fetcher) {
        return Jimmer.getDefaultJSqlClient().findById(fetcher, id);
    }

    @Override
    public List<Book> findByIds(List<Integer> ids) {
        return Jimmer.getDefaultJSqlClient().findByIds(Book.class, ids);
    }

    @Override
    public SimpleSaveResult<Book> save(Book book) {
        SimpleSaveResult<Book> save = Jimmer.getDefaultJSqlClient().save(book);
        int i = 1 / 0;
        return save;
    }

    @Override
    public Map<Long, BigDecimal> findAvgPriceGroupByStoreId(Collection<Long> storeIds) {
        return Tuple2.toMap(
                Jimmer.getDefaultJSqlClient()
                        .createQuery(table)
                        .where(table.storeId().in(storeIds))
                        .groupBy(table.storeId())
                        .select(
                                table.storeId(),
                                table.price().avg())
                        .execute());
    }

    @Override
    public List<Book> findBooksByName(@Nullable String name, @Nullable Fetcher<Book> fetcher) {
        return Jimmer.getDefaultJSqlClient()
                .createQuery(table)
                .whereIf(
                        name != null && !name.isEmpty(),
                        table.name().ilike(name))
                .select(
                        table.fetch(fetcher))
                .execute();
    }

    @Override
    public List<Book> findBooksByName(@Nullable String name) {
        return Jimmer.getDefaultJSqlClient()
                .createQuery(table)
                .whereIf(
                        name != null && !name.isEmpty(),
                        table.name().ilike(name))
                .select(table.fetch(Fetchers.BOOK_FETCHER.allScalarFields().store(Fetchers.BOOK_STORE_FETCHER.name())))
                .execute();
    }

    @Override
    @Transactional
    public void update() {
        Jimmer.getDefaultJSqlClient()
                .createUpdate(Tables.BOOK_STORE_TABLE)
                .set(Tables.BOOK_STORE_TABLE.website(), "https://www.manning.com")
                .where(Tables.BOOK_STORE_TABLE.id().eq(2L))
                .execute();
    }

    @Override
    public List<Book> manyToMany() {
        return Jimmer.getDefaultJSqlClient()
                .createQuery(table)
                .where(table.edition().eq(1))
                .select(
                        table.fetch(
                                Fetchers.BOOK_FETCHER
                                        .allScalarFields()
                                        .authors(
                                                Fetchers.AUTHOR_FETCHER
                                                        .allScalarFields())))
                .execute();
    }

    @Override
    public void updateOneToMany() {
        Jimmer.getDefaultJSqlClient()
                .createUpdate(table)
                .set(table.store().id(), 2L)
                .where(table.id().eq(7L))
                .execute();
    }

    @Override
    public void saveManyToMany() {
        Jimmer.getDefaultJSqlClient()
                .getAssociations(BookProps.AUTHORS)
                .save(10, 3);
    }
}
