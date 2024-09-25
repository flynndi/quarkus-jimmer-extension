package io.quarkiverse.jimmer.runtime.repository;

public class TestInterfaceImpl<T> implements TestInterface<T> {

    public TestInterfaceImpl() {
    }

    @Override
    public String test(T t) {
        return t.toString();
    }
}
