package io.quarkiverse.jimmer.test.repository.model;

import org.babyfish.jimmer.sql.Entity;
import org.babyfish.jimmer.sql.Id;
import org.babyfish.jimmer.sql.Table;

@Entity
@Table(name = "REPOSITORY_STORE")
public interface BookStore {

    @Id
    long id();

    String name();
}
