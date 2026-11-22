package io.quarkiverse.jimmer.it.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.quarkiverse.jimmer.it.Constant;
import io.quarkiverse.jimmer.it.entity.UserRole;
import io.quarkus.agroal.DataSource;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class UserRoleRepositoryExampleTest {

    private static final UUID USER_ROLE_ID = UUID.fromString(Constant.USER_ROLE_ID);

    @Inject
    UserRoleJavaRepository javaRepository;

    @Inject
    @DataSource(Constant.DATASOURCE2)
    UserRoleRepository legacyRepository;

    @Test
    void javaRepositoryFindsByIdUsingTheNamedSqlClient() {
        assertSeededUserRole(javaRepository.findById(USER_ROLE_ID));
    }

    @Test
    void legacyRepositoryDerivesQueriesOnTheNamedDataSource() {
        assertSeededUserRole(legacyRepository.findByUserId(Constant.USER_ID));
        assertSeededUserRole(legacyRepository.findByRoleId(Constant.ROLE_ID));
        assertSeededUserRole(legacyRepository.findByUserIdAndRoleId(Constant.USER_ID, Constant.ROLE_ID));
    }

    private static void assertSeededUserRole(UserRole role) {
        assertNotNull(role);
        assertEquals(USER_ROLE_ID, role.id());
        assertEquals(Constant.USER_ID, role.userId());
        assertEquals(Constant.ROLE_ID, role.roleId());
    }
}
