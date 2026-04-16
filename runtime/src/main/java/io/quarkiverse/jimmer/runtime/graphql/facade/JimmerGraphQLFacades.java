package io.quarkiverse.jimmer.runtime.graphql.facade;

import java.util.ArrayList;
import java.util.List;

import io.quarkus.arc.Arc;
import io.quarkus.arc.InstanceHandle;

public final class JimmerGraphQLFacades {

    private JimmerGraphQLFacades() {
    }

    public static <T> T wrap(Object raw, Class<T> facadeType) {
        if (raw == null) {
            return null;
        }
        if (facadeType.isInstance(raw)) {
            return facadeType.cast(raw);
        }
        for (InstanceHandle<JimmerGraphQLGeneratedFacadeRegistry> handle : registries()) {
            JimmerGraphQLGeneratedFacadeRegistry registry = handle.get();
            if (registry.supportsFacadeType(facadeType)) {
                return registry.wrap(raw, facadeType);
            }
        }
        throw new IllegalArgumentException("Unsupported GraphQL facade type: " + facadeType.getName());
    }

    public static Object wrap(Object raw) {
        if (raw == null || raw instanceof JimmerGraphQLFacade<?>) {
            return raw;
        }
        for (InstanceHandle<JimmerGraphQLGeneratedFacadeRegistry> handle : registries()) {
            JimmerGraphQLGeneratedFacadeRegistry registry = handle.get();
            if (registry.supportsRaw(raw)) {
                return registry.wrap(raw);
            }
        }
        return raw;
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

    private static List<InstanceHandle<JimmerGraphQLGeneratedFacadeRegistry>> registries() {
        List<InstanceHandle<JimmerGraphQLGeneratedFacadeRegistry>> handles = Arc.container()
                .listAll(JimmerGraphQLGeneratedFacadeRegistry.class);
        if (handles.isEmpty()) {
            throw new IllegalStateException("No generated GraphQL facade registry is available");
        }
        return handles;
    }
}
