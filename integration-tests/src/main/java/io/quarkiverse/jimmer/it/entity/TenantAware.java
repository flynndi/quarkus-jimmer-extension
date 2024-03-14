package io.quarkiverse.jimmer.it.entity;

import org.babyfish.jimmer.sql.MappedSuperclass;

@MappedSuperclass(microServiceName = "quarkus-jimmer-integration-tests")
public interface TenantAware {

    String tenant();
}
