package io.quarkiverse.jimmer.it.repository;

import io.quarkiverse.jimmer.it.entity.BookStore;
import io.quarkiverse.jimmer.runtime.repository.JRepository;

public interface BookStoreRepository extends JRepository<BookStore, Long> {

}
