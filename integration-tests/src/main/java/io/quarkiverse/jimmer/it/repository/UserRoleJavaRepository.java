package io.quarkiverse.jimmer.it.repository;

import java.util.List;
import java.util.UUID;

import jakarta.inject.Singleton;

import org.babyfish.jimmer.sql.JSqlClient;

import io.quarkiverse.jimmer.it.config.Constant;
import io.quarkiverse.jimmer.it.entity.Fetchers;
import io.quarkiverse.jimmer.it.entity.Tables;
import io.quarkiverse.jimmer.it.entity.UserRole;
import io.quarkiverse.jimmer.it.entity.dto.UserRoleSpecification;
import io.quarkiverse.jimmer.runtime.repo.support.AbstractJavaRepository;
import io.quarkus.agroal.DataSource;

@Singleton
public class UserRoleJavaRepository extends AbstractJavaRepository<UserRole, UUID> {
    protected UserRoleJavaRepository(@DataSource(Constant.DATASOURCE2) JSqlClient sqlClient) {
        super(sqlClient);
    }

    public List<UserRole> find(UserRoleSpecification specification) {
        return sql.createQuery(Tables.USER_ROLE_TABLE)
                .where(specification)
                .select(Tables.USER_ROLE_TABLE.fetch(Fetchers.USER_ROLE_FETCHER.allScalarFields()))
                .execute();
    }
}
