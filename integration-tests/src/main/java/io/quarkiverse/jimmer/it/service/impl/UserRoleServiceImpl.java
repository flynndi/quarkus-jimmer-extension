package io.quarkiverse.jimmer.it.service.impl;

import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;

import org.babyfish.jimmer.sql.JSqlClient;
import org.babyfish.jimmer.sql.runtime.LogicalDeletedBehavior;

import io.quarkiverse.jimmer.it.config.Constant;
import io.quarkiverse.jimmer.it.entity.Tables;
import io.quarkiverse.jimmer.it.entity.UserRole;
import io.quarkiverse.jimmer.it.repository.UserRoleRepository;
import io.quarkiverse.jimmer.it.service.IUserRoleService;
import io.quarkiverse.jimmer.runtime.Jimmer;
import io.quarkus.agroal.DataSource;

@ApplicationScoped
public class UserRoleServiceImpl implements IUserRoleService {

    private final UserRoleRepository userRoleRepository;

    private final JSqlClient jSqlClientDB2;

    public UserRoleServiceImpl(@DataSource(Constant.DATASOURCE2) UserRoleRepository userRoleRepository,
            @DataSource(Constant.DATASOURCE2) JSqlClient jSqlClientDB2) {
        this.userRoleRepository = userRoleRepository;
        this.jSqlClientDB2 = jSqlClientDB2;
    }

    @Override
    public UserRole findById(UUID id) {
        return Jimmer.getJSqlClient(Constant.DATASOURCE2).findById(UserRole.class, id);
    }

    @Override
    public void updateById(UUID id) {
        Jimmer.getJSqlClient(Constant.DATASOURCE2)
                .createUpdate(Tables.USER_ROLE_TABLE)
                .set(Tables.USER_ROLE_TABLE.roleId(), "123")
                .where(Tables.USER_ROLE_TABLE.id().eq(id))
                .execute();
    }

    @Override
    public void deleteById(UUID id) {
        userRoleRepository.deleteById(id);
    }

    @Override
    public UserRole deleteReverseById(UUID id) {
        return jSqlClientDB2.filters(cfg -> cfg.setBehavior(LogicalDeletedBehavior.REVERSED))
                .findById(UserRole.class, id);
    }
}
