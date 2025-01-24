package io.quarkiverse.jimmer.it.repository;

import java.util.List;
import java.util.UUID;

import io.quarkiverse.jimmer.it.config.Constant;
import io.quarkiverse.jimmer.it.entity.Fetchers;
import io.quarkiverse.jimmer.it.entity.Tables;
import io.quarkiverse.jimmer.it.entity.UserRole;
import io.quarkiverse.jimmer.it.entity.dto.UserRoleSpecification;
import io.quarkiverse.jimmer.runtime.repository.JRepository;
import io.quarkus.agroal.DataSource;

@DataSource(Constant.DATASOURCE2)
public interface UserRoleRepository extends JRepository<UserRole, UUID> {

    default List<UserRole> find(UserRoleSpecification userRoleSpecification) {
        return sql()
                .createQuery(Tables.USER_ROLE_TABLE)
                .where(userRoleSpecification)
                .select(Tables.USER_ROLE_TABLE.fetch(Fetchers.USER_ROLE_FETCHER.allScalarFields()))
                .execute();
    }

    UserRole findByUserId(String userId);
}
