package io.quarkiverse.jimmer.test.http.model;

import org.babyfish.jimmer.sql.Entity;
import org.babyfish.jimmer.sql.Id;

@Entity(microServiceName = "http-test")
public interface HttpStore {
    @Id
    long id();

    String name();
}
