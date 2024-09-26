package io.quarkiverse.jimmer.runtime.repository;

import org.babyfish.jimmer.sql.JSqlClient;

public class TestInterfaceImpl<T, ID> implements TestInterface<T, ID> {

    private final JSqlClient jSqlClient;

    protected final Class<T> entityType;

    protected TestInterfaceImpl(JSqlClient jSqlClient) {
        this(jSqlClient, null);
    }

    public TestInterfaceImpl(JSqlClient jSqlClient, Class<T> entityType) {
        this.jSqlClient = jSqlClient;
        this.entityType = entityType;
    }

    @Override
    public String test(T t, ID id) {
        System.out.println("t = " + t);
        System.out.println("jSqlClient = " + jSqlClient);
        System.out.println("entityType = " + entityType);
        System.out.println("id = " + id);
        return t.toString();
    }
}
