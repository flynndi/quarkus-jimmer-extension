package io.quarkiverse.jimmer.it.entity;

import java.util.List;

import org.babyfish.jimmer.sql.*;

@Entity
public interface Author {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    int id();

    @Key
    String firstName();

    @Key
    String lastName();

    Gender gender();

    @ManyToMany(mappedBy = "authors")
    List<Book> books();
}
