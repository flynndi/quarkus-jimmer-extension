package io.quarkiverse.jimmer.runtime.graphql;

import java.util.Map;

import jakarta.json.bind.Jsonb;

import graphql.GraphQL;
import graphql.schema.*;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ArcContainer;
import io.smallrye.graphql.api.Context;
import io.smallrye.graphql.execution.event.InvokeInfo;
import io.smallrye.graphql.schema.model.Operation;
import io.smallrye.graphql.spi.EventingService;
import org.jboss.jandex.JandexAntTask;
import org.jboss.jandex.JandexReflection;
import org.jboss.jandex.JarIndexer;

public class JimmerGraphqlEventService implements EventingService {

    @Override
    public String getConfigKey() {
        return null;
    }

    @Override
    public GraphQLSchema.Builder beforeSchemaBuild(GraphQLSchema.Builder builder) {
        //        GraphQLSchema graphQLSchema = new DefaultSchemaResourceGraphQlSourceBuilder().initGraphQlSchema();
        //        return GraphQLSchema.newSchema(graphQLSchema);
        ArcContainer container = Arc.container();
        System.out.println("container = " + container);
        GraphQLSchema graphQLSchema = new DefaultSchemaResourceGraphQlSourceBuilder().initGraphQlSchema();
        return GraphQLSchema.newSchema(graphQLSchema);
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
