package io.quarkiverse.jimmer.graphql.apt;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class JimmerGraphQLSourceGenerator {

    static final String ROOT_PACKAGE = "io.quarkiverse.jimmer.generated.graphql";

    static final String MODEL_PACKAGE = ROOT_PACKAGE + ".model";

    static final String RESOLVER_PACKAGE = ROOT_PACKAGE + ".resolver";

    static final String REGISTRY_PACKAGE = ROOT_PACKAGE + ".registry";

    private final JimmerGraphQLSourceModel model;

    JimmerGraphQLSourceGenerator(JimmerGraphQLSourceModel model) {
        this.model = model;
    }

    Map<String, String> generate() {
        Map<String, String> sources = new LinkedHashMap<>();
        boolean wroteAny = false;
        for (JimmerGraphQLSourceType entity : model.entities()) {
            String facadeType = model.facadeClassName(entity.qualifiedName());
            sources.put(MODEL_PACKAGE + '.' + facadeType, facadeSource(entity));
            wroteAny = true;
            List<JimmerGraphQLSourceMethod> complexMethods = model.complexMethods(entity);
            if (!complexMethods.isEmpty()) {
                sources.put(RESOLVER_PACKAGE + '.' + entity.simpleName() + "GqlSourceResolver",
                        resolverSource(entity, complexMethods));
            }
        }
        if (wroteAny) {
            sources.put(REGISTRY_PACKAGE + ".JimmerGraphQLFacadeRegistry", registrySource());
        }
        return sources;
    }

    private String facadeSource(JimmerGraphQLSourceType entity) {
        String rawType = entity.qualifiedName();
        String facadeType = model.facadeClassName(entity.qualifiedName());
        StringBuilder builder = new StringBuilder();
        builder.append("package ").append(MODEL_PACKAGE).append(";\n\n");
        builder.append("import org.eclipse.microprofile.graphql.Type;\n");
        builder.append("import io.quarkiverse.jimmer.runtime.graphql.facade.JimmerGraphQLFacade;\n\n");
        builder.append("@Type(\"").append(entity.simpleName()).append("\")\n");
        builder.append("public final class ").append(facadeType)
                .append(" implements JimmerGraphQLFacade<").append(rawType).append("> {\n\n");
        builder.append("    private final ").append(rawType).append(" raw;\n\n");
        builder.append("    public ").append(facadeType).append('(').append(rawType).append(" raw) {\n");
        builder.append("        this.raw = raw;\n");
        builder.append("    }\n\n");
        for (JimmerGraphQLSourceMethod method : model.scalarMethods(entity)) {
            builder.append("    public ").append(method.returnType()).append(' ')
                    .append(getterName(method.name())).append("() {\n");
            builder.append("        return raw.").append(method.rawAccessorName()).append("();\n");
            builder.append("    }\n\n");
        }
        builder.append("    @Override\n");
        builder.append("    public ").append(rawType).append(" __raw() {\n");
        builder.append("        return raw;\n");
        builder.append("    }\n");
        builder.append("}\n");
        return builder.toString();
    }

    private String resolverSource(JimmerGraphQLSourceType entity, List<JimmerGraphQLSourceMethod> complexMethods) {
        String facadeType = MODEL_PACKAGE + '.' + model.facadeClassName(entity.qualifiedName());
        StringBuilder builder = new StringBuilder();
        builder.append("package ").append(RESOLVER_PACKAGE).append(";\n\n");
        builder.append("import jakarta.inject.Inject;\n");
        builder.append("import org.eclipse.microprofile.graphql.GraphQLApi;\n");
        builder.append("import org.eclipse.microprofile.graphql.Name;\n");
        builder.append("import org.eclipse.microprofile.graphql.Source;\n");
        builder.append("import graphql.schema.DataFetchingEnvironment;\n");
        builder.append("import io.smallrye.graphql.api.Context;\n");
        builder.append("import io.quarkiverse.jimmer.runtime.graphql.facade.JimmerGraphQLFacadeSupport;\n\n");
        builder.append("@GraphQLApi\n");
        builder.append("public final class ").append(entity.simpleName()).append("GqlSourceResolver {\n\n");
        builder.append("    @Inject\n");
        builder.append("    JimmerGraphQLFacadeSupport support;\n\n");
        for (JimmerGraphQLSourceMethod method : complexMethods) {
            builder.append("    @Name(\"").append(method.name()).append("\")\n");
            builder.append("    public ").append(batchResolverReturnType(method)).append(' ')
                    .append(method.name()).append("(@Source(name = \"").append(method.name()).append("\") java.util.List<")
                    .append(facadeType).append("> sources, Context context) {\n");
            builder.append("        DataFetchingEnvironment env = context.unwrap(DataFetchingEnvironment.class);\n");
            if (method.collection() && model.isEntityType(method.elementType())) {
                builder.append("        return support.loadFacadeListBatch(sources, \"").append(method.name())
                        .append("\", env, ").append(model.facadeQualifiedName(method.elementType())).append(".class);\n");
            } else if (model.isEntityType(method.elementType())) {
                builder.append("        return support.loadFacadeBatch(sources, \"").append(method.name())
                        .append("\", env, ").append(model.facadeQualifiedName(method.elementType())).append(".class);\n");
            } else {
                builder.append("        return support.loadValueBatch(sources, \"").append(method.name())
                        .append("\", env);\n");
            }
            builder.append("    }\n\n");
        }
        builder.append("}\n");
        return builder.toString();
    }

    private String registrySource() {
        StringBuilder builder = new StringBuilder();
        builder.append("package ").append(REGISTRY_PACKAGE).append(";\n\n");
        builder.append("import jakarta.inject.Singleton;\n");
        builder.append("import io.quarkus.arc.Unremovable;\n");
        builder.append("import io.quarkiverse.jimmer.runtime.graphql.facade.JimmerGraphQLFacade;\n");
        builder.append("import io.quarkiverse.jimmer.runtime.graphql.facade.JimmerGraphQLGeneratedFacadeRegistry;\n");
        builder.append("import ").append(MODEL_PACKAGE).append(".*;\n\n");
        builder.append("@Singleton\n");
        builder.append("@Unremovable\n");
        builder.append("public final class JimmerGraphQLFacadeRegistry implements JimmerGraphQLGeneratedFacadeRegistry {\n\n");
        builder.append("    @Override\n");
        builder.append("    public <T> T wrap(Object raw, Class<T> facadeType) {\n");
        builder.append("        if (raw == null) {\n");
        builder.append("            return null;\n");
        builder.append("        }\n");
        builder.append("        if (facadeType.isInstance(raw)) {\n");
        builder.append("            return facadeType.cast(raw);\n");
        builder.append("        }\n");
        for (String entityName : model.entityQualifiedNames()) {
            builder.append("        if (facadeType == ").append(model.facadeQualifiedName(entityName)).append(".class) {\n");
            builder.append("            return facadeType.cast(new ").append(model.facadeQualifiedName(entityName))
                    .append("((").append(entityName).append(") raw));\n");
            builder.append("        }\n");
        }
        builder.append(
                "        throw new IllegalArgumentException(\"Unsupported GraphQL facade type: \" + facadeType.getName());\n");
        builder.append("    }\n\n");
        builder.append("    @Override\n");
        builder.append("    public Object wrap(Object raw) {\n");
        builder.append("        if (raw == null || raw instanceof JimmerGraphQLFacade<?>) {\n");
        builder.append("            return raw;\n");
        builder.append("        }\n");
        for (String entityName : model.entityQualifiedNames()) {
            builder.append("        if (raw instanceof ").append(entityName).append(" value) {\n");
            builder.append("            return new ").append(model.facadeQualifiedName(entityName)).append("(value);\n");
            builder.append("        }\n");
        }
        builder.append("        return raw;\n");
        builder.append("    }\n");
        builder.append("}\n");
        return builder.toString();
    }

    private String batchResolverReturnType(JimmerGraphQLSourceMethod method) {
        return "java.util.List<" + batchResolverElementType(method) + '>';
    }

    private String batchResolverElementType(JimmerGraphQLSourceMethod method) {
        if (method.collection() && model.isEntityType(method.elementType())) {
            return "java.util.List<" + model.facadeQualifiedName(method.elementType()) + '>';
        }
        if (model.isEntityType(method.elementType())) {
            return model.facadeQualifiedName(method.elementType());
        }
        return method.returnType();
    }

    private static String getterName(String propertyName) {
        return "get" + Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1);
    }
}
