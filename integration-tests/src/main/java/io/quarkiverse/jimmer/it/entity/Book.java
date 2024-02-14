package io.quarkiverse.jimmer.it.entity;

import java.math.BigDecimal;
import java.util.List;

import org.babyfish.jimmer.sql.*;
import org.jetbrains.annotations.Nullable;

@Entity
public interface Book extends TenantAware {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    int id();

    String name();

    int edition();

    BigDecimal price();

    @IdView
    Long storeId();

    @Nullable
    @ManyToOne
    BookStore store();

    @ManyToMany
    List<Author> authors();
}
