package io.quarkiverse.jimmer.test.model;

import org.babyfish.jimmer.sql.Entity;
import org.babyfish.jimmer.sql.Id;

@Entity
public interface CdiBook {
    @Id
    long id();

    String name();
}
