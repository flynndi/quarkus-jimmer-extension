package io.quarkiverse.jimmer.runtime.graphql.facade;

import java.util.ArrayList;
import java.util.List;

import io.quarkus.arc.Arc;
import io.quarkus.arc.InstanceHandle;

public final class JimmerGraphQLFacades {

    private JimmerGraphQLFacades() {
    }

    public static <T> T wrap(Object raw, Class<T> facadeType) {
        return registry().wrap(raw, facadeType);
    }

    public static Object wrap(Object raw) {
        return registry().wrap(raw);
    }

    public static <T> List<T> wrapList(Iterable<?> values, Class<T> facadeType) {
        if (values == null) {
            return null;
        }
        List<T> wrappedValues = new ArrayList<>();
        for (Object value : values) {
            wrappedValues.add(wrap(value, facadeType));
        }
        return wrappedValues;
    }

    private static JimmerGraphQLGeneratedFacadeRegistry registry() {
        InstanceHandle<JimmerGraphQLGeneratedFacadeRegistry> handle = Arc.container()
                .instance(JimmerGraphQLGeneratedFacadeRegistry.class);
        if (!handle.isAvailable()) {
            throw new IllegalStateException("No generated GraphQL facade registry is available");
        }
        return handle.get();
    }
}
