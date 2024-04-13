package io.quarkiverse.jimmer.it.service;

import java.util.UUID;

import io.quarkiverse.jimmer.it.entity.UserRole;

public interface IUserRoleService {

    UserRole findById(UUID id);

    void updateById(UUID id);

    void deleteById(UUID id);

    UserRole deleteReverseById(UUID id);
}
