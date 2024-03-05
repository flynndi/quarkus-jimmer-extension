package io.quarkiverse.jimmer.it.repository;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.jimmer.it.entity.Book;
import io.quarkiverse.jimmer.runtime.repository.support.JRepository;

@ApplicationScoped
public class BookRepository extends JRepository<Book, Long> {

}
