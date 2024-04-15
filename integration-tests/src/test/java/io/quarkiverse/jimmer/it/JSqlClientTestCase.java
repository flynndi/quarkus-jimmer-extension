package io.quarkiverse.jimmer.it;

import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.inject.Named;

import org.babyfish.jimmer.sql.JSqlClient;
import org.babyfish.jimmer.sql.ast.table.Props;
import org.babyfish.jimmer.sql.filter.Filter;
import org.babyfish.jimmer.sql.filter.Filters;
import org.jboss.jandex.DotName;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.quarkiverse.jimmer.it.config.TenantFilter;
import io.quarkiverse.jimmer.it.entity.*;
import io.quarkiverse.jimmer.runtime.Jimmer;
import io.quarkiverse.jimmer.runtime.java.QuarkusJSqlClientContainer;
import io.quarkus.agroal.DataSource;
import io.quarkus.arc.Arc;
import io.quarkus.arc.InstanceHandle;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class JSqlClientTestCase {

    @Inject
    JSqlClient jSqlClient;

    @Inject
    @DataSource(Constant.DATASOURCE2)
    JSqlClient jSqlClientDB2;

    @Inject
    QuarkusJSqlClientContainer QuarkusJSqlClientContainer;

    @Inject
    @Named(Constant.CONTAINER_DATASOURCE2)
    QuarkusJSqlClientContainer QuarkusJSqlClientContainerDB2;

    @Test
    public void testBean() {
        Assertions.assertEquals(jSqlClient, Jimmer.getDefaultJSqlClient());
        Assertions.assertEquals(jSqlClientDB2, Jimmer.getJSqlClient(Constant.DATASOURCE2));
        Assertions.assertNotEquals(jSqlClient, jSqlClientDB2);
        Assertions.assertNotEquals(jSqlClient, Jimmer.getJSqlClient(Constant.DATASOURCE2));
        Assertions.assertNotEquals(jSqlClientDB2, Jimmer.getDefaultJSqlClient());

        Assertions.assertNotEquals(QuarkusJSqlClientContainer, QuarkusJSqlClientContainerDB2);
        Assertions.assertNotEquals(QuarkusJSqlClientContainer.getjSqlClient(),
                QuarkusJSqlClientContainerDB2.getjSqlClient());
        Assertions.assertEquals(QuarkusJSqlClientContainer.getjSqlClient(), jSqlClient);
        Assertions.assertEquals(QuarkusJSqlClientContainerDB2.getjSqlClient(), jSqlClientDB2);

        Assertions.assertEquals(Jimmer.getJSqlClientContainer(Constant.DEFAULT), QuarkusJSqlClientContainer);
        Assertions.assertEquals(Jimmer.getJSqlClientContainer(Constant.DATASOURCE2), QuarkusJSqlClientContainerDB2);
        Assertions.assertEquals(Jimmer.getJSqlClientContainer(Constant.DEFAULT).getjSqlClient(), jSqlClient);
        Assertions.assertEquals(Jimmer.getJSqlClientContainer(Constant.DATASOURCE2).getjSqlClient(), jSqlClientDB2);
    }

    @Test
    public void testTenantFilter() {
        Filters filters = jSqlClient.getFilters();
        Filter<Props> filter = filters.getFilter(Book.class);
        InstanceHandle<TenantFilter> instance = Arc.container().instance(TenantFilter.class);
        Assertions.assertTrue(instance.isAvailable());
        Assertions.assertNotNull(instance.get());
        TenantFilter tenantFilter = instance.get();
        Assertions.assertEquals(filter.toString().replace("ExportedFilter{filters=[", "").replace("]}", ""),
                tenantFilter.toString());
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
