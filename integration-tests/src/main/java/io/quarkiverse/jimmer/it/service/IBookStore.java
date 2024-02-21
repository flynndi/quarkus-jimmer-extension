package io.quarkiverse.jimmer.it.service;

import java.util.List;

import io.quarkiverse.jimmer.it.entity.BookStore;

public interface IBookStore {

    List<BookStore> oneToMany();
}
