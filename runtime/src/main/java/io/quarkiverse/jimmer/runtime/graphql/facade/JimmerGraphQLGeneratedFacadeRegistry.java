package io.quarkiverse.jimmer.runtime.graphql.facade;

public interface JimmerGraphQLGeneratedFacadeRegistry {

    boolean supportsFacadeType(Class<?> facadeType);

    boolean supportsRaw(Object raw);

    <T> T wrap(Object raw, Class<T> facadeType);

    Object wrap(Object raw);
}
