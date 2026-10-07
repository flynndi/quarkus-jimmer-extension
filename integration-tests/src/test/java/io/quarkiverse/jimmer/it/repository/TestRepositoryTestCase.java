package io.quarkiverse.jimmer.it.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.quarkiverse.jimmer.it.Constant;
import io.quarkiverse.jimmer.it.entity.UserRole;
import io.quarkus.agroal.DataSource;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class TestRepositoryTestCase {

    @Inject
    @DataSource(Constant.DATASOURCE2)
    UserRoleRepository userRoleRepository;

    @Test
    void generatedRepositoryUsesItsNamedDataSource() {
        UserRole byUser = userRoleRepository.findByUserId(Constant.USER_ID);
        UserRole byRole = userRoleRepository.findByRoleId(Constant.ROLE_ID);
        UserRole byBoth = userRoleRepository.findByUserIdAndRoleId(Constant.USER_ID, Constant.ROLE_ID);

        for (UserRole userRole : List.of(byUser, byRole, byBoth)) {
            assertEquals(UUID.fromString(Constant.USER_ROLE_ID), userRole.id());
            assertEquals(Constant.USER_ID, userRole.userId());
            assertEquals(Constant.ROLE_ID, userRole.roleId());
        }
    }
}
