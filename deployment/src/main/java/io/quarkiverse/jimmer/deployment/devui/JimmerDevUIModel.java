package io.quarkiverse.jimmer.deployment.devui;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.Type;

import io.quarkiverse.jimmer.deployment.repository.RepositoryMetadata;
import io.quarkus.datasource.common.runtime.DataSourceUtil;
import io.quarkus.deployment.util.JandexUtil;

/** Declared model information for Dev UI. Does not load application classes or resolve runtime clients. */
public final class JimmerDevUIModel {

    private static final String SQL = "org.babyfish.jimmer.sql.";
    private static final String REPOSITORIES = "io.quarkiverse.jimmer.runtime.";
    private static final DotName ENTITY = DotName.createSimple(SQL + "Entity");
    private static final DotName DATA_SOURCE = DotName.createSimple("io.quarkus.agroal.DataSource");
    private static final DotName INJECT = DotName.createSimple("jakarta.inject.Inject");
    private static final Set<String> CLIENT_TYPES = Set.of(SQL + "JSqlClient", SQL + "runtime.JSqlClientImplementor",
            SQL + "kt.KSqlClient");
    private static final List<String> ASSOCIATIONS = List.of("ManyToOne", "OneToOne", "OneToMany", "ManyToMany",
            "ManyToManyView");

    private JimmerDevUIModel() {
    }

    /** The application repository map must come from the extension's existing repository analysis. */
    public static Map<String, Object> create(IndexView index, List<RepositoryMetadata> legacyRepositories,
            Map<String, String> repositoryEntities) {
        Map<String, Map<String, Object>> entities = new TreeMap<>();
        for (ClassInfo type : index.getKnownClasses()) {
            if (!type.hasDeclaredAnnotation(ENTITY)) {
                continue;
            }
            Map<String, Map<String, Object>> properties = new TreeMap<>();
            collectProperties(index, type, properties, new HashSet<>());
            String name = type.name().toString();
            Map<String, Object> entity = new LinkedHashMap<>();
            entity.put("name", name);
            entity.put("simpleName", name.substring(Math.max(name.lastIndexOf('.'), name.lastIndexOf('$')) + 1));
            entity.put("superTypes", type.interfaceNames().stream().map(DotName::toString).sorted().toList());
            entity.put("tableName", annotationValue(type.declaredAnnotation(DotName.createSimple(SQL + "Table")), "name"));
            entity.put("microServiceName", annotationValue(type.declaredAnnotation(ENTITY), "microServiceName"));
            entity.put("properties", List.copyOf(properties.values()));
            entities.put(name, entity);
        }

        Map<String, Map<String, Object>> repositories = new TreeMap<>();
        for (RepositoryMetadata metadata : legacyRepositories) {
            String name = metadata.getRepositoryInterface().getName();
            List<Type> javaTypes = repositoryTypes(index, name, REPOSITORIES + "repository.JRepository");
            boolean kotlin = javaTypes.isEmpty();
            List<Type> types = kotlin ? repositoryTypes(index, name, REPOSITORIES + "repository.KRepository") : javaTypes;
            repositories.put(name,
                    repository(name, types.isEmpty() ? "unknown" : kotlin ? "kotlin" : "java", "legacy",
                            metadata.getDomainType().getName(),
                            types, metadata.getDataSourceName(), "repository-metadata"));
        }
        repositoryEntities.forEach((name, entity) -> {
            List<Type> javaTypes = repositoryTypes(index, name, REPOSITORIES + "repo.support.AbstractJavaRepository");
            boolean kotlin = javaTypes.isEmpty();
            List<Type> types = kotlin ? repositoryTypes(index, name, REPOSITORIES + "repo.support.AbstractKotlinRepository")
                    : javaTypes;
            Binding binding = dataSourceBinding(index, index.getClassByName(DotName.createSimple(name)));
            repositories.put(name, repository(name, types.isEmpty() ? "unknown" : kotlin ? "kotlin" : "java", "application",
                    entity, types, binding.dataSource(), binding.source()));
        });
        return Map.of("entities", List.copyOf(entities.values()), "repositories", List.copyOf(repositories.values()));
    }

