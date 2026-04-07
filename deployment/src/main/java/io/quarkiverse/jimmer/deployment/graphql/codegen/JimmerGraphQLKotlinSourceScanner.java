package io.quarkiverse.jimmer.deployment.graphql.codegen;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.jetbrains.kotlin.cli.common.messages.MessageCollector;
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles;
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment;
import org.jetbrains.kotlin.com.intellij.openapi.Disposable;
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer;
import org.jetbrains.kotlin.config.CommonConfigurationKeys;
import org.jetbrains.kotlin.config.CompilerConfiguration;
import org.jetbrains.kotlin.config.JVMConfigurationKeys;
import org.jetbrains.kotlin.config.JvmTarget;
import org.jetbrains.kotlin.psi.KtAnnotated;
import org.jetbrains.kotlin.psi.KtAnnotationEntry;
import org.jetbrains.kotlin.psi.KtClass;
import org.jetbrains.kotlin.psi.KtDeclaration;
import org.jetbrains.kotlin.psi.KtFile;
import org.jetbrains.kotlin.psi.KtNamedFunction;
import org.jetbrains.kotlin.psi.KtNullableType;
import org.jetbrains.kotlin.psi.KtProperty;
import org.jetbrains.kotlin.psi.KtTypeElement;
import org.jetbrains.kotlin.psi.KtTypeProjection;
import org.jetbrains.kotlin.psi.KtTypeReference;
import org.jetbrains.kotlin.psi.KtUserType;

import io.quarkus.bootstrap.prebuild.CodeGenException;

final class JimmerGraphQLKotlinSourceScanner {

    List<JimmerGraphQLSourceType> scan(Path sourceDir) throws CodeGenException {
        List<Path> sourceFiles = kotlinSourceFiles(sourceDir);
        if (sourceFiles.isEmpty()) {
            return List.of();
        }
        Disposable disposable = Disposer.newDisposable("jimmer-graphql-kotlin-source-scanner");
        try {
            CompilerConfiguration configuration = new CompilerConfiguration();
            configuration.put(CommonConfigurationKeys.MODULE_NAME, "jimmer-graphql-codegen");
            configuration.put(CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY, MessageCollector.Companion.getNONE());
            configuration.put(JVMConfigurationKeys.NO_JDK, Boolean.TRUE);
            configuration.put(JVMConfigurationKeys.JVM_TARGET, JvmTarget.JVM_17);

            KotlinCoreEnvironment environment = KotlinCoreEnvironment.createForProduction(
                    disposable,
                    configuration,
                    EnvironmentConfigFiles.JVM_CONFIG_FILES);
            environment.addKotlinSourceRoots(sourceFiles.stream().map(Path::toFile).toList());
            return scan(environment.getSourceFiles());
        } catch (RuntimeException ex) {
            throw new CodeGenException("Cannot scan Kotlin source directory: " + sourceDir, ex);
        } finally {
            Disposer.dispose(disposable);
        }
    }

