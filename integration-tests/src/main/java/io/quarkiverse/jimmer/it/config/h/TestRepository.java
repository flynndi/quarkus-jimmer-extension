package io.quarkiverse.jimmer.it.config.h;

import java.util.Arrays;
import java.util.List;

import io.quarkiverse.jimmer.it.entity.Book;
import io.quarkiverse.jimmer.runtime.repository.TestInterface;

public interface TestRepository extends TestInterface<Book, Long> {

    default List<Book> testList() {
        return sql().findByIds(entityType(), Arrays.asList(1L, 3L));
    }
}
