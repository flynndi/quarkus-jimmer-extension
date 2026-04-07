package io.quarkiverse.jimmer.deployment.graphql.codegen;

import java.util.List;

record JimmerGraphQLSourceMethod(
        String name,
        String returnType,
        boolean collection,
        String elementType,
        boolean complex,
        boolean transientResolver,
        List<String> annotations) {
}
