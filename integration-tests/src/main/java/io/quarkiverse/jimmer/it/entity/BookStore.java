package io.quarkiverse.jimmer.it.entity;

import java.math.BigDecimal;
import java.util.List;

import org.babyfish.jimmer.sql.*;
import org.jetbrains.annotations.Nullable;

import io.quarkiverse.jimmer.it.resolver.BookStoreAvgPriceResolver;

@Entity
public interface BookStore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    long id();

    @Key // ❶
    String name();

    @Nullable // ❷
    String website();

    @OneToMany(mappedBy = "store", orderedProps = { // ❸
            @OrderedProp("name"),
            @OrderedProp(value = "edition", desc = true)
    })
    List<Book> books();

    @Transient(value = BookStoreAvgPriceResolver.class)
    BigDecimal avgPrice();

    //    @Transient(ref = "bookStoreNewestBooksResolver")
    //    List<Book> newestBooks();
}
