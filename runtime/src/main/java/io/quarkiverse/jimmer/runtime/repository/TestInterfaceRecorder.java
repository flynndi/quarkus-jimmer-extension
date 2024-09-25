package io.quarkiverse.jimmer.runtime.repository;

import java.util.function.Function;
import java.util.function.Supplier;

import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class TestInterfaceRecorder {

    public Function<SyntheticCreationalContext<TestInterface>, TestInterface> createTestInterface(String name) {
        return testInterfaceSyntheticCreationalContext -> {
            try {
                return (TestInterface) (Class.forName(name, true, Thread.currentThread().getContextClassLoader())
                        .newInstance());
            } catch (ClassNotFoundException | InstantiationException | IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        };
    }
}
