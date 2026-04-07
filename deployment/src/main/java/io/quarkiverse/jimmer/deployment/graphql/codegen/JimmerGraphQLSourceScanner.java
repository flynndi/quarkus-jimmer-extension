package io.quarkiverse.jimmer.deployment.graphql.codegen;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;
import com.github.javaparser.ast.type.ArrayType;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.PrimitiveType;
import com.github.javaparser.ast.type.Type;

import io.quarkus.bootstrap.prebuild.CodeGenException;

final class JimmerGraphQLSourceScanner {

    private static final Set<String> ASSOCIATION_ANNOTATIONS = Set.of(
            "ManyToOne",
            "OneToOne",
            "OneToMany",
            "ManyToMany");

    private static final String ENTITY = "Entity";
    private static final String MAPPED_SUPERCLASS = "MappedSuperclass";

    private final JavaParser parser = new JavaParser();

    List<JimmerGraphQLSourceType> scan(Path sourceDir) throws CodeGenException {
        List<CompilationUnit> units = parseUnits(sourceDir);
        if (units.isEmpty()) {
            return List.of();
        }
        Map<String, String> qualifiedNamesBySimpleName = new LinkedHashMap<>();
        Map<String, JimmerGraphQLSourceKind> kindsByQualifiedName = new LinkedHashMap<>();
        for (CompilationUnit unit : units) {
            String packageName = unit.getPackageDeclaration()
                    .map(declaration -> declaration.getNameAsString())
                    .orElse("");
            for (var type : unit.getTypes()) {
                String qualifiedName = qualify(packageName, type.getNameAsString());
                qualifiedNamesBySimpleName.putIfAbsent(type.getNameAsString(), qualifiedName);
                if (type instanceof EnumDeclaration) {
                    kindsByQualifiedName.put(qualifiedName, JimmerGraphQLSourceKind.ENUM);
                } else if (type instanceof ClassOrInterfaceDeclaration declaration && declaration.isInterface()) {
                    kindsByQualifiedName.put(qualifiedName, sourceKind(declaration));
                }
            }
        }
        List<JimmerGraphQLSourceType> scannedTypes = new ArrayList<>();
        for (CompilationUnit unit : units) {
            String packageName = unit.getPackageDeclaration()
                    .map(declaration -> declaration.getNameAsString())
                    .orElse("");
            List<String> imports = unit.getImports().stream()
                    .filter(importDeclaration -> !importDeclaration.isAsterisk())
                    .map(ImportDeclaration::getNameAsString)
                    .toList();
            for (EnumDeclaration enumDeclaration : unit.findAll(EnumDeclaration.class,
                    enumDeclaration -> enumDeclaration.isTopLevelType())) {
                String qualifiedName = qualify(packageName, enumDeclaration.getNameAsString());
                scannedTypes.add(new JimmerGraphQLSourceType(
                        packageName,
                        enumDeclaration.getNameAsString(),
                        qualifiedName,
                        JimmerGraphQLSourceKind.ENUM,
                        imports,
                        List.of(),
                        List.of()));
            }
            for (ClassOrInterfaceDeclaration declaration : unit.findAll(
                    ClassOrInterfaceDeclaration.class,
                    candidate -> candidate.isTopLevelType() && candidate.isInterface())) {
                JimmerGraphQLSourceKind kind = sourceKind(declaration);
                if (kind == JimmerGraphQLSourceKind.OTHER) {
                    continue;
                }
                String qualifiedName = qualify(packageName, declaration.getNameAsString());
                List<String> extendsTypes = declaration.getExtendedTypes().stream()
                        .map(type -> resolveQualifiedType(type, packageName, imports, qualifiedNamesBySimpleName,
                                kindsByQualifiedName))
                        .toList();
                List<JimmerGraphQLSourceMethod> methods = new ArrayList<>();
                for (MethodDeclaration method : declaration.getMethods()) {
                    if (!isPropertyCandidate(method)) {
                        continue;
                    }
                    Set<String> annotations = annotationNames(method);
                    String returnType = resolveQualifiedType(method.getType(), packageName, imports, qualifiedNamesBySimpleName,
                            kindsByQualifiedName);
                    boolean collection = isCollectionType(method.getType());
                    String elementType = collection ? resolveCollectionElementType(method.getType(), packageName, imports,
                            qualifiedNamesBySimpleName, kindsByQualifiedName)
                            : returnType;
                    boolean complex = annotations.contains("Transient")
                            || annotations.stream().anyMatch(ASSOCIATION_ANNOTATIONS::contains)
                            || kindsByQualifiedName.get(elementType) == JimmerGraphQLSourceKind.ENTITY;
                    methods.add(new JimmerGraphQLSourceMethod(
                            method.getNameAsString(),
                            returnType,
                            collection,
                            elementType,
                            complex,
                            annotations.contains("Transient"),
                            new ArrayList<>(annotations)));
                }
                scannedTypes.add(new JimmerGraphQLSourceType(
                        packageName,
                        declaration.getNameAsString(),
                        qualifiedName,
                        kind,
                        imports,
                        extendsTypes,
                        methods));
            }
        }
        return scannedTypes;
    }

    private List<CompilationUnit> parseUnits(Path sourceDir) throws CodeGenException {
        try (Stream<Path> stream = Files.walk(sourceDir)) {
            List<CompilationUnit> units = new ArrayList<>();
            for (Path sourceFile : stream.filter(path -> path.toString().endsWith(".java")).toList()) {
                ParseResult<CompilationUnit> result = parser.parse(sourceFile);
                CompilationUnit unit = result.getResult()
                        .orElseThrow(() -> new CodeGenException("Cannot parse source file: " + sourceFile));
                units.add(unit);
            }
            return units;
        } catch (UncheckedIOException | IOException ex) {
            throw new CodeGenException("Cannot scan source directory: " + sourceDir, ex);
        }
    }

