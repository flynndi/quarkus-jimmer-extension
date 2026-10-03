package io.quarkiverse.jimmer.test.cloud.model;

import java.util.UUID;

import org.babyfish.jimmer.sql.Entity;
import org.babyfish.jimmer.sql.Id;

@Entity(microServiceName = "http-test")
public interface ExchangeStore {
    @Id
    UUID id();

    String name();
}
