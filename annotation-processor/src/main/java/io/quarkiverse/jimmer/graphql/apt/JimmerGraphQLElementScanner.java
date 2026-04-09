package io.quarkiverse.jimmer.graphql.apt;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;

final class JimmerGraphQLElementScanner {

    static final String ENTITY = "org.babyfish.jimmer.sql.Entity";
    static final String MAPPED_SUPERCLASS = "org.babyfish.jimmer.sql.MappedSuperclass";
    static final String TRANSIENT = "org.babyfish.jimmer.sql.Transient";

    private static final Set<String> ASSOCIATION_ANNOTATIONS = Set.of(
            "org.babyfish.jimmer.sql.ManyToOne",
            "org.babyfish.jimmer.sql.OneToOne",
            "org.babyfish.jimmer.sql.OneToMany",
            "org.babyfish.jimmer.sql.ManyToMany");

    private static final Set<String> COLLECTION_TYPES = Set.of(
            "java.util.List",
            "java.util.Collection",
            "java.util.Set");

    private final Elements elementUtils;

    JimmerGraphQLElementScanner(Elements elementUtils) {
        this.elementUtils = elementUtils;
    }

    JimmerGraphQLSourceType scanType(TypeElement type) {
        List<String> extendsTypes = new ArrayList<>();
        for (TypeMirror interfaceType : type.getInterfaces()) {
            extendsTypes.add(rawTypeQualifiedName(interfaceType));
        }
        List<JimmerGraphQLSourceMethod> methods = new ArrayList<>();
        for (Element enclosed : type.getEnclosedElements()) {
            if (enclosed.getKind() != ElementKind.METHOD) {
                continue;
            }
            ExecutableElement method = (ExecutableElement) enclosed;
            if (!isPropertyCandidate(method)) {
                continue;
            }
            Set<String> annotations = annotationNames(method);
            TypeMirror returnType = method.getReturnType();
            boolean collection = isCollectionType(returnType);
            TypeMirror elementType = collection ? collectionElementType(returnType) : returnType;
            String elementTypeName = qualifiedTypeName(elementType);
            boolean complex = annotations.contains(TRANSIENT)
                    || annotations.stream().anyMatch(ASSOCIATION_ANNOTATIONS::contains)
                    || sourceKind(elementType) == JimmerGraphQLSourceKind.ENTITY;
            methods.add(new JimmerGraphQLSourceMethod(
                    method.getSimpleName().toString(),
                    method.getSimpleName().toString(),
                    qualifiedTypeName(returnType),
                    collection,
                    elementTypeName,
                    complex,
                    annotations.contains(TRANSIENT),
                    List.copyOf(annotations)));
        }
        return new JimmerGraphQLSourceType(
                elementUtils.getPackageOf(type).getQualifiedName().toString(),
                type.getSimpleName().toString(),
                type.getQualifiedName().toString(),
                sourceKind(type),
                List.copyOf(extendsTypes),
                List.copyOf(methods));
    }

    JimmerGraphQLSourceKind sourceKind(TypeElement type) {
        return sourceKind((Element) type);
    }

    JimmerGraphQLSourceKind sourceKind(TypeMirror type) {
        if (!(type instanceof DeclaredType declaredType) || !(declaredType.asElement() instanceof TypeElement typeElement)) {
            return JimmerGraphQLSourceKind.OTHER;
        }
        return sourceKind(typeElement);
    }

    private JimmerGraphQLSourceKind sourceKind(Element element) {
        if (hasAnnotation(element, ENTITY)) {
            return JimmerGraphQLSourceKind.ENTITY;
        }
        if (hasAnnotation(element, MAPPED_SUPERCLASS)) {
            return JimmerGraphQLSourceKind.MAPPED_SUPERCLASS;
        }
        if (element.getKind() == ElementKind.ENUM) {
            return JimmerGraphQLSourceKind.ENUM;
        }
        return JimmerGraphQLSourceKind.OTHER;
    }

    private static boolean isPropertyCandidate(ExecutableElement method) {
        return method.getParameters().isEmpty()
                && !method.isDefault()
                && !method.getModifiers().contains(Modifier.PRIVATE)
                && !method.getModifiers().contains(Modifier.STATIC);
    }

    private static Set<String> annotationNames(Element element) {
        Set<String> annotations = new LinkedHashSet<>();
        for (AnnotationMirror annotation : element.getAnnotationMirrors()) {
            annotations.add(annotation.getAnnotationType().toString());
        }
        return annotations;
    }

    private static boolean hasAnnotation(Element element, String annotationType) {
        return annotationNames(element).contains(annotationType);
    }

    private static boolean isCollectionType(TypeMirror type) {
        return COLLECTION_TYPES.contains(rawTypeQualifiedName(type));
    }

    private static TypeMirror collectionElementType(TypeMirror type) {
        if (!(type instanceof DeclaredType declaredType) || declaredType.getTypeArguments().isEmpty()) {
            return type;
        }
        return declaredType.getTypeArguments().get(0);
    }

    private static String rawTypeQualifiedName(TypeMirror type) {
        if (type instanceof DeclaredType declaredType && declaredType.asElement() instanceof TypeElement typeElement) {
            return typeElement.getQualifiedName().toString();
        }
        return type.toString();
    }

    private static String qualifiedTypeName(TypeMirror type) {
        if (type instanceof ArrayType arrayType) {
            return qualifiedTypeName(arrayType.getComponentType()) + "[]";
        }
        if (type instanceof DeclaredType declaredType && declaredType.asElement() instanceof TypeElement typeElement) {
            String qualifiedName = typeElement.getQualifiedName().toString();
            if (declaredType.getTypeArguments().isEmpty()) {
                return qualifiedName;
            }
            StringBuilder builder = new StringBuilder(qualifiedName).append('<');
            for (int i = 0; i < declaredType.getTypeArguments().size(); i++) {
                if (i > 0) {
                    builder.append(", ");
                }
                builder.append(qualifiedTypeName(declaredType.getTypeArguments().get(i)));
            }
            return builder.append('>').toString();
        }
        return type.toString();
    }
}
