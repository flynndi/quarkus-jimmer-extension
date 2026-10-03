package io.quarkiverse.jimmer.test.cloud.model;

import org.babyfish.jimmer.sql.Entity;
import org.babyfish.jimmer.sql.Id;
import org.babyfish.jimmer.sql.ManyToOne;

@Entity(microServiceName = "http-test")
public interface ExchangeBook {
    @Id
    String id();

    String name();

    @ManyToOne
    ExchangeStore store();
}
