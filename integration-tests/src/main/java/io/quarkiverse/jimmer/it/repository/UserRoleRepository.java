package io.quarkiverse.jimmer.it.repository;

import java.util.UUID;

import io.quarkiverse.jimmer.it.config.Constant;
import io.quarkiverse.jimmer.it.entity.UserRole;
import io.quarkiverse.jimmer.runtime.repository.JRepository;
import io.quarkus.agroal.DataSource;

@DataSource(Constant.DATASOURCE2)
public interface UserRoleRepository extends JRepository<UserRole, UUID> {

    UserRole findByUserId(String userId);

    UserRole findByRoleId(String roleId);

    UserRole findByUserIdAndRoleId(String userId, String roleId);
}
