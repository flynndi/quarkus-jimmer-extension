package io.quarkiverse.jimmer.it.repository;

import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.jimmer.it.entity.UserRole;
import io.quarkiverse.jimmer.runtime.repository.support.JRepository;
import io.quarkus.agroal.DataSource;

@ApplicationScoped
@DataSource("DB2")
public class UserRoleRepository extends JRepository<UserRole, UUID> {
}
