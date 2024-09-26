package io.quarkiverse.jimmer.runtime.repository;

import java.util.function.Function;
import java.util.function.Supplier;

import org.jboss.invocation.proxy.ProxyConfiguration;
import org.jboss.invocation.proxy.ProxyFactory;

import io.quarkiverse.jimmer.runtime.util.InvocationDelegate;
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

    public Supplier<Object> testInterfaceSupply(String name) {
        return () -> {
            Class<?> aClass = null;
            try {
                aClass = Class.forName(name, true, Thread.currentThread().getContextClassLoader());
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
            try {
                //                JSqlClient jSqlClient = Arc.container().instance(JSqlClient.class).get();
                //                return aClass.getDeclaredConstructor(JSqlClient.class).newInstance(jSqlClient);
                InvocationDelegate invocationDelegate = new InvocationDelegate();
                ProxyConfiguration<Object> proxyConfiguration = new ProxyConfiguration<>()
                        .setSuperClass(Object.class)
                        .setProxyName("$Proxy2")
                        .setClassLoader(Thread.currentThread().getContextClassLoader())
                        .addAdditionalInterface(TestInterface.class);
                return (TestInterface) new ProxyFactory<>(proxyConfiguration).newInstance(invocationDelegate);
            } catch (InstantiationException | IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        };
    }
}
