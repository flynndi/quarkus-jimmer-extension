package io.quarkiverse.jimmer.it.repository;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.jimmer.it.entity.Book;
import io.quarkiverse.jimmer.runtime.repository.JRepository;

@ApplicationScoped
public class BookRepository implements JRepository<Book, Long> {

}