    private List<JimmerGraphQLSourceType> scan(List<KtFile> files) {
        if (files.isEmpty()) {
            return List.of();
        }
        Map<String, String> qualifiedNamesBySimpleName = new LinkedHashMap<>();
        Map<String, JimmerGraphQLSourceKind> kindsByQualifiedName = new LinkedHashMap<>();
        for (KtFile file : files) {
            String packageName = file.getPackageFqName().asString();
            for (KtDeclaration declaration : file.getDeclarations()) {
                if (!(declaration instanceof KtClass type) || !type.isTopLevel()) {
                    continue;
                }
                String simpleName = type.getName();
                if (simpleName == null || simpleName.isBlank()) {
                    continue;
                }
                String qualifiedName = JimmerGraphQLSourceScanner.qualify(packageName, simpleName);
                qualifiedNamesBySimpleName.putIfAbsent(simpleName, qualifiedName);
                if (type.isEnum()) {
                    kindsByQualifiedName.put(qualifiedName, JimmerGraphQLSourceKind.ENUM);
                } else if (type.isInterface()) {
                    kindsByQualifiedName.put(qualifiedName, sourceKind(type));
                }
            }
        }
        List<JimmerGraphQLSourceType> scannedTypes = new ArrayList<>();
        for (KtFile file : files) {
            String packageName = file.getPackageFqName().asString();
            List<String> imports = file.getImportDirectives().stream()
                    .filter(importDirective -> !importDirective.isAllUnder() && importDirective.getImportedFqName() != null)
                    .map(importDirective -> importDirective.getImportedFqName().asString())
                    .toList();
            for (KtDeclaration declaration : file.getDeclarations()) {
                if (!(declaration instanceof KtClass type) || !type.isTopLevel()) {
                    continue;
                }
                String simpleName = type.getName();
                if (simpleName == null || simpleName.isBlank()) {
                    continue;
                }
                String qualifiedName = JimmerGraphQLSourceScanner.qualify(packageName, simpleName);
                if (type.isEnum()) {
                    scannedTypes.add(new JimmerGraphQLSourceType(
                            packageName,
                            simpleName,
                            qualifiedName,
                            JimmerGraphQLSourceKind.ENUM,
                            imports,
                            List.of(),
                            List.of()));
                    continue;
                }
                if (!type.isInterface()) {
                    continue;
                }
                JimmerGraphQLSourceKind kind = sourceKind(type);
                if (kind == JimmerGraphQLSourceKind.OTHER) {
                    continue;
                }
                List<String> extendsTypes = type.getSuperTypeListEntries().stream()
                        .map(entry -> resolveQualifiedType(entry.getTypeReference(), packageName, imports,
                                qualifiedNamesBySimpleName, kindsByQualifiedName))
                        .toList();
                List<JimmerGraphQLSourceMethod> methods = new ArrayList<>();
                for (KtDeclaration member : type.getDeclarations()) {
                    if (member instanceof KtProperty property) {
                        addProperty(methods, property, packageName, imports, qualifiedNamesBySimpleName, kindsByQualifiedName);
                    } else if (member instanceof KtNamedFunction function) {
                        addFunction(methods, function, packageName, imports, qualifiedNamesBySimpleName, kindsByQualifiedName);
                    }
                }
                scannedTypes.add(new JimmerGraphQLSourceType(
                        packageName,
                        simpleName,
                        qualifiedName,
                        kind,
                        imports,
                        extendsTypes,
                        methods));
            }
        }
        return scannedTypes;
    }

    private void addProperty(
            List<JimmerGraphQLSourceMethod> methods,
            KtProperty property,
            String packageName,
            List<String> imports,
            Map<String, String> qualifiedNamesBySimpleName,
            Map<String, JimmerGraphQLSourceKind> kindsByQualifiedName) {
        if (!isPropertyCandidate(property)) {
            return;
        }
        String name = property.getName();
        if (name == null || name.isBlank()) {
            return;
        }
        Set<String> annotations = annotationNames(property);
        String returnType = resolveQualifiedType(
                property.getTypeReference(),
                packageName,
                imports,
                qualifiedNamesBySimpleName,
                kindsByQualifiedName);
        boolean collection = JimmerGraphQLSourceScanner.isCollectionType(rawTypeName(returnType));
        String elementType = collection ? resolveCollectionElementType(
                property.getTypeReference(),
                packageName,
                imports,
                qualifiedNamesBySimpleName,
                kindsByQualifiedName)
                : returnType;
        boolean complex = isComplex(annotations, elementType, kindsByQualifiedName);
        methods.add(new JimmerGraphQLSourceMethod(
                name,
                rawAccessorName(name, returnType),
                returnType,
                collection,
                elementType,
                complex,
                annotations.contains(JimmerGraphQLSourceScanner.TRANSIENT),
                new ArrayList<>(annotations)));
    }

    private void addFunction(
            List<JimmerGraphQLSourceMethod> methods,
            KtNamedFunction function,
            String packageName,
            List<String> imports,
            Map<String, String> qualifiedNamesBySimpleName,
            Map<String, JimmerGraphQLSourceKind> kindsByQualifiedName) {
        if (!isPropertyCandidate(function)) {
            return;
        }
        String name = function.getName();
        if (name == null || name.isBlank()) {
            return;
        }
        Set<String> annotations = annotationNames(function);
        String returnType = resolveQualifiedType(
                function.getTypeReference(),
                packageName,
                imports,
                qualifiedNamesBySimpleName,
                kindsByQualifiedName);
        boolean collection = JimmerGraphQLSourceScanner.isCollectionType(rawTypeName(returnType));
        String elementType = collection ? resolveCollectionElementType(
                function.getTypeReference(),
                packageName,
                imports,
                qualifiedNamesBySimpleName,
                kindsByQualifiedName)
                : returnType;
        boolean complex = isComplex(annotations, elementType, kindsByQualifiedName);
        methods.add(new JimmerGraphQLSourceMethod(
                name,
                name,
                returnType,
                collection,
                elementType,
                complex,
                annotations.contains(JimmerGraphQLSourceScanner.TRANSIENT),
                new ArrayList<>(annotations)));
    }

