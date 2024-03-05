package io.quarkiverse.jimmer.it.repository;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.jimmer.it.entity.BookStore;
import io.quarkiverse.jimmer.runtime.repository.support.JRepository;

@ApplicationScoped
public class BookStoreRepository extends JRepository<BookStore, Long> {

}
