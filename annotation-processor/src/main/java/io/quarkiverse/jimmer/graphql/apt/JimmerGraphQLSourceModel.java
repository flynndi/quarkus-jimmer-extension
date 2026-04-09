package io.quarkiverse.jimmer.graphql.apt;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

final class JimmerGraphQLSourceModel {

    private final Map<String, JimmerGraphQLSourceType> typesByQualifiedName;

    JimmerGraphQLSourceModel(List<JimmerGraphQLSourceType> types) {
        this.typesByQualifiedName = new LinkedHashMap<>();
        for (JimmerGraphQLSourceType type : types) {
            JimmerGraphQLSourceType previous = typesByQualifiedName.putIfAbsent(type.qualifiedName(), type);
            if (previous != null && !previous.equals(type)) {
                throw new IllegalStateException("Duplicate GraphQL source type: " + type.qualifiedName());
            }
        }
    }

    Collection<JimmerGraphQLSourceType> entities() {
        List<JimmerGraphQLSourceType> entities = new ArrayList<>();
        for (JimmerGraphQLSourceType type : typesByQualifiedName.values()) {
            if (type.isEntity()) {
                entities.add(type);
            }
        }
        return entities;
    }

    JimmerGraphQLSourceType type(String qualifiedName) {
        return typesByQualifiedName.get(qualifiedName);
    }

    boolean isEntityType(String qualifiedName) {
        JimmerGraphQLSourceType type = typesByQualifiedName.get(qualifiedName);
        return type != null && type.isEntity();
    }

    List<JimmerGraphQLSourceMethod> scalarMethods(JimmerGraphQLSourceType type) {
        LinkedHashMap<String, JimmerGraphQLSourceMethod> methods = new LinkedHashMap<>();
        collectMethods(type, methods, false);
        return new ArrayList<>(methods.values());
    }

    List<JimmerGraphQLSourceMethod> complexMethods(JimmerGraphQLSourceType type) {
        LinkedHashMap<String, JimmerGraphQLSourceMethod> methods = new LinkedHashMap<>();
        collectMethods(type, methods, true);
        return new ArrayList<>(methods.values());
    }

    private void collectMethods(
            JimmerGraphQLSourceType type,
            LinkedHashMap<String, JimmerGraphQLSourceMethod> methods,
            boolean complex) {
        for (String parentName : type.extendsTypes()) {
            JimmerGraphQLSourceType parent = typesByQualifiedName.get(parentName);
            if (parent != null && (parent.isEntity() || parent.isMappedSuperclass())) {
                collectMethods(parent, methods, complex);
            }
        }
        for (JimmerGraphQLSourceMethod method : type.methods()) {
            if (method.complex() == complex) {
                methods.put(method.name(), method);
            }
        }
    }

    String facadeClassName(String qualifiedName) {
        JimmerGraphQLSourceType type = type(qualifiedName);
        if (type == null) {
            throw new IllegalArgumentException("Illegal GraphQL source type: " + qualifiedName);
        }
        return type.simpleName() + "Gql";
    }

    String facadeQualifiedName(String qualifiedName) {
        return JimmerGraphQLSourceGenerator.MODEL_PACKAGE + '.' + facadeClassName(qualifiedName);
    }

    List<String> entityQualifiedNames() {
        LinkedHashSet<String> entityNames = new LinkedHashSet<>();
        for (JimmerGraphQLSourceType entity : entities()) {
            entityNames.add(entity.qualifiedName());
        }
        return new ArrayList<>(entityNames);
    }
}