    private static boolean isComplex(
            Set<String> annotations,
            String elementType,
            Map<String, JimmerGraphQLSourceKind> kindsByQualifiedName) {
        return annotations.contains(JimmerGraphQLSourceScanner.TRANSIENT)
                || annotations.stream().anyMatch(JimmerGraphQLSourceScanner.ASSOCIATION_ANNOTATIONS::contains)
                || kindsByQualifiedName.get(elementType) == JimmerGraphQLSourceKind.ENTITY;
    }

    private static List<Path> kotlinSourceFiles(Path sourceDir) throws CodeGenException {
        if (!Files.isDirectory(sourceDir)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.walk(sourceDir)) {
            return stream.filter(path -> path.toString().endsWith(".kt")).toList();
        } catch (IOException ex) {
            throw new CodeGenException("Cannot scan Kotlin source directory: " + sourceDir, ex);
        }
    }

    private static JimmerGraphQLSourceKind sourceKind(KtClass type) {
        return JimmerGraphQLSourceScanner.sourceKind(annotationNames(type));
    }

    private static boolean isPropertyCandidate(KtProperty property) {
        return property.isMember()
                && !property.isLocal()
                && property.getReceiverTypeReference() == null;
    }

    private static boolean isPropertyCandidate(KtNamedFunction function) {
        return !function.isLocal()
                && function.getReceiverTypeReference() == null
                && function.getValueParameters().isEmpty();
    }

    private static Set<String> annotationNames(KtAnnotated annotated) {
        Set<String> annotations = new LinkedHashSet<>();
        for (KtAnnotationEntry annotation : annotated.getAnnotationEntries()) {
            if (annotation.getShortName() != null) {
                annotations.add(annotation.getShortName().asString());
            }
        }
        return annotations;
    }

    private static String resolveCollectionElementType(
            KtTypeReference typeReference,
            String packageName,
            List<String> imports,
            Map<String, String> qualifiedNamesBySimpleName,
            Map<String, JimmerGraphQLSourceKind> kindsByQualifiedName) {
        KtTypeElement typeElement = unwrapNullable(typeReference != null ? typeReference.getTypeElement() : null);
        if (!(typeElement instanceof KtUserType userType) || userType.getTypeArguments().isEmpty()) {
            return resolveQualifiedType(typeReference, packageName, imports, qualifiedNamesBySimpleName, kindsByQualifiedName);
        }
        KtTypeProjection projection = userType.getTypeArguments().get(0);
        return resolveQualifiedType(
                projection.getTypeReference(),
                packageName,
                imports,
                qualifiedNamesBySimpleName,
                kindsByQualifiedName);
    }

    private static String resolveQualifiedType(
            KtTypeReference typeReference,
            String packageName,
            List<String> imports,
            Map<String, String> qualifiedNamesBySimpleName,
            Map<String, JimmerGraphQLSourceKind> kindsByQualifiedName) {
        if (typeReference == null) {
            return "java.lang.Object";
        }
        return resolveTypeElement(
                unwrapNullable(typeReference.getTypeElement()),
                typeReference.getTypeText(),
                packageName,
                imports,
                qualifiedNamesBySimpleName,
                kindsByQualifiedName);
    }

    private static String resolveTypeElement(
            KtTypeElement typeElement,
            String fallbackTypeText,
            String packageName,
            List<String> imports,
            Map<String, String> qualifiedNamesBySimpleName,
            Map<String, JimmerGraphQLSourceKind> kindsByQualifiedName) {
        if (typeElement instanceof KtUserType userType) {
            String rawName = rawUserTypeName(userType);
            String qualifiedName = qualifyKotlinTypeName(rawName, packageName, imports, qualifiedNamesBySimpleName,
                    kindsByQualifiedName);
            List<KtTypeReference> arguments = userType.getTypeArgumentsAsTypes();
            if (arguments.isEmpty()) {
                return qualifiedName;
            }
            String genericArguments = arguments.stream()
                    .map(argument -> resolveQualifiedType(argument, packageName, imports, qualifiedNamesBySimpleName,
                            kindsByQualifiedName))
                    .reduce((left, right) -> left + ", " + right)
                    .orElse("");
            return qualifiedName + '<' + genericArguments + '>';
        }
        return qualifyKotlinTypeName(stripNullability(fallbackTypeText), packageName, imports, qualifiedNamesBySimpleName,
                kindsByQualifiedName);
    }

