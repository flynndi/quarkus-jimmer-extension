package io.quarkiverse.jimmer.it.service.impl;

import static io.quarkiverse.jimmer.it.entity.Tables.BOOK_TABLE;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import org.babyfish.jimmer.sql.JSqlClient;
import org.babyfish.jimmer.sql.ast.mutation.SimpleSaveResult;
import org.babyfish.jimmer.sql.ast.tuple.Tuple2;
import org.babyfish.jimmer.sql.fetcher.Fetcher;
import org.jetbrains.annotations.Nullable;

import io.quarkiverse.jimmer.it.entity.*;
import io.quarkiverse.jimmer.it.service.IBook;

@ApplicationScoped
public class BookImpl implements IBook {

    private final BookTable table = BOOK_TABLE;
    private final JSqlClient sqlClient;

    public BookImpl(JSqlClient sqlClient) {
        this.sqlClient = sqlClient;
    }

    @Override
    public Book findById(long id) {
        return sqlClient.findById(Book.class, id);
    }

    @Override
    public List<Book> findByIds(List<Integer> ids) {
        return sqlClient.findByIds(Book.class, ids);
    }

    @Override
    public SimpleSaveResult<Book> save(Book book) {
        return sqlClient.save(book);
    }

    @Override
    public Map<Long, BigDecimal> findAvgPriceGroupByStoreId(Collection<Long> storeIds) {
        return Tuple2.toMap(
                sqlClient.createQuery(table)
                        .where(table.storeId().in(storeIds))
                        .groupBy(table.storeId())
                        .select(
                                table.storeId(),
                                table.price().avgAsDecimal())
                        .execute());
    }

    @Override
    public List<Book> findBooksByName(@Nullable String name, @Nullable Fetcher<Book> fetcher) {
        return sqlClient.createQuery(table)
                .whereIf(
                        name != null && !name.isEmpty(),
                        table.name().ilike(name))
                .select(
                        table.fetch(fetcher))
                .execute();
    }

    @Override
    public List<Book> findBooksByName(@Nullable String name) {
        return sqlClient.createQuery(table)
                .whereIf(
                        name != null && !name.isEmpty(),
                        table.name().ilike(name))
                .select(table.fetch(Fetchers.BOOK_FETCHER.allScalarFields().store(Fetchers.BOOK_STORE_FETCHER.name())))
                .execute();
    }

    @Override
    @Transactional
    public void update() {
        sqlClient.createUpdate(Tables.BOOK_STORE_TABLE)
                .set(Tables.BOOK_STORE_TABLE.website(), "https://www.manning.com")
                .where(Tables.BOOK_STORE_TABLE.id().eq(2L))
                .execute();
    }

    @Override
    public List<Book> manyToMany() {
        return sqlClient.createQuery(table)
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
        sqlClient.createUpdate(table)
                .set(table.store().id(), 2L)
                .where(table.id().eq(7L))
                .execute();
    }

    @Override
    public void saveManyToMany() {
        sqlClient.getAssociations(BookProps.AUTHORS)
                .save(10, 3);
    }
}
