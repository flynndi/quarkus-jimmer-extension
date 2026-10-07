package io.quarkiverse.jimmer.it.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.quarkiverse.jimmer.it.Constant;
import io.quarkiverse.jimmer.it.entity.UserRole;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class TestJavaRepositoryTestCase {

    @Inject
    UserRoleJavaRepository userRoleJavaRepository;

    @Test
    void findsUuidEntityUsingTheNamedSqlClient() {
        UserRole userRole = userRoleJavaRepository.findById(UUID.fromString(Constant.USER_ROLE_ID));

        assertNotNull(userRole);
        assertEquals(UUID.fromString(Constant.USER_ROLE_ID), userRole.id());
        assertEquals(Constant.USER_ID, userRole.userId());
        assertEquals(Constant.ROLE_ID, userRole.roleId());
    }
}
