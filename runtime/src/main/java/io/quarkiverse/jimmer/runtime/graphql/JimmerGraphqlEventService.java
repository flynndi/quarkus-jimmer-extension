package io.quarkiverse.jimmer.runtime.graphql;

import java.util.Map;

import jakarta.json.bind.Jsonb;

import graphql.GraphQL;
import graphql.schema.*;
import io.quarkiverse.jimmer.runtime.util.GraphQLSchemaBuilder;
import io.smallrye.graphql.api.Context;
import io.smallrye.graphql.execution.event.InvokeInfo;
import io.smallrye.graphql.schema.model.Operation;
import io.smallrye.graphql.spi.EventingService;

public class JimmerGraphqlEventService implements EventingService {

    @Override
    public String getConfigKey() {
        return null;
    }

    @Override
    public GraphQLSchema.Builder beforeSchemaBuild(GraphQLSchema.Builder builder) {
        //        GraphQLSchema graphQLSchema = new DefaultSchemaResourceGraphQlSourceBuilder().initGraphQlSchema();
        //        return GraphQLSchema.newSchema(graphQLSchema);

        ClassNameTypeResolver classNameTypeResolver = new ClassNameTypeResolver();
        GraphQLCodeRegistry codeRegistry = GraphQLCodeRegistry.newCodeRegistry()
                .typeResolver("io_quarkiverse_jimmer_it_entity_UserRole", classNameTypeResolver)
                .typeResolver("io_quarkiverse_jimmer_it_entity_Author", classNameTypeResolver)
                .typeResolver("io_quarkiverse_jimmer_it_entity_Book", classNameTypeResolver)
                .typeResolver("io_quarkiverse_jimmer_it_entity_BookStore", classNameTypeResolver)
                .typeResolver("io_quarkiverse_jimmer_it_entity_TreeNode", classNameTypeResolver)
                .typeResolver("io_quarkiverse_jimmer_it_entity_BaseEntity", classNameTypeResolver)
                .typeResolver("io_quarkiverse_jimmer_it_entity_TenantAware", classNameTypeResolver)
                .typeResolver("org_babyfish_jimmer_runtime_ImmutableSpi", classNameTypeResolver)
                .typeResolver("io_quarkiverse_jimmer_it_entity_UserRoleDraftProducerImplementor", classNameTypeResolver)
                .typeResolver("io_quarkiverse_jimmer_it_entity_AuthorDraftProducerImplementor", classNameTypeResolver)
                .typeResolver("io_quarkiverse_jimmer_it_entity_BookDraftProducerImplementor", classNameTypeResolver)
                .typeResolver("io_quarkiverse_jimmer_it_entity_BookStoreDraftProducerImplementor", classNameTypeResolver)
                .typeResolver("io_quarkiverse_jimmer_it_entity_TreeNodeDraftProducerImplementor", classNameTypeResolver)
                .build();

        GraphQLInterfaceType one = GraphQLSchemaBuilder
                .buildInterfaceType("io.quarkiverse.jimmer.it.entity.UserRole");
        GraphQLInterfaceType two = GraphQLSchemaBuilder
                .buildInterfaceType("io.quarkiverse.jimmer.it.entity.Author");
        GraphQLInterfaceType three = GraphQLSchemaBuilder
                .buildInterfaceType("io.quarkiverse.jimmer.it.entity.Book");
        GraphQLInterfaceType four = GraphQLSchemaBuilder
                .buildInterfaceType("io.quarkiverse.jimmer.it.entity.BookStore");
        GraphQLInterfaceType five = GraphQLSchemaBuilder
                .buildInterfaceType("io.quarkiverse.jimmer.it.entity.TreeNode");
        GraphQLInterfaceType evelen = GraphQLSchemaBuilder
                .buildInterfaceType("io.quarkiverse.jimmer.it.entity.BaseEntity");
        GraphQLInterfaceType threetin = GraphQLSchemaBuilder
                .buildInterfaceType("io.quarkiverse.jimmer.it.entity.TenantAware");
        GraphQLInterfaceType tw = GraphQLSchemaBuilder
                .buildInterfaceType("org.babyfish.jimmer.runtime.ImmutableSpi");
        //        GraphQLInterfaceType six = GraphQLSchemaBuilder
        //                .buildInterfaceType("io.quarkiverse.jimmer.it.entity.UserRoleDraft$Producer$Implementor");
        //        GraphQLInterfaceType seven = GraphQLSchemaBuilder
        //                .buildInterfaceType("io.quarkiverse.jimmer.it.entity.AuthorDraft$Producer$Implementor");
        //        GraphQLInterfaceType eight = GraphQLSchemaBuilder
        //                .buildInterfaceType("io.quarkiverse.jimmer.it.entity.BookDraft$Producer$Implementor");
        //        GraphQLInterfaceType nine = GraphQLSchemaBuilder
        //                .buildInterfaceType("io.quarkiverse.jimmer.it.entity.BookStoreDraft$Producer$Implementor");
        //        GraphQLInterfaceType ten = GraphQLSchemaBuilder
        //                .buildInterfaceType("io.quarkiverse.jimmer.it.entity.TreeNodeDraft$Producer$Implementor");

        return builder.codeRegistry(codeRegistry)
                .additionalType(one)
                .additionalType(two)
                .additionalType(three)
                .additionalType(four)
                .additionalType(five)
                .additionalType(evelen)
                .additionalType(tw)
                .additionalType(threetin);
        //                .additionalType(six)
        //                .additionalType(seven)
        //                .additionalType(eight)
        //                .additionalType(nine)
        //                .additionalType(ten);
        //        return EventingService.super.beforeSchemaBuild(builder);
    }

    @Override
    public Operation createOperation(Operation operation) {
        return EventingService.super.createOperation(operation);
    }

    @Override
    public Map<String, Jsonb> overrideJsonbConfig() {
        return EventingService.super.overrideJsonbConfig();
    }

    @Override
    public GraphQL.Builder beforeGraphQLBuild(GraphQL.Builder builder) {
        return EventingService.super.beforeGraphQLBuild(builder);
    }

    @Override
    public void beforeExecute(Context context) {
        EventingService.super.beforeExecute(context);
    }

    @Override
    public void afterExecute(Context context) {
        EventingService.super.afterExecute(context);
    }

    @Override
    public void errorExecute(String executionId, Throwable t) {
        EventingService.super.errorExecute(executionId, t);
    }

    @Override
    public void errorExecute(Context context, Throwable t) {
        EventingService.super.errorExecute(context, t);
    }

    @Override
    public void beforeDataFetch(Context context) {
        EventingService.super.beforeDataFetch(context);
    }

    @Override
    public void beforeInvoke(InvokeInfo invokeInfo) throws Exception {
        EventingService.super.beforeInvoke(invokeInfo);
    }

    @Override
    public void afterDataFetch(Context context) {
        EventingService.super.afterDataFetch(context);
    }

    @Override
    public void errorDataFetch(String executionId, Throwable t) {
        EventingService.super.errorDataFetch(executionId, t);
    }

    @Override
    public void errorDataFetch(Context context, Throwable t) {
        EventingService.super.errorDataFetch(context, t);
    }
}
