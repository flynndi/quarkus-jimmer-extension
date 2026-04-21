package io.quarkiverse.jimmer.graphql.apt;

import java.util.List;

import com.squareup.javapoet.AnnotationSpec;

record JimmerGraphQLSourceMethod(
        String name,
        String rawAccessorName,
        String returnType,
        boolean collection,
        String elementType,
        boolean complex,
        boolean transientResolver,
        String graphQLName,
        List<AnnotationSpec> annotations) {
}
