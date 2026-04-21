package io.quarkiverse.jimmer.graphql.apt;

import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ArrayTypeName;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeVariableName;
import com.squareup.javapoet.WildcardTypeName;

final class JimmerGraphQLSourceGenerator {

    private static final ClassName TYPE_ANNOTATION = ClassName.get("org.eclipse.microprofile.graphql", "Type");
    private static final ClassName GRAPHQL_API = ClassName.get("org.eclipse.microprofile.graphql", "GraphQLApi");
    private static final ClassName GRAPHQL_NAME = ClassName.get("org.eclipse.microprofile.graphql", "Name");
    private static final ClassName GRAPHQL_SOURCE = ClassName.get("org.eclipse.microprofile.graphql", "Source");
    private static final ClassName INJECT = ClassName.get("jakarta.inject", "Inject");
    private static final ClassName SINGLETON = ClassName.get("jakarta.inject", "Singleton");
    private static final ClassName UNREMOVABLE = ClassName.get("io.quarkus.arc", "Unremovable");
    private static final ClassName CONTEXT = ClassName.get("io.smallrye.graphql.api", "Context");
    private static final ClassName DATA_FETCHING_ENVIRONMENT = ClassName.get("graphql.schema", "DataFetchingEnvironment");
    private static final ClassName OBJECT = ClassName.get(Object.class);
    private static final ClassName CLASS = ClassName.get(Class.class);
    private static final ClassName LIST = ClassName.get(List.class);
    private static final ClassName ILLEGAL_ARGUMENT_EXCEPTION = ClassName.get(IllegalArgumentException.class);
    private static final ClassName JIMMER_GRAPHQL_FACADE = ClassName.get(
            "io.quarkiverse.jimmer.runtime.graphql.facade",
            "JimmerGraphQLFacade");
    private static final ClassName JIMMER_GRAPHQL_FACADE_SUPPORT = ClassName.get(
            "io.quarkiverse.jimmer.runtime.graphql.facade",
            "JimmerGraphQLFacadeSupport");
    private static final ClassName JIMMER_GRAPHQL_GENERATED_FACADE_REGISTRY = ClassName.get(
            "io.quarkiverse.jimmer.runtime.graphql.facade",
            "JimmerGraphQLGeneratedFacadeRegistry");

    private final JimmerGraphQLSourceModel model;

    JimmerGraphQLSourceGenerator(JimmerGraphQLSourceModel model) {
        this.model = model;
    }

    Map<String, String> generate() {
        Map<String, String> sources = new LinkedHashMap<>();
        boolean wroteAny = false;
        for (JimmerGraphQLSourceType entity : model.entities()) {
            ClassName facadeType = facadeTypeName(entity.qualifiedName());
            sources.put(facadeType.reflectionName(),
                    javaFile(facadeType.packageName(), facadeSpec(entity, facadeType)).toString());
            wroteAny = true;
            List<JimmerGraphQLSourceMethod> complexMethods = model.complexMethods(entity);
            if (!complexMethods.isEmpty()) {
                ClassName resolverType = resolverTypeName(entity.qualifiedName());
                sources.put(resolverType.reflectionName(),
                        javaFile(resolverType.packageName(), resolverSpec(entity, facadeType, complexMethods)).toString());
            }
        }
        if (wroteAny) {
            for (Map.Entry<String, List<String>> entry : model.entityQualifiedNamesByGraphqlPackage().entrySet()) {
                String packageName = entry.getKey();
                ClassName registryType = ClassName.get(packageName, "JimmerGraphQLFacadeRegistry");
                sources.put(registryType.reflectionName(), javaFile(packageName, registrySpec(entry.getValue())).toString());
            }
        }
        return sources;
    }

