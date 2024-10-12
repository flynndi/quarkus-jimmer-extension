package io.quarkiverse.jimmer.it.repository;

import java.util.UUID;

import jakarta.inject.Singleton;

import org.babyfish.jimmer.sql.JSqlClient;

import io.quarkiverse.jimmer.it.config.Constant;
import io.quarkiverse.jimmer.it.entity.UserRole;
import io.quarkiverse.jimmer.runtime.repo.support.AbstractJavaRepository;
import io.quarkus.agroal.DataSource;

@Singleton
public class UserRoleJavaRepository extends AbstractJavaRepository<UserRole, UUID> {
    protected UserRoleJavaRepository(@DataSource(Constant.DATASOURCE2) JSqlClient sqlClient) {
        super(sqlClient);
    }
}
