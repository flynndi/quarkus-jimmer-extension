package io.quarkiverse.jimmer.runtime.graphql;

import java.util.List;

import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.TypeDefinitionRegistry;
import graphql.schema.idl.WiringFactory;

public interface RuntimeWiringConfigurer {

    /**
     * Provides the configurer access to the {@link TypeDefinitionRegistry}.
     *
     * @param registry the registry
     * @since 1.3.0
     */
    default void setTypeDefinitionRegistry(TypeDefinitionRegistry registry) {
        // no-op
    }

    /**
     * Apply changes to the {@link RuntimeWiring.Builder} such as registering
     * {@link graphql.schema.DataFetcher}s, custom scalar types, and more.
     *
     * @param builder the builder to configure
     */
    void configure(RuntimeWiring.Builder builder);

    /**
     * Variant of {@link #configure(RuntimeWiring.Builder)} that also collects
     * {@link WiringFactory} instances that are then combined as one via
     * {@link graphql.schema.idl.CombinedWiringFactory}.
     *
     * @param builder the builder to configure
     * @param container the list of configured factories to add or insert into
     */
    default void configure(RuntimeWiring.Builder builder, List<WiringFactory> container) {
        // no-op
    }
}
