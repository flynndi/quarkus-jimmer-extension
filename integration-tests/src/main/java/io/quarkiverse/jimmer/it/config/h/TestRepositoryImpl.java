package io.quarkiverse.jimmer.it.config.h;

import io.quarkiverse.jimmer.it.entity.Book;

public class TestRepositoryImpl implements TestRepository {

    @Override
    public String test(Book book, Long aLong) {
        return "";
    }
}
