package io.quarkiverse.jimmer.test.repository.model;

import java.math.BigDecimal;
import java.util.List;

import org.babyfish.jimmer.sql.Entity;
import org.babyfish.jimmer.sql.Id;
import org.babyfish.jimmer.sql.IdView;
import org.babyfish.jimmer.sql.JoinTable;
import org.babyfish.jimmer.sql.ManyToMany;
import org.babyfish.jimmer.sql.ManyToOne;
import org.babyfish.jimmer.sql.Table;
import org.jetbrains.annotations.Nullable;

@Entity
@Table(name = "REPOSITORY_BOOK")
public interface Book {

    @Id
    long id();

    String name();

    int edition();

    BigDecimal price();

    @IdView
    @Nullable
    Long storeId();

    @ManyToOne
    @Nullable
    BookStore store();

    @ManyToMany
    @JoinTable(name = "REPOSITORY_BOOK_AUTHOR", joinColumnName = "BOOK_ID", inverseJoinColumnName = "AUTHOR_ID")
    List<Author> authors();
}
