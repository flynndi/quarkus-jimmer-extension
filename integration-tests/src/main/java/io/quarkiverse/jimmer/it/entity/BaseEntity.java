package io.quarkiverse.jimmer.it.entity;

import java.time.LocalDateTime;

import org.babyfish.jimmer.sql.MappedSuperclass;

@MappedSuperclass
public interface BaseEntity {

    LocalDateTime createdTime();

    LocalDateTime modifiedTime();
}