    private static void collectProperties(IndexView index, ClassInfo type, Map<String, Map<String, Object>> properties,
            Set<DotName> visited) {
        if (!visited.add(type.name())) {
            return;
        }
        for (DotName parentName : type.interfaceNames()) {
            ClassInfo parent = index.getClassByName(parentName);
            if (parent != null) {
                collectProperties(index, parent, properties, visited);
            }
        }
        for (MethodInfo method : type.methods()) {
            if (method.isSynthetic() || method.isBridge() || Modifier.isStatic(method.flags())
                    || method.parametersCount() != 0 || method.returnType().kind() == Type.Kind.VOID
                    || (!method.isAbstract()
                            && !method.hasDeclaredAnnotation(DotName.createSimple("org.babyfish.jimmer.Formula")))) {
                continue;
            }
            String getterName = method.name();
            String name = getterName;
            if (getterName.startsWith("get") && getterName.length() > 3 && Character.isUpperCase(getterName.charAt(3))) {
                name = Character.toLowerCase(getterName.charAt(3)) + getterName.substring(4);
            }
            // isX depends on Kotlin property names or the Java APT keepIsPrefix option, which the index cannot prove.
            String association = ASSOCIATIONS.stream()
                    .filter(annotation -> method.hasDeclaredAnnotation(DotName.createSimple(SQL + annotation)))
                    .findFirst().orElse("");
            Type target = method.returnType();
            if (target.kind() == Type.Kind.PARAMETERIZED_TYPE && target.name().toString().equals("java.util.List")) {
                target = target.asParameterizedType().arguments().get(0);
            }
            if (target.kind() == Type.Kind.WILDCARD_TYPE) {
                target = target.asWildcardType().extendsBound();
            }
            ClassInfo targetClass = target == null ? null : index.getClassByName(target.name());
            String targetType = targetClass != null && (targetClass.hasDeclaredAnnotation(ENTITY)
                    || targetClass.hasDeclaredAnnotation(DotName.createSimple(SQL + "Embeddable")))
                            ? targetClass.name().toString()
                            : "";
            boolean nullable = method.declaredAnnotations().stream().anyMatch(JimmerDevUIModel::isNullable)
                    || method.returnType().annotations().stream().anyMatch(JimmerDevUIModel::isNullable);
            Map<String, Object> property = new LinkedHashMap<>();
            property.put("name", name);
            property.put("getterName", getterName);
            property.put("declaredIn", type.name().toString());
            property.put("type", method.returnType().toString());
            property.put("id", method.hasDeclaredAnnotation(DotName.createSimple(SQL + "Id")));
            property.put("nullable", nullable);
            property.put("association", association);
            property.put("targetType", targetType);
            property.put("transientProperty", method.hasDeclaredAnnotation(DotName.createSimple(SQL + "Transient")));
            property.put("logicalDeleted", method.hasDeclaredAnnotation(DotName.createSimple(SQL + "LogicalDeleted")));
            properties.put(name, property);
        }
    }

    private static boolean isNullable(AnnotationInstance annotation) {
        String name = annotation.name().toString();
        return name.endsWith(".Nullable") || name.endsWith(".Null") || name.equals("org.babyfish.jimmer.client.TNullable");
    }

    private static String annotationValue(AnnotationInstance annotation, String name) {
        return annotation == null || annotation.value(name) == null ? "" : annotation.value(name).asString();
    }

    private static List<Type> repositoryTypes(IndexView index, String name, String base) {
        try {
            return JandexUtil.resolveTypeParameters(DotName.createSimple(name), DotName.createSimple(base), index);
        } catch (IllegalArgumentException ignored) {
            // An incomplete index must not prevent dev mode from starting just to render optional model information.
            return List.of();
        }
    }

    private static Map<String, Object> repository(String name, String kind, String style, String entityType, List<Type> types,
            String dataSource, String bindingSource) {
        return Map.of("name", name, "kind", kind, "style", style, "entityType", entityType,
                "idType", types.size() > 1 ? types.get(1).toString() : "unknown", "dataSource", dataSource,
                "bindingSource", bindingSource);
    }