    private TypeSpec facadeSpec(JimmerGraphQLSourceType entity, ClassName facadeType) {
        TypeName rawType = typeName(entity.qualifiedName());
        FieldSpec rawField = FieldSpec.builder(rawType, "raw", PRIVATE, FINAL).build();

        TypeSpec.Builder builder = TypeSpec.classBuilder(facadeType)
                .addModifiers(PUBLIC, FINAL)
                .addAnnotation(AnnotationSpec.builder(TYPE_ANNOTATION)
                        .addMember("value", "$S", entity.simpleName())
                        .build())
                .addSuperinterface(ParameterizedTypeName.get(JIMMER_GRAPHQL_FACADE, rawType))
                .addField(rawField)
                .addMethod(MethodSpec.constructorBuilder()
                        .addModifiers(PUBLIC)
                        .addParameter(rawType, "raw")
                        .addStatement("this.raw = raw")
                        .build());

        for (JimmerGraphQLSourceMethod method : model.scalarMethods(entity)) {
            MethodSpec.Builder getterBuilder = MethodSpec.methodBuilder(getterName(method.name()))
                    .addModifiers(PUBLIC)
                    .returns(typeName(method.returnType()));
            if (!method.graphQLName().equals(method.name())) {
                getterBuilder.addAnnotation(AnnotationSpec.builder(GRAPHQL_NAME)
                        .addMember("value", "$S", method.graphQLName())
                        .build());
            }
            for (AnnotationSpec annotation : method.annotations()) {
                getterBuilder.addAnnotation(annotation);
            }
            builder.addMethod(getterBuilder
                    .addStatement("return raw.$L()", method.rawAccessorName())
                    .build());
        }

        builder.addMethod(MethodSpec.methodBuilder("__raw")
                .addAnnotation(Override.class)
                .addModifiers(PUBLIC)
                .returns(rawType)
                .addStatement("return raw")
                .build());

        return builder.build();
    }

    private TypeSpec resolverSpec(
            JimmerGraphQLSourceType entity,
            ClassName facadeType,
            List<JimmerGraphQLSourceMethod> complexMethods) {
        TypeSpec.Builder builder = TypeSpec.classBuilder(entity.simpleName() + "GqlSourceResolver")
                .addModifiers(PUBLIC, FINAL)
                .addAnnotation(GRAPHQL_API)
                .addField(FieldSpec.builder(JIMMER_GRAPHQL_FACADE_SUPPORT, "support")
                        .addAnnotation(INJECT)
                        .build());

        for (JimmerGraphQLSourceMethod method : complexMethods) {
            builder.addMethod(batchResolverMethod(facadeType, method));
        }
        return builder.build();
    }

    private MethodSpec batchResolverMethod(ClassName facadeType, JimmerGraphQLSourceMethod method) {
        TypeName sourcesType = ParameterizedTypeName.get(LIST, facadeType);
        ParameterSpec sources = ParameterSpec.builder(sourcesType, "sources")
                .addAnnotation(AnnotationSpec.builder(GRAPHQL_SOURCE)
                        .addMember("name", "$S", method.graphQLName())
                        .build())
                .build();

        MethodSpec.Builder builder = MethodSpec.methodBuilder(method.name())
                .addModifiers(PUBLIC)
                .addAnnotation(AnnotationSpec.builder(GRAPHQL_NAME)
                        .addMember("value", "$S", method.graphQLName())
                        .build())
                .returns(batchResolverReturnType(method))
                .addParameter(sources)
                .addParameter(CONTEXT, "context")
                .addStatement("$T env = context.unwrap($T.class)", DATA_FETCHING_ENVIRONMENT, DATA_FETCHING_ENVIRONMENT);
        for (AnnotationSpec annotation : method.annotations()) {
            builder.addAnnotation(annotation);
        }

        if (method.collection() && model.isEntityType(method.elementType())) {
            builder.addStatement(
                    "return support.loadFacadeListBatch(sources, $S, env, $T.class)",
                    method.name(),
                    facadeTypeName(method.elementType()));
        } else if (model.isEntityType(method.elementType())) {
            builder.addStatement(
                    "return support.loadFacadeBatch(sources, $S, env, $T.class)",
                    method.name(),
                    facadeTypeName(method.elementType()));
        } else {
            builder.addStatement("return support.loadValueBatch(sources, $S, env)", method.name());
        }
        return builder.build();
    }

    private TypeSpec registrySpec(List<String> entityNames) {
        TypeVariableName typeVariable = TypeVariableName.get("T");
        TypeSpec.Builder builder = TypeSpec.classBuilder("JimmerGraphQLFacadeRegistry")
                .addModifiers(PUBLIC, FINAL)
                .addAnnotation(SINGLETON)
                .addAnnotation(UNREMOVABLE)
                .addSuperinterface(JIMMER_GRAPHQL_GENERATED_FACADE_REGISTRY)
                .addMethod(supportsFacadeTypeMethod(entityNames))
                .addMethod(supportsRawMethod(entityNames))
                .addMethod(wrapTypedMethod(typeVariable, entityNames))
                .addMethod(wrapUntypedMethod(entityNames));

        return builder.build();
    }

