package io.quarkiverse.jimmer.it.service;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.jimmer.it.entity.Book;
import io.quarkiverse.jimmer.runtime.repository.support.JRepositoryImpl;

@ApplicationScoped
public class BookRepository extends JRepositoryImpl<Book, Long> {

}
