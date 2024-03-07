package io.quarkiverse.jimmer.it.repository;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.jimmer.it.entity.BookStore;
import io.quarkiverse.jimmer.runtime.repository.JRepository;

@ApplicationScoped
public class BookStoreRepository implements JRepository<BookStore, Long> {

}