    private MethodSpec supportsFacadeTypeMethod(List<String> entityNames) {
        MethodSpec.Builder builder = MethodSpec.methodBuilder("supportsFacadeType")
                .addAnnotation(Override.class)
                .addModifiers(PUBLIC)
                .returns(TypeName.BOOLEAN)
                .addParameter(ParameterizedTypeName.get(CLASS, WildcardTypeName.subtypeOf(OBJECT)), "facadeType");
        for (String entityName : entityNames) {
            builder.beginControlFlow("if (facadeType == $T.class)", facadeTypeName(entityName))
                    .addStatement("return true")
                    .endControlFlow();
        }
        builder.addStatement("return false");
        return builder.build();
    }

    private MethodSpec supportsRawMethod(List<String> entityNames) {
        MethodSpec.Builder builder = MethodSpec.methodBuilder("supportsRaw")
                .addAnnotation(Override.class)
                .addModifiers(PUBLIC)
                .returns(TypeName.BOOLEAN)
                .addParameter(OBJECT, "raw")
                .beginControlFlow("if (raw == null || raw instanceof $T<?>)", JIMMER_GRAPHQL_FACADE)
                .addStatement("return false")
                .endControlFlow();
        for (String entityName : entityNames) {
            builder.beginControlFlow("if (raw instanceof $T)", className(entityName))
                    .addStatement("return true")
                    .endControlFlow();
        }
        builder.addStatement("return false");
        return builder.build();
    }

    private MethodSpec wrapTypedMethod(TypeVariableName typeVariable, List<String> entityNames) {
        MethodSpec.Builder builder = MethodSpec.methodBuilder("wrap")
                .addAnnotation(Override.class)
                .addModifiers(PUBLIC)
                .addTypeVariable(typeVariable)
                .returns(typeVariable)
                .addParameter(OBJECT, "raw")
                .addParameter(ParameterizedTypeName.get(CLASS, typeVariable), "facadeType")
                .beginControlFlow("if (raw == null)")
                .addStatement("return null")
                .endControlFlow()
                .beginControlFlow("if (facadeType.isInstance(raw))")
                .addStatement("return facadeType.cast(raw)")
                .endControlFlow();

        for (String entityName : entityNames) {
            ClassName facadeType = facadeTypeName(entityName);
            builder.beginControlFlow("if (facadeType == $T.class)", facadeType)
                    .beginControlFlow("if (!(raw instanceof $T value))", typeName(entityName))
                    .addStatement("throw new $T($S + raw.getClass().getName() + $S + facadeType.getName())",
                            ILLEGAL_ARGUMENT_EXCEPTION,
                            "Raw value type does not match GraphQL facade registry mapping: ",
                            " -> ")
                    .endControlFlow()
                    .addStatement("return facadeType.cast(new $T(value))", facadeType)
                    .endControlFlow();
        }

        builder.addStatement("throw new $T($S + facadeType.getName())",
                ILLEGAL_ARGUMENT_EXCEPTION,
                "Unsupported GraphQL facade type: ");
        return builder.build();
    }

    private MethodSpec wrapUntypedMethod(List<String> entityNames) {
        MethodSpec.Builder builder = MethodSpec.methodBuilder("wrap")
                .addAnnotation(Override.class)
                .addModifiers(PUBLIC)
                .returns(OBJECT)
                .addParameter(OBJECT, "raw")
                .beginControlFlow("if (raw == null || raw instanceof $T<?>)", JIMMER_GRAPHQL_FACADE)
                .addStatement("return raw")
                .endControlFlow();

        for (String entityName : entityNames) {
            ClassName entityType = className(entityName);
            ClassName facadeType = facadeTypeName(entityName);
            builder.beginControlFlow("if (raw instanceof $T value)", entityType)
                    .addStatement("return new $T(value)", facadeType)
                    .endControlFlow();
        }

        builder.addStatement("return raw");
        return builder.build();
    }

    private TypeName batchResolverReturnType(JimmerGraphQLSourceMethod method) {
        return ParameterizedTypeName.get(LIST, batchResolverElementType(method));
    }

