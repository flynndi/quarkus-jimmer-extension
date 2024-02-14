package io.quarkiverse.jimmer.it.entity;

import org.babyfish.jimmer.sql.MappedSuperclass;

@MappedSuperclass
public interface TenantAware {

    String tenant();
}