    private static JimmerGraphQLSourceKind sourceKind(ClassOrInterfaceDeclaration declaration) {
        Set<String> annotations = annotationNames(declaration);
        if (annotations.contains(ENTITY)) {
            return JimmerGraphQLSourceKind.ENTITY;
        }
        if (annotations.contains(MAPPED_SUPERCLASS)) {
            return JimmerGraphQLSourceKind.MAPPED_SUPERCLASS;
        }
        return JimmerGraphQLSourceKind.OTHER;
    }

    private static Set<String> annotationNames(NodeWithAnnotations<?> node) {
        Set<String> annotations = new LinkedHashSet<>();
        node.getAnnotations().forEach(annotation -> annotations.add(annotation.getName().getIdentifier()));
        return annotations;
    }

    private static boolean isPropertyCandidate(MethodDeclaration method) {
        return method.getParameters().isEmpty()
                && !method.isDefault()
                && !method.isPrivate()
                && !method.isStatic();
    }

    private static boolean isCollectionType(Type type) {
        if (!(type instanceof ClassOrInterfaceType classType)) {
            return false;
        }
        String name = classType.getName().getIdentifier();
        return name.equals("List") || name.equals("Collection") || name.equals("Set");
    }

    private static String resolveCollectionElementType(
            Type type,
            String packageName,
            List<String> imports,
            Map<String, String> qualifiedNamesBySimpleName,
            Map<String, JimmerGraphQLSourceKind> kindsByQualifiedName) {
        if (!(type instanceof ClassOrInterfaceType classType)) {
            return resolveQualifiedType(type, packageName, imports, qualifiedNamesBySimpleName, kindsByQualifiedName);
        }
        Type elementType = classType.getTypeArguments()
                .filter(arguments -> !arguments.isEmpty())
                .map(arguments -> arguments.get(0))
                .orElse(type);
        return resolveQualifiedType(elementType, packageName, imports, qualifiedNamesBySimpleName, kindsByQualifiedName);
    }

    private static String resolveQualifiedType(
            Type type,
            String packageName,
            List<String> imports,
            Map<String, String> qualifiedNamesBySimpleName,
            Map<String, JimmerGraphQLSourceKind> kindsByQualifiedName) {
        if (type instanceof PrimitiveType primitiveType) {
            return primitiveType.asString();
        }
        if (type instanceof ArrayType arrayType) {
            return resolveQualifiedType(arrayType.getComponentType(), packageName, imports, qualifiedNamesBySimpleName,
                    kindsByQualifiedName) + "[]";
        }
        if (type instanceof ClassOrInterfaceType classType) {
            String scope = classType.getScope()
                    .map(value -> resolveQualifiedType(value, packageName, imports, qualifiedNamesBySimpleName,
                            kindsByQualifiedName))
                    .orElse(null);
            String rawName = scope != null ? scope + '.' + classType.getNameAsString() : classType.getNameAsString();
            String qualifiedName = qualifySimpleName(rawName, packageName, imports, qualifiedNamesBySimpleName,
                    kindsByQualifiedName);
            if (classType.getTypeArguments().isEmpty()) {
                return qualifiedName;
            }
            String genericArguments = classType.getTypeArguments().orElseThrow().stream()
                    .map(argument -> resolveQualifiedType(argument, packageName, imports, qualifiedNamesBySimpleName,
                            kindsByQualifiedName))
                    .reduce((left, right) -> left + ", " + right)
                    .orElse("");
            return qualifiedName + '<' + genericArguments + '>';
        }
        return type.asString();
    }

    private static String qualifySimpleName(
            String name,
            String packageName,
            List<String> imports,
            Map<String, String> qualifiedNamesBySimpleName,
            Map<String, JimmerGraphQLSourceKind> kindsByQualifiedName) {
        if (name.contains(".")) {
            return name;
        }
        String samePackageName = qualify(packageName, name);
        if (kindsByQualifiedName.containsKey(samePackageName)) {
            return samePackageName;
        }
        String fromImports = imports.stream()
                .filter(importName -> importName.endsWith('.' + name))
                .findFirst()
                .orElse(null);
        if (fromImports != null) {
            return fromImports;
        }
        String fromKnownTypes = qualifiedNamesBySimpleName.get(name);
        if (fromKnownTypes != null) {
            return fromKnownTypes;
        }
        if (isJavaLangType(name)) {
            return "java.lang." + name;
        }
        return switch (name) {
            case "List" -> "java.util.List";
            case "Set" -> "java.util.Set";
            case "Collection" -> "java.util.Collection";
            case "Map" -> "java.util.Map";
            default -> name;
        };
    }

    private static boolean isJavaLangType(String name) {
        String normalized = name.toLowerCase(Locale.ROOT);
        return normalized.equals("string")
                || normalized.equals("long")
                || normalized.equals("integer")
                || normalized.equals("double")
                || normalized.equals("float")
                || normalized.equals("boolean")
                || normalized.equals("byte")
                || normalized.equals("short")
                || normalized.equals("character")
                || normalized.equals("void");
    }

    private static String qualify(String packageName, String simpleName) {
        return packageName == null || packageName.isBlank() ? simpleName : packageName + '.' + simpleName;
    }
}
