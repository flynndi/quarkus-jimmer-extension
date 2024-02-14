package io.quarkiverse.jimmer.it.service.impl;

import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.jimmer.it.entity.UserRole;
import io.quarkiverse.jimmer.it.service.IUserRoleService;
import io.quarkiverse.jimmer.runtime.Jimmer;

@ApplicationScoped
public class UserRoleServiceImpl implements IUserRoleService {

    private final String DB2 = "DB2";

    @Override
    public UserRole findById(UUID id) {
        return Jimmer.getJSqlClient(DB2).findById(UserRole.class, id);
    }
}
