package io.quarkiverse.jimmer.deployment.graphql.codegen;

import java.util.List;

record JimmerGraphQLSourceType(
        String packageName,
        String simpleName,
        String qualifiedName,
        JimmerGraphQLSourceKind kind,
        List<String> imports,
        List<String> extendsTypes,
        List<JimmerGraphQLSourceMethod> methods) {

    boolean isEntity() {
        return kind == JimmerGraphQLSourceKind.ENTITY;
    }

    boolean isMappedSuperclass() {
        return kind == JimmerGraphQLSourceKind.MAPPED_SUPERCLASS;
    }
}