    private static Binding dataSourceBinding(IndexView index, ClassInfo repository) {
        if (repository == null) {
            return new Binding("unknown", "class-not-indexed");
        }
        boolean beanClass = repository.declaredAnnotations().stream().anyMatch(annotation -> {
            String name = annotation.name().toString();
            if (Set.of("jakarta.inject.Singleton", "jakarta.enterprise.context.ApplicationScoped",
                    "jakarta.enterprise.context.RequestScoped", "jakarta.enterprise.context.SessionScoped",
                    "jakarta.enterprise.context.ConversationScoped", "jakarta.enterprise.context.Dependent")
                    .contains(name)) {
                return true;
            }
            ClassInfo annotationType = index.getClassByName(annotation.name());
            return annotationType != null && (annotationType.hasDeclaredAnnotation(DotName.createSimple("jakarta.inject.Scope"))
                    || annotationType.hasDeclaredAnnotation(DotName.createSimple("jakarta.enterprise.context.NormalScope"))
                    || annotationType.hasDeclaredAnnotation(DotName.createSimple("jakarta.enterprise.inject.Stereotype")));
        });
        if (!beanClass) {
            return new Binding("unknown", "bean-discovery-not-proven");
        }
        List<Binding> bindings = new ArrayList<>();
        List<MethodInfo> constructors = repository.constructors().stream().filter(method -> !method.isSynthetic()).toList();
        for (MethodInfo constructor : constructors) {
            if (constructors.size() == 1 || constructor.hasDeclaredAnnotation(INJECT)) {
                for (var parameter : constructor.parameters()) {
                    if (CLIENT_TYPES.contains(parameter.type().name().toString())) {
                        bindings.add(injectionBinding(index, parameter.declaredAnnotations(), "constructor-parameter"));
                    }
                }
            }
        }
        Set<DotName> visited = new HashSet<>();
        for (ClassInfo type = repository; type != null
                && visited.add(type.name()); type = type.superName() == null ? null : index.getClassByName(type.superName())) {
            for (var field : type.fields()) {
                if (field.hasDeclaredAnnotation(INJECT) && CLIENT_TYPES.contains(field.type().name().toString())) {
                    bindings.add(injectionBinding(index, field.declaredAnnotations(), "injected-field"));
                }
            }
            for (MethodInfo method : type.methods()) {
                if (!method.name().equals("<init>") && method.hasDeclaredAnnotation(INJECT)) {
                    for (var parameter : method.parameters()) {
                        if (CLIENT_TYPES.contains(parameter.type().name().toString())) {
                            bindings.add(injectionBinding(index, parameter.declaredAnnotations(), "initializer-parameter"));
                        }
                    }
                }
            }
        }
        if (bindings.isEmpty()) {
            return new Binding("unknown", "no-direct-client-injection");
        }
        if (bindings.stream().anyMatch(binding -> binding.dataSource().equals("unknown"))
                || bindings.stream().map(Binding::dataSource).distinct().count() > 1) {
            return new Binding("unknown", "ambiguous-client-injection");
        }
        return new Binding(bindings.get(0).dataSource(),
                bindings.stream().map(Binding::source).distinct().sorted().reduce((a, b) -> a + ", " + b).orElseThrow());
    }

    private static Binding injectionBinding(IndexView index, Collection<AnnotationInstance> annotations, String source) {
        String dataSource = DataSourceUtil.DEFAULT_DATASOURCE_NAME;
        for (AnnotationInstance annotation : annotations) {
            if (annotation.name().equals(DATA_SOURCE)) {
                dataSource = annotationValue(annotation, "value");
            } else {
                String name = annotation.name().toString();
                if (name.equals("jakarta.inject.Inject") || name.equals("jakarta.enterprise.inject.Default")
                        || isNullable(annotation) || name.endsWith(".NotNull") || name.endsWith(".NonNull")) {
                    continue;
                }
                if (name.equals("jakarta.enterprise.inject.Any") || name.equals("jakarta.inject.Named")) {
                    return new Binding("unknown", source + ":custom-qualifier");
                }
                ClassInfo annotationType = index.getClassByName(annotation.name());
                if (annotationType == null
                        || annotationType.hasDeclaredAnnotation(DotName.createSimple("jakarta.inject.Qualifier"))) {
                    return new Binding("unknown", source + ":custom-qualifier");
                }
            }
        }
        return new Binding(dataSource, source);
    }

    private record Binding(String dataSource, String source) {
    }
}
