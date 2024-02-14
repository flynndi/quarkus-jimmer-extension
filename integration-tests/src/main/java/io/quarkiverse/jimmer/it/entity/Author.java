package io.quarkiverse.jimmer.it.entity;

import java.util.List;
import java.util.UUID;

import org.babyfish.jimmer.sql.*;

@Entity
public interface Author {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    UUID id();

    @Key
    String firstName();

    @Key
    String lastName();

    Gender gender();

    @ManyToMany(mappedBy = "authors")
    List<Book> books();
}
