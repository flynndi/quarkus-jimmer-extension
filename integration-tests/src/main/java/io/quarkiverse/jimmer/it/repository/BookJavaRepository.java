package io.quarkiverse.jimmer.it.repository;

import jakarta.inject.Singleton;

import org.babyfish.jimmer.sql.JSqlClient;

import io.quarkiverse.jimmer.it.entity.Book;
import io.quarkiverse.jimmer.runtime.repo.support.AbstractJavaRepository;

@Singleton
public class BookJavaRepository extends AbstractJavaRepository<Book, Long> {
    protected BookJavaRepository(JSqlClient sqlClient) {
        super(sqlClient);
    }
}
