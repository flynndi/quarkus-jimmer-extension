package io.quarkiverse.jimmer.graphql.apt;

import java.util.List;

record JimmerGraphQLSourceMethod(
        String name,
        String rawAccessorName,
        String returnType,
        boolean collection,
        String elementType,
        boolean complex,
        boolean transientResolver,
        List<String> annotations) {
}
