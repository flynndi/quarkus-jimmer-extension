package io.quarkiverse.jimmer.runtime.repository;

import org.babyfish.jimmer.sql.JSqlClient;

public class TestInterfaceImpl<T> implements TestInterface<T> {

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
    public String test(T t) {
        System.out.println("t = " + t);
        System.out.println("jSqlClient = " + jSqlClient);
        System.out.println("entityType = " + entityType);
        return t.toString();
    }
}
