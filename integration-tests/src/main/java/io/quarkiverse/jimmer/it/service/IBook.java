package io.quarkiverse.jimmer.it.service;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.babyfish.jimmer.sql.ast.mutation.SimpleSaveResult;
import org.babyfish.jimmer.sql.fetcher.Fetcher;
import org.jetbrains.annotations.Nullable;

import io.quarkiverse.jimmer.it.entity.Book;

public interface IBook {

    Book findById(int id);

    Book findById(int id, Fetcher<Book> fetcher);

    List<Book> findByIds(List<Integer> ids);

    SimpleSaveResult<Book> save(Book book);

    Map<Long, BigDecimal> findAvgPriceGroupByStoreId(Collection<Long> storeIds);

    List<Book> findBooksByName(@Nullable String name, @Nullable Fetcher<Book> fetcher);

    List<Book> findBooksByName(@Nullable String name);

    void update();

    List<Book> manyToMany();

    void updateOneToMany();

    void saveManyToMany();
}
