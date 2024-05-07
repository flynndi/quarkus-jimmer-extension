package io.quarkiverse.jimmer.it.repository;

import java.util.List;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.jimmer.it.entity.Fetchers;
import io.quarkiverse.jimmer.it.entity.Tables;
import io.quarkiverse.jimmer.it.entity.UserRole;
import io.quarkiverse.jimmer.it.entity.dto.UserRoleSpecification;
import io.quarkiverse.jimmer.runtime.repository.JRepository;
import io.quarkus.agroal.DataSource;

@ApplicationScoped
@DataSource("DB2")
public class UserRoleRepository implements JRepository<UserRole, UUID> {

    public List<UserRole> find(UserRoleSpecification userRoleSpecification) {
        return sql()
                .createQuery(Tables.USER_ROLE_TABLE)
                .where(userRoleSpecification)
                .select(Tables.USER_ROLE_TABLE.fetch(Fetchers.USER_ROLE_FETCHER.allScalarFields()))
                .execute();
    }
}
