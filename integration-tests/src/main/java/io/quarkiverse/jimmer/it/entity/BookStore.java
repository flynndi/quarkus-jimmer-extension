package io.quarkiverse.jimmer.it.entity;

import java.math.BigDecimal;
import java.util.List;

import org.babyfish.jimmer.sql.*;
import org.jetbrains.annotations.Nullable;

import io.quarkiverse.jimmer.it.resolver.BookStoreAvgPriceResolver;
import io.quarkiverse.jimmer.it.resolver.BookStoreNewestBooksResolver;

@Entity
public interface BookStore extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    long id();

    @Key
    String name();

    @Nullable
    String website();

    @OneToMany(mappedBy = "store", orderedProps = {
            @OrderedProp("name"),
            @OrderedProp(value = "edition", desc = true)
    })
    List<Book> books();

    @Transient(value = BookStoreAvgPriceResolver.class)
    BigDecimal avgPrice();

    @Transient(value = BookStoreNewestBooksResolver.class)
    List<Book> newestBooks();
}
