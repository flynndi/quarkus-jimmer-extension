package io.quarkiverse.jimmer.it.config.h;

import org.babyfish.jimmer.sql.JSqlClient;

import io.quarkiverse.jimmer.it.entity.Book;
import io.quarkiverse.jimmer.runtime.repository.TestInterface;
import io.quarkiverse.jimmer.runtime.repository.TestInterfaceImpl;

/**
 * template
 */
//@Unremovable
//@ApplicationScoped
public class TestRepositoryImpl implements TestRepository {

    private final TestInterface defaultImpl;

    public TestRepositoryImpl(JSqlClient var1) throws ClassNotFoundException {
        ClassLoader var2 = Thread.currentThread().getContextClassLoader();
        Class var3 = Class.forName("io.quarkiverse.jimmer.it.entity.Book", false, var2);
        TestInterface var4 = new TestInterfaceImpl(var1, var3);
        this.defaultImpl = var4;
    }

    @Override
    public Book findNullable(Long aLong) {
        return (Book) this.defaultImpl.findNullable(aLong);
    }

    public JSqlClient sql() {
        return this.defaultImpl.sql();
    }

    public Class entityType() {
        return this.defaultImpl.entityType();
    }
}
