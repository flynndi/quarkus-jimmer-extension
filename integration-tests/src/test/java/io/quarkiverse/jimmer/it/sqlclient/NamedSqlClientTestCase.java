package io.quarkiverse.jimmer.it.sqlclient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.sql.SQLException;
import java.util.UUID;

import jakarta.inject.Inject;

import org.babyfish.jimmer.sql.JSqlClient;
import org.junit.jupiter.api.Test;

import io.quarkiverse.jimmer.it.Constant;
import io.quarkiverse.jimmer.it.entity.UserRole;
import io.quarkiverse.jimmer.it.entity.UserRoleTable;
import io.quarkus.agroal.DataSource;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class NamedSqlClientTestCase {

    @Inject
    @DataSource(Constant.DATASOURCE2)
    JSqlClient sqlClient;

    @Inject
    @DataSource(Constant.DATASOURCE2)
    javax.sql.DataSource dataSource;

    @Test
    void namedClientReadsUuidAndSerializedPropertiesFromItsOwnDatabase() throws SQLException {
        UUID id = UUID.randomUUID();
        try (var connection = dataSource.getConnection()) {
            try {
                try (var insert = connection.prepareStatement(
                        "insert into user_role(id, user_id, role_id, delete_flag, auth_user) values (?, ?, ?, false, ?)")) {
                    insert.setString(1, id.toString());
                    insert.setString(2, "named-client-user");
                    insert.setString(3, "named-client-role");
                    insert.setString(4, "{\"id\":\"named-client-auth\"}");
                    assertEquals(1, insert.executeUpdate());
                }

                var table = UserRoleTable.$;
                UserRole role = sqlClient.createQuery(table)
                        .where(table.id().eq(id))
                        .select(table)
                        .fetchOne();

                assertEquals(id, role.id());
                assertEquals("named-client-user", role.userId());
                assertEquals("named-client-role", role.roleId());
                assertNotNull(role.authUser());
                assertEquals("named-client-auth", role.authUser().getId());
            } finally {
                try (var delete = connection.prepareStatement("delete from user_role where id = ?")) {
                    delete.setString(1, id.toString());
                    delete.executeUpdate();
                }
            }
        }
    }
}
