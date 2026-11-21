package io.quarkiverse.jimmer.runtime.cfg.support;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Set;
import java.util.function.Function;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Instance;

import io.quarkus.arc.ArcContainer;
import io.quarkus.arc.InstanceHandle;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.arc.impl.InstanceImpl;

/** Determines who releases the CDI plugins obtained for a SQL client. */
@FunctionalInterface
public interface SqlClientBeanAccess {

    /** Manual clients have no close hook to release dependent instances. */
    SqlClientBeanAccess UNMANAGED = InstanceHandle::get;

    <T> T get(InstanceHandle<T> handle);

    default <T> T lookup(ArcContainer container, Function<Instance<Object>, T> operation) {
        return operation.apply(container.beanManager().createInstance());
    }

    static <C> C createManaged(SyntheticCreationalContext<C> context, Function<SqlClientBeanAccess, C> create) {
        try {
            return create.apply(context.getInjectedReference(Managed.class));
        } catch (RuntimeException | Error failure) {
            // A failed synthetic bean never reaches CDI destruction.
            try {
                context.release();
            } catch (RuntimeException | Error cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    /** A dependent injection point of each synthetic SQL client, including named clients. */
    @Dependent
    final class Managed implements SqlClientBeanAccess {

        private Deque<Runnable> dependentDestructions = new ArrayDeque<>();

        @Override
        public <T> T get(InstanceHandle<T> handle) {
            T instance = handle.get();
            var bean = handle.getBean();
            if (instance != null && bean != null && bean.getScope() == Dependent.class) {
                retain(handle::destroy);
            }
            return instance;
        }

        @Override
        public <T> T lookup(ArcContainer container, Function<Instance<Object>, T> operation) {
            // A private ArC lookup owns the producer's creational context even if the product is a shared proxy.
            // Instance.destroy(product) would destroy that proxy's contextual instance instead.
            var lookup = InstanceImpl.forGlobalEntrypoint(Object.class, Set.of());
            T result;
            try {
                result = operation.apply(lookup);
            } catch (RuntimeException | Error failure) {
                lookup.destroy();
                throw failure;
            }
            if (lookup.hasDependentInstances()) {
                retain(lookup::destroy);
            }
            return result;
        }

        private void retain(Runnable destroy) {
            synchronized (this) {
                if (dependentDestructions != null) {
                    dependentDestructions.push(destroy);
                    return;
                }
            }
            // A lazy provider can finish resolving a bean while the client is being destroyed.
            destroy.run();
            throw new IllegalStateException("The SQL client has already been destroyed");
        }

        @PreDestroy
        void destroy() {
            Deque<Runnable> destructions;
            synchronized (this) {
                destructions = dependentDestructions;
                dependentDestructions = null;
            }
            if (destructions != null) {
                destructions.forEach(Runnable::run);
            }
        }
    }
}
