package io.quarkiverse.jimmer.it.service.impl;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.jimmer.it.entity.BookStore;
import io.quarkiverse.jimmer.it.entity.BookStoreTable;
import io.quarkiverse.jimmer.it.entity.Fetchers;
import io.quarkiverse.jimmer.it.entity.Tables;
import io.quarkiverse.jimmer.it.service.IBookStore;
import io.quarkiverse.jimmer.runtime.Jimmer;

@ApplicationScoped
public class BookStoreImpl implements IBookStore {

    BookStoreTable table = Tables.BOOK_STORE_TABLE;

    @Override
    public List<BookStore> oneToMany() {
        return Jimmer.getDefaultJSqlClient().createQuery(table)
                .select(
                        table.fetch(
                                Fetchers.BOOK_STORE_FETCHER
                                        .allScalarFields()
                                        .books(
                                                Fetchers.BOOK_FETCHER
                                                        .allScalarFields())))
                .execute();
    }
}
