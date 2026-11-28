package io.quarkiverse.jimmer.runtime;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

import org.jetbrains.annotations.Nullable;

import io.quarkus.arc.Arc;
import io.quarkus.arc.BeanDestroyer;
import io.quarkus.arc.InstanceHandle;
import io.quarkus.arc.SyntheticCreationalContext;

/**
 * Owns and releases {@code @Dependent} SPI instances acquired by synthetic CDI-managed SQL clients.
 */
public final class SqlClientDestroyer implements BeanDestroyer<Object> {

    private static final ConcurrentMap<IdentityKey, DependentHandles> OWNED_HANDLES = new ConcurrentHashMap<>();

    public SqlClientDestroyer() {
    }

    static <C> C create(
            SyntheticCreationalContext<C> context,
            @Nullable String dataSourceName,
            Function<SqlClientBeans, C> factory) {
        DependentHandles handles = new DependentHandles();
        try {
            C client = factory.apply(new SqlClientBeans(Arc.requireContainer(), dataSourceName, handles::track));
            if (client != null) {
                OWNED_HANDLES.put(new IdentityKey(client), handles);
            } else {
                handles.close();
            }
            return client;
        } catch (RuntimeException | Error failure) {
            // A failed synthetic bean never reaches CDI destruction; release dependent instances created so far.
            try {
                handles.close();
            } catch (RuntimeException | Error cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            try {
                context.release();
            } catch (RuntimeException | Error cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    @Override
    public void destroy(Object instance, SyntheticCreationalContext<Object> context) {
        if (instance != null) {
            DependentHandles handles = OWNED_HANDLES.remove(new IdentityKey(instance));
            if (handles != null) {
                handles.close();
            }
        }
    }

    private static final class IdentityKey {

        private final Object target;

        private final int hash;

        private IdentityKey(Object target) {
            this.target = target;
            this.hash = System.identityHashCode(target);
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof IdentityKey other && this.target == other.target;
        }
    }

    private static final class DependentHandles {

        private Deque<InstanceHandle<?>> stack = new ArrayDeque<>();

        private void track(InstanceHandle<?> handle) {
            synchronized (this) {
                if (stack != null) {
                    stack.push(handle);
                    return;
                }
            }
            // A lazy provider can finish resolving a bean while the client is being destroyed.
            handle.destroy();
            throw new IllegalStateException("The SQL client has already been destroyed");
        }

        private void close() {
            Deque<InstanceHandle<?>> handles;
            synchronized (this) {
                handles = stack;
                stack = null;
            }
            if (handles == null) {
                return;
            }
            Throwable failure = null;
            for (InstanceHandle<?> handle : handles) {
                try {
                    handle.destroy();
                } catch (RuntimeException | Error ex) {
                    if (failure == null) {
                        failure = ex;
                    } else if (failure != ex) {
                        failure.addSuppressed(ex);
                    }
                }
            }
            if (failure instanceof RuntimeException ex) {
                throw ex;
            }
            if (failure instanceof Error error) {
                throw error;
            }
        }
    }
}