    private TypeName batchResolverElementType(JimmerGraphQLSourceMethod method) {
        if (method.collection() && model.isEntityType(method.elementType())) {
            return ParameterizedTypeName.get(LIST, facadeTypeName(method.elementType()));
        }
        if (model.isEntityType(method.elementType())) {
            return facadeTypeName(method.elementType());
        }
        return typeName(method.returnType());
    }

    private static JavaFile javaFile(String packageName, TypeSpec typeSpec) {
        return JavaFile.builder(packageName, typeSpec)
                .skipJavaLangImports(true)
                .build();
    }

    private ClassName facadeTypeName(String qualifiedEntityName) {
        return ClassName.get(model.graphqlPackageName(qualifiedEntityName), model.facadeClassName(qualifiedEntityName));
    }

    private ClassName resolverTypeName(String qualifiedEntityName) {
        return ClassName.get(model.graphqlPackageName(qualifiedEntityName),
                model.type(qualifiedEntityName).simpleName() + "GqlSourceResolver");
    }

    private static TypeName typeName(String text) {
        return new TypeNameParser(text).parse();
    }

    private static ClassName className(String text) {
        TypeName typeName = typeName(text);
        if (!(typeName instanceof ClassName className)) {
            throw new IllegalArgumentException("Expected declared type but got: " + text);
        }
        return className;
    }

    private static String getterName(String propertyName) {
        return "get" + Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1);
    }

    private static final class TypeNameParser {

        private final String source;

        private int index;

        private TypeNameParser(String source) {
            this.source = source;
        }

        private TypeName parse() {
            TypeName typeName = parseType();
            skipWhitespace();
            if (index != source.length()) {
                throw new IllegalArgumentException("Cannot parse type: " + source);
            }
            return typeName;
        }

        private TypeName parseType() {
            skipWhitespace();
            String token = parseToken();
            TypeName typeName = primitiveType(token);
            if (typeName == null) {
                typeName = ClassName.bestGuess(token);
            }
            skipWhitespace();
            if (peek('<')) {
                if (!(typeName instanceof ClassName className)) {
                    throw new IllegalArgumentException("Parameterized type must start with a class: " + source);
                }
                consume('<');
                List<TypeName> arguments = new ArrayList<>();
                do {
                    arguments.add(parseType());
                    skipWhitespace();
                    if (peek(',')) {
                        consume(',');
                    } else {
                        break;
                    }
                } while (true);
                consume('>');
                typeName = ParameterizedTypeName.get(className, arguments.toArray(TypeName[]::new));
            }
            while (matchArraySuffix()) {
                typeName = ArrayTypeName.of(typeName);
            }
            return typeName;
        }

        private String parseToken() {
            skipWhitespace();
            int start = index;
            while (index < source.length()) {
                char ch = source.charAt(index);
                if (Character.isJavaIdentifierPart(ch) || ch == '.' || ch == '$') {
                    index++;
                    continue;
                }
                break;
            }
            if (start == index) {
                throw new IllegalArgumentException("Cannot parse type token from: " + source);
            }
            return source.substring(start, index);
        }

        private boolean matchArraySuffix() {
            skipWhitespace();
            if (index + 1 < source.length() && source.charAt(index) == '[' && source.charAt(index + 1) == ']') {
                index += 2;
                return true;
            }
            return false;
        }

        private boolean peek(char ch) {
            skipWhitespace();
            return index < source.length() && source.charAt(index) == ch;
        }

        private void consume(char ch) {
            skipWhitespace();
            if (index >= source.length() || source.charAt(index) != ch) {
                throw new IllegalArgumentException("Expected '" + ch + "' in type: " + source);
            }
            index++;
        }

        private void skipWhitespace() {
            while (index < source.length() && Character.isWhitespace(source.charAt(index))) {
                index++;
            }
        }

        private static TypeName primitiveType(String name) {
            return switch (name) {
                case "boolean" -> TypeName.BOOLEAN;
                case "byte" -> TypeName.BYTE;
                case "short" -> TypeName.SHORT;
                case "int" -> TypeName.INT;
                case "long" -> TypeName.LONG;
                case "char" -> TypeName.CHAR;
                case "float" -> TypeName.FLOAT;
                case "double" -> TypeName.DOUBLE;
                case "void" -> TypeName.VOID;
                default -> null;
            };
        }
    }
}
