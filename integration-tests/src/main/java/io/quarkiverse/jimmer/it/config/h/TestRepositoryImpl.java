package io.quarkiverse.jimmer.it.config.h;

import org.babyfish.jimmer.sql.JSqlClient;

import io.quarkiverse.jimmer.it.entity.Book;
import io.quarkiverse.jimmer.runtime.repository.TestInterfaceImpl;

/**
 * template
 */
//@Unremovable
//@ApplicationScoped
public class TestRepositoryImpl implements TestRepository {

    private final TestInterfaceImpl testInterface;

    public TestRepositoryImpl(JSqlClient jSqlClient) throws ClassNotFoundException {
        ClassLoader var1 = Thread.currentThread().getContextClassLoader();
        Class var2 = Class.forName("io.quarkiverse.jimmer.it.entity.Book", false, var1);
        this.testInterface = new TestInterfaceImpl<>(jSqlClient, var2);
    }

    @Override
    public JSqlClient sql() {
        return testInterface.sql();
    }

    @Override
    public Class<Book> entityType() {
        return testInterface.entityType();
    }

    @Override
    public Book findNullable(Long aLong) {
        return (Book) testInterface.findNullable(aLong);
    }
}
