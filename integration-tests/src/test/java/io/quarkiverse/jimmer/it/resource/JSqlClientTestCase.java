package io.quarkiverse.jimmer.it.resource;

import java.util.List;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.inject.Named;

import org.babyfish.jimmer.sql.JSqlClient;
import org.jboss.jandex.DotName;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.quarkiverse.jimmer.it.Constant;
import io.quarkiverse.jimmer.it.entity.*;
import io.quarkiverse.jimmer.runtime.Jimmer;
import io.quarkiverse.jimmer.runtime.java.JQuarkusSqlClientContainer;
import io.quarkus.agroal.DataSource;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class JSqlClientTestCase {

    @Inject
    JSqlClient jSqlClient;

    @Inject
    @DataSource("DB2")
    JSqlClient jSqlClientDB2;

    @Inject
    JQuarkusSqlClientContainer JQuarkusSqlClientContainer;

    @Inject
    @Named(Constant.CONTAINER_DATASOURCE2)
    JQuarkusSqlClientContainer JQuarkusSqlClientContainerDB2;

    @Test
    public void testBean() {
        Assertions.assertEquals(jSqlClient, Jimmer.getDefaultJSqlClient());
        Assertions.assertEquals(jSqlClientDB2, Jimmer.getJSqlClient(Constant.DATASOURCE2));
        Assertions.assertNotEquals(jSqlClient, jSqlClientDB2);
        Assertions.assertNotEquals(jSqlClient, Jimmer.getJSqlClient(Constant.DATASOURCE2));
        Assertions.assertNotEquals(jSqlClientDB2, Jimmer.getDefaultJSqlClient());

        Assertions.assertNotEquals(JQuarkusSqlClientContainer, JQuarkusSqlClientContainerDB2);
        Assertions.assertNotEquals(JQuarkusSqlClientContainer.getjSqlClient(),
                JQuarkusSqlClientContainerDB2.getjSqlClient());
        Assertions.assertEquals(JQuarkusSqlClientContainer.getjSqlClient(), jSqlClient);
        Assertions.assertEquals(JQuarkusSqlClientContainerDB2.getjSqlClient(), jSqlClientDB2);

        Assertions.assertEquals(Jimmer.getJSqlClientContainer(Constant.DEFAULT), JQuarkusSqlClientContainer);
        Assertions.assertEquals(Jimmer.getJSqlClientContainer(Constant.DATASOURCE2), JQuarkusSqlClientContainerDB2);
        Assertions.assertEquals(Jimmer.getJSqlClientContainer(Constant.DEFAULT).getjSqlClient(), jSqlClient);
        Assertions.assertEquals(Jimmer.getJSqlClientContainer(Constant.DATASOURCE2).getjSqlClient(), jSqlClientDB2);
    }

    @Test
    public void testTenantFilter() {
        List<Book> list = jSqlClient
                .createQuery(BookTable.$)
                .select(BookTable.$.fetch(Fetchers.BOOK_FETCHER.allTableFields()))
                .execute();
        Assertions.assertNotNull(list);
    }

    @Test
    public void testScalarProvider() {
        UserRole userRole = jSqlClientDB2
                .createQuery(UserRoleTable.$)
                .where(UserRoleTable.$.id().eq(UUID.fromString(Constant.USER_ROLE_ID)))
                .select(UserRoleTable.$.fetch(Fetchers.USER_ROLE_FETCHER.allTableFields()))
                .fetchOne();
        Assertions.assertNotNull(userRole);
        Assertions.assertEquals(userRole.id().getClass().getTypeName(), DotName.createSimple(UUID.class).local());
    }
}
