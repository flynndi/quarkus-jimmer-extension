package io.quarkiverse.jimmer.runtime.repository;

import org.babyfish.jimmer.sql.JSqlClient;
import org.babyfish.jimmer.sql.runtime.JSqlClientImplementor;

import io.quarkiverse.jimmer.runtime.repository.support.Utils;

public class TestInterfaceImpl<E, ID> implements TestInterface<E, ID> {

    protected final JSqlClientImplementor sqlClient;

    protected final Class<E> entityType;

    protected TestInterfaceImpl(JSqlClient sqlClient) {
        this(sqlClient, null);
    }

    public TestInterfaceImpl(JSqlClient sqlClient, Class<E> entityType) {
        this.sqlClient = Utils.validateSqlClient(sqlClient);
        this.entityType = entityType;
    }

    @Override
    public JSqlClient sql() {
        return sqlClient;
    }

    @Override
    public Class<E> entityType() {
        return entityType;
    }

    @Override
    public E findNullable(ID id) {
        return sqlClient.getEntities().findById(entityType, id);
    }
}
