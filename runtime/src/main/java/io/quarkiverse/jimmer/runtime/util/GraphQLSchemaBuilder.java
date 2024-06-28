package io.quarkiverse.jimmer.runtime.util;

import static graphql.Scalars.GraphQLString;
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition;
import static graphql.schema.GraphQLObjectType.newObject;
import static graphql.schema.GraphQLTypeReference.typeRef;

import java.util.HashMap;
import java.util.Map;

import graphql.schema.*;

public class GraphQLSchemaBuilder {

    private Map<String, GraphQLObjectType> typeMap = new HashMap<>();

    public static GraphQLInterfaceType buildInterfaceType(Class<?> iface) {
        // Extract interface name and fields
        String name = iface.getSimpleName();
        GraphQLInterfaceType.Builder builder = GraphQLInterfaceType.newInterface()
                .name(name);

        for (var method : iface.getDeclaredMethods()) {
            builder.field(newFieldDefinition()
                    .name(method.getName())
                    .type(GraphQLString));
        }

        //        builder.typeResolver(env -> {
        //            Object javaObject = env.getObject();
        //            return typeMap.get(javaObject.getClass().getSimpleName());
        //        });

        return builder.build();
    }

    public static GraphQLInterfaceType buildInterfaceType(String interfaceName) {
        Class<?> iface = null;
        try {
            iface = Class.forName(interfaceName, true, Thread.currentThread().getContextClassLoader());
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
        GraphQLInterfaceType.Builder builder = GraphQLInterfaceType.newInterface()
                .name(interfaceName.replace(".", "_").replace("$", ""));

        for (var method : iface.getDeclaredMethods()) {
            builder.field(newFieldDefinition()
                    .name(method.getName())
                    .type(GraphQLString));
        }
        return builder.build();
    }

    public GraphQLObjectType buildObjectType(Class<?> implClass, GraphQLInterfaceType interfaceType) {
        // Extract class name and fields
        String name = implClass.getSimpleName();
        GraphQLObjectType.Builder builder = newObject()
                .name(name)
                .withInterface(typeRef(interfaceType.getName()));

        for (var method : implClass.getDeclaredMethods()) {
            builder.field(newFieldDefinition()
                    .name(method.getName())
                    .type(GraphQLString));
        }

        GraphQLObjectType objectType = builder.build();
        typeMap.put(name, objectType);
        return objectType;
    }

    //    public static void main(String[] args) {
    //        GraphQLSchemaBuilder builder = new GraphQLSchemaBuilder();
    //        GraphQLInterfaceType animalInterface = builder.buildInterfaceType(Animal.class);
    //        GraphQLObjectType dogType = builder.buildObjectType(Dog.class, animalInterface);
    //        GraphQLObjectType catType = builder.buildObjectType(Cat.class, animalInterface);
    //
    //        GraphQLSchema schema = builder.buildSchema(animalInterface, List.of(dogType, catType));
    //
    //        GraphQL graphQL = GraphQL.newGraphQL(schema).build();
    //        String query = "{ items { name } }";
    //        ExecutionResult result = graphQL.execute(query);
    //        System.out.println(result.getData().toString());
    //    }
}
