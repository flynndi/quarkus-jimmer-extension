package io.quarkiverse.jimmer.test.http.model;

import org.babyfish.jimmer.sql.Entity;
import org.babyfish.jimmer.sql.Id;
import org.babyfish.jimmer.sql.ManyToOne;

@Entity(microServiceName = "http-test")
public interface HttpBook {
    @Id
    long id();

    String name();

    @ManyToOne
    HttpStore store();
}
