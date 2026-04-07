package io.quarkiverse.jimmer.runtime.graphql.facade;

public interface JimmerGraphQLGeneratedFacadeRegistry {

    <T> T wrap(Object raw, Class<T> facadeType);

    Object wrap(Object raw);
}
