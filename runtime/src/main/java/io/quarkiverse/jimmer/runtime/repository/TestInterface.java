package io.quarkiverse.jimmer.runtime.repository;

import org.babyfish.jimmer.sql.JSqlClient;

public interface TestInterface<E, ID> {

    JSqlClient sql();

    Class<E> entityType();

    E findNullable(ID id);
}
