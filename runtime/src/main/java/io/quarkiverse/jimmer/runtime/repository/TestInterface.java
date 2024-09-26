package io.quarkiverse.jimmer.runtime.repository;

public interface TestInterface<T, ID> {

    String test(T t, ID id);
}
