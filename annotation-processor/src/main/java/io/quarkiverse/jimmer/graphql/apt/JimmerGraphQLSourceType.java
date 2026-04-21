package io.quarkiverse.jimmer.graphql.apt;

import java.util.List;

record JimmerGraphQLSourceType(
        String packageName,
        String simpleName,
        String qualifiedName,
        JimmerGraphQLSourceKind kind,
        List<String> extendsTypes,
        List<JimmerGraphQLSourceMethod> methods) {

    boolean isEntity() {
        return kind == JimmerGraphQLSourceKind.ENTITY;
    }

    boolean isMappedSuperclass() {
        return kind == JimmerGraphQLSourceKind.MAPPED_SUPERCLASS;
    }
}
