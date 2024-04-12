package io.quarkiverse.jimmer.it.entity;

import java.util.UUID;

import jakarta.annotation.Nullable;

import org.babyfish.jimmer.sql.Entity;
import org.babyfish.jimmer.sql.GeneratedValue;
import org.babyfish.jimmer.sql.Id;

import io.quarkiverse.jimmer.it.config.UUIDGenerator;
import io.quarkiverse.jimmer.it.config.jsonmapping.AuthUser;

@Entity
public interface UserRole {

    @Id
    @GeneratedValue(generatorType = UUIDGenerator.class)
    UUID id();

    String userId();

    String roleId();

    boolean deleteFlag();

    @Nullable
    AuthUser authUser();
}