    private static KtTypeElement unwrapNullable(KtTypeElement typeElement) {
        KtTypeElement current = typeElement;
        while (current instanceof KtNullableType nullableType) {
            current = nullableType.getInnerType();
        }
        return current;
    }

    private static String rawUserTypeName(KtUserType userType) {
        String currentName = userType.getReferencedName();
        if (currentName == null || currentName.isBlank()) {
            return "java.lang.Object";
        }
        KtUserType qualifier = userType.getQualifier();
        if (qualifier == null) {
            return currentName;
        }
        return rawUserTypeName(qualifier) + '.' + currentName;
    }

    private static String qualifyKotlinTypeName(
            String rawTypeName,
            String packageName,
            List<String> imports,
            Map<String, String> qualifiedNamesBySimpleName,
            Map<String, JimmerGraphQLSourceKind> kindsByQualifiedName) {
        if (rawTypeName == null || rawTypeName.isBlank()) {
            return "java.lang.Object";
        }
        if (rawTypeName.contains(".")) {
            return switch (rawTypeName) {
                case "kotlin.String" -> "java.lang.String";
                case "kotlin.Long" -> "java.lang.Long";
                case "kotlin.Int" -> "java.lang.Integer";
                case "kotlin.Short" -> "java.lang.Short";
                case "kotlin.Byte" -> "java.lang.Byte";
                case "kotlin.Double" -> "java.lang.Double";
                case "kotlin.Float" -> "java.lang.Float";
                case "kotlin.Boolean" -> "java.lang.Boolean";
                case "kotlin.Char" -> "java.lang.Character";
                case "kotlin.collections.List", "kotlin.collections.MutableList" -> "java.util.List";
                case "kotlin.collections.Set", "kotlin.collections.MutableSet" -> "java.util.Set";
                case "kotlin.collections.Collection", "kotlin.collections.MutableCollection" -> "java.util.Collection";
                case "kotlin.collections.Map", "kotlin.collections.MutableMap" -> "java.util.Map";
                default -> rawTypeName;
            };
        }
        return switch (rawTypeName) {
            case "String" -> "java.lang.String";
            case "Long" -> "java.lang.Long";
            case "Int" -> "java.lang.Integer";
            case "Short" -> "java.lang.Short";
            case "Byte" -> "java.lang.Byte";
            case "Double" -> "java.lang.Double";
            case "Float" -> "java.lang.Float";
            case "Boolean" -> "java.lang.Boolean";
            case "Char" -> "java.lang.Character";
            case "List", "MutableList" -> "java.util.List";
            case "Set", "MutableSet" -> "java.util.Set";
            case "Collection", "MutableCollection" -> "java.util.Collection";
            case "Map", "MutableMap" -> "java.util.Map";
            default -> JimmerGraphQLSourceScanner.qualifySimpleName(
                    rawTypeName,
                    packageName,
                    imports,
                    qualifiedNamesBySimpleName,
                    kindsByQualifiedName);
        };
    }

    private static String rawAccessorName(String name, String returnType) {
        if (isBooleanType(returnType)
                && name.startsWith("is")
                && name.length() > 2
                && Character.isUpperCase(name.charAt(2))) {
            return name;
        }
        return "get" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    private static boolean isBooleanType(String qualifiedTypeName) {
        String rawTypeName = rawTypeName(qualifiedTypeName);
        return rawTypeName.equals("boolean") || rawTypeName.equals("java.lang.Boolean");
    }

    private static String rawTypeName(String qualifiedTypeName) {
        int genericStart = qualifiedTypeName.indexOf('<');
        return genericStart >= 0 ? qualifiedTypeName.substring(0, genericStart) : qualifiedTypeName;
    }

    private static String stripNullability(String typeName) {
        String normalized = typeName == null ? "" : typeName.trim();
        while (normalized.endsWith("?")) {
            normalized = normalized.substring(0, normalized.length() - 1).trim();
        }
        return normalized;
    }
}
