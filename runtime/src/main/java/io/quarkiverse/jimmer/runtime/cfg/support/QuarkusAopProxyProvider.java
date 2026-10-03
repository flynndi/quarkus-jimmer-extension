package io.quarkiverse.jimmer.runtime.cfg.support;

import org.babyfish.jimmer.sql.di.AopProxyProvider;

import io.quarkus.arc.ClientProxy;
import io.quarkus.arc.InjectableBean;
import io.quarkus.arc.Subclass;

/** Resolves metadata without replacing the CDI reference used to invoke an extension point. */
public final class QuarkusAopProxyProvider implements AopProxyProvider {

    @Override
    public Class<?> getTargetClass(Object instance) {
        while (instance instanceof ClientProxy proxy) {
            if (proxy.arc_bean().getKind() == InjectableBean.Kind.CLASS) {
                return proxy.arc_bean().getBeanClass();
            }
            // A producer's declared return type need not be the implementation type.
            // Unwrap only for inspection; callers keep invoking the original CDI reference.
            instance = proxy.arc_contextualInstance();
        }
        Class<?> type = instance.getClass();
        while (Subclass.class.isAssignableFrom(type)) {
            type = type.getSuperclass();
        }
        return type;
    }
}
