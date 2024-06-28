package io.quarkiverse.jimmer.runtime.graphql;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import graphql.language.InterfaceTypeDefinition;
import graphql.language.UnionTypeDefinition;
import graphql.schema.DataFetcher;
import graphql.schema.GraphQLSchema;
import graphql.schema.TypeResolver;
import graphql.schema.idl.*;
import io.quarkiverse.jimmer.runtime.util.StringUtils;
import io.quarkus.arc.Arc;
import io.quarkus.arc.InstanceHandle;

final class DefaultSchemaResourceGraphQlSourceBuilder {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultSchemaResourceGraphQlSourceBuilder.class);

    private final List<RuntimeWiringConfigurer> runtimeWiringConfigurers = new ArrayList<>();

    @Nullable
    private BiFunction<TypeDefinitionRegistry, RuntimeWiring, GraphQLSchema> schemaFactory;

    GraphQLSchema initGraphQlSchema() {
        List<InputStream> schemaResources = new ArrayList<>();
        Enumeration<URL> schemaResourcesUrls = null;
        try {
            schemaResourcesUrls = Thread.currentThread().getContextClassLoader().getResources("graphql/schema.graphqls");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        if (schemaResourcesUrls.hasMoreElements()) {
            try {
                schemaResources.add(schemaResourcesUrls.nextElement().openStream());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        TypeDefinitionRegistry registry = schemaResources.stream()
                .map(this::parse)
                .reduce(TypeDefinitionRegistry::merge)
                .orElseThrow(MissingSchemaException::new);

        //        LOGGER.info("Loaded " + this.schemaResources.size() + " resource(s) in the GraphQL schema.");
        //        if (LOGGER.isDebugEnabled()) {
        //            String resources = this.schemaResources.stream()
        //                    .map(Resource::getDescription)
        //                    .collect(Collectors.joining(","));
        //            LOGGER.debug("Loaded GraphQL schema resources: (" + resources + ")");
        //        }

        List<InstanceHandle<RuntimeWiringConfigurer>> instanceHandles = Arc.container().listAll(RuntimeWiringConfigurer.class);
        if (!instanceHandles.isEmpty()) {
            for (InstanceHandle<RuntimeWiringConfigurer> instanceHandle : instanceHandles) {
                RuntimeWiringConfigurer runtimeWiringConfigurer = instanceHandle.get();
                runtimeWiringConfigurers.add(runtimeWiringConfigurer);
            }
        }

        RuntimeWiring runtimeWiring = initRuntimeWiring(registry);
        updateForCustomRootOperationTypeNames(registry, runtimeWiring);

        TypeResolver typeResolver = initTypeResolver();
        registry.types().values().forEach((def) -> {
            if (def instanceof UnionTypeDefinition || def instanceof InterfaceTypeDefinition) {
                runtimeWiring.getTypeResolvers().putIfAbsent(def.getName(), typeResolver);
            }
        });

        // SchemaMappingInspector needs RuntimeWiring, but cannot run here since type
        // visitors may transform the schema, for example to add Connection types.

        //        if (this.schemaReportConsumer != null) {
        //            this.schemaReportRunner = (schema) ->
        //                    this.schemaReportConsumer.accept(createSchemaReport(schema, runtimeWiring));
        //        }

        return (this.schemaFactory != null) ? this.schemaFactory.apply(registry, runtimeWiring)
                : new SchemaGenerator().makeExecutableSchema(registry, runtimeWiring);
    }

    private TypeDefinitionRegistry parse(InputStream inputStream) {
        try {
            return new SchemaParser().parse(inputStream);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to parse schema resource: " + ex);
        }
    }

    private RuntimeWiring initRuntimeWiring(TypeDefinitionRegistry typeRegistry) {
        RuntimeWiring.Builder builder = RuntimeWiring.newRuntimeWiring();
        this.runtimeWiringConfigurers.forEach((configurer) -> {
            configurer.setTypeDefinitionRegistry(typeRegistry);
            configurer.configure(builder);
        });

        List<WiringFactory> factories = new ArrayList<>();
        WiringFactory factory = builder.build().getWiringFactory();
        if (!factory.getClass().equals(NoopWiringFactory.class)) {
            factories.add(factory);
        }
        this.runtimeWiringConfigurers.forEach((configurer) -> configurer.configure(builder, factories));
        if (!factories.isEmpty()) {
            builder.wiringFactory(new CombinedWiringFactory(factories));
        }

        return builder.build();
    }

    private static void updateForCustomRootOperationTypeNames(
            TypeDefinitionRegistry registry, RuntimeWiring runtimeWiring) {

        if (registry.schemaDefinition().isEmpty()) {
            return;
        }

        registry.schemaDefinition().get().getOperationTypeDefinitions().forEach((definition) -> {
            String name = StringUtils.capitalize(definition.getName());
            Map<String, DataFetcher> dataFetcherMap = runtimeWiring.getDataFetchers().remove(name);
            if (null == dataFetcherMap || dataFetcherMap.isEmpty()) {
                runtimeWiring.getDataFetchers().put(definition.getTypeName().getName(), dataFetcherMap);
            }
        });
    }

    private TypeResolver initTypeResolver() {
        return new ClassNameTypeResolver();
    }
}
