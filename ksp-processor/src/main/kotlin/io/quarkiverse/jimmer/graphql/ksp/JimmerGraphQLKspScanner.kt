package io.quarkiverse.jimmer.graphql.ksp

import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType

class JimmerGraphQLKspScanner {

    companion object {
        const val ENTITY = "org.babyfish.jimmer.sql.Entity"
        const val MAPPED_SUPERCLASS = "org.babyfish.jimmer.sql.MappedSuperclass"
        const val TRANSIENT = "org.babyfish.jimmer.sql.Transient"
        const val GRAPHQL_NAME = "org.eclipse.microprofile.graphql.Name"
        private const val GRAPHQL_ANNOTATION_PREFIX = "org.eclipse.microprofile.graphql."

        private val ASSOCIATION_ANNOTATIONS = setOf(
            "org.babyfish.jimmer.sql.ManyToOne",
            "org.babyfish.jimmer.sql.OneToOne",
            "org.babyfish.jimmer.sql.OneToMany",
            "org.babyfish.jimmer.sql.ManyToMany",
        )

        private val COLLECTION_TYPES = setOf(
            "kotlin.collections.List",
            "kotlin.collections.Collection",
            "kotlin.collections.Set",
            "java.util.List",
            "java.util.Collection",
            "java.util.Set",
        )
    }

    fun scanType(type: KSClassDeclaration): JimmerGraphQLSourceType {
        val extendsTypes = type.superTypes
            .mapNotNull { rawTypeQualifiedName(it.resolve()) }
            .filterNot { it == "kotlin.Any" }
            .toList()

        val methods = type.declarations
            .filterIsInstance<KSPropertyDeclaration>()
            .filter(::isPropertyCandidate)
            .map { property ->
                val annotationNames = annotationNames(property)
                val returnType = property.type.resolve()
                val collection = isCollectionType(returnType)
                val elementType = if (collection) {
                    collectionElementType(returnType)
                } else {
                    returnType
                }
                val elementTypeName = qualifiedTypeName(elementType)
                val complex = annotationNames.contains(TRANSIENT) ||
                    annotationNames.any { it in ASSOCIATION_ANNOTATIONS } ||
                    sourceKind(elementType) == JimmerGraphQLSourceKind.ENTITY
                JimmerGraphQLSourceMethod(
                    name = property.simpleName.asString(),
                    returnType = qualifiedTypeName(returnType),
                    collection = collection,
                    elementType = elementTypeName,
                    complex = complex,
                    transientResolver = annotationNames.contains(TRANSIENT),
                    graphQLName = graphQLName(property),
                    annotations = annotationSpecs(property),
                )
            }
            .toList()

        return JimmerGraphQLSourceType(
            packageName = type.packageName.asString(),
            simpleName = type.simpleName.asString(),
            qualifiedName = requireNotNull(type.qualifiedName?.asString()) {
                "Local or anonymous Kotlin entity is not supported: ${type.simpleName.asString()}"
            },
            kind = sourceKind(type),
            extendsTypes = extendsTypes,
            methods = methods,
        )
    }

    fun sourceKind(type: KSClassDeclaration): JimmerGraphQLSourceKind =
        sourceKind(type as KSDeclaration)

    fun sourceKind(type: KSType): JimmerGraphQLSourceKind =
        sourceKind(type.declaration)

    private fun sourceKind(declaration: KSDeclaration): JimmerGraphQLSourceKind {
        if (declaration !is KSClassDeclaration) {
            return JimmerGraphQLSourceKind.OTHER
        }
        return when {
            hasAnnotation(declaration, ENTITY) -> JimmerGraphQLSourceKind.ENTITY
            hasAnnotation(declaration, MAPPED_SUPERCLASS) -> JimmerGraphQLSourceKind.MAPPED_SUPERCLASS
            declaration.classKind == ClassKind.ENUM_CLASS -> JimmerGraphQLSourceKind.ENUM
            else -> JimmerGraphQLSourceKind.OTHER
        }
    }

    private fun isPropertyCandidate(property: KSPropertyDeclaration): Boolean =
        property.extensionReceiver == null

    private fun annotationNames(declaration: KSDeclaration): Set<String> =
        declaration.annotations.mapNotNull(::annotationQualifiedName).toSet()

    private fun graphQLName(declaration: KSDeclaration): String {
        for (annotation in declaration.annotations) {
            if (annotationQualifiedName(annotation) != GRAPHQL_NAME) {
                continue
            }
            for (argument in annotation.arguments) {
                if (argument.name?.asString() == "value") {
                    val value = argument.value as? String
                    if (!value.isNullOrBlank()) {
                        return value
                    }
                }
            }
        }
        return declaration.simpleName.asString()
    }

    private fun annotationSpecs(declaration: KSDeclaration): List<JimmerGraphQLSourceAnnotation> =
        declaration.annotations.mapNotNull { annotation ->
            val annotationType = annotationQualifiedName(annotation) ?: return@mapNotNull null
            if (annotationType == GRAPHQL_NAME ||
                annotationType == TRANSIENT ||
                annotationType in ASSOCIATION_ANNOTATIONS
            ) {
                return@mapNotNull null
            }
            if (!isSafeFunctionAnnotation(annotationType)) {
                return@mapNotNull null
            }
            JimmerGraphQLSourceAnnotation(
                typeName = annotationType,
                arguments = annotation.arguments.map { argument ->
                    argument.name?.asString() to renderAnnotationValue(argument.value)
                }.filter { (_, value) -> value.isNotBlank() }
            )
        }.toList()

    private fun isSafeFunctionAnnotation(annotationType: String): Boolean =
        annotationType.startsWith(GRAPHQL_ANNOTATION_PREFIX) ||
            annotationType == "java.lang.Deprecated" ||
            annotationType == "kotlin.Deprecated"

    private fun hasAnnotation(declaration: KSDeclaration, annotationType: String): Boolean =
        annotationNames(declaration).contains(annotationType)

    private fun isCollectionType(type: KSType): Boolean =
        rawTypeQualifiedName(type) in COLLECTION_TYPES

    private fun collectionElementType(type: KSType): KSType =
        type.arguments.firstOrNull()?.type?.resolve() ?: type

    private fun rawTypeQualifiedName(type: KSType): String? =
        (type.declaration as? KSClassDeclaration)?.qualifiedName?.asString()

    private fun qualifiedTypeName(type: KSType): String {
        val declaration = type.declaration
        val qualifiedName = declaration.qualifiedName?.asString() ?: declaration.simpleName.asString()
        val arguments = type.arguments
            .map { argument ->
                if (argument.type == null) {
                    "*"
                } else {
                    qualifiedTypeName(argument.type!!.resolve())
                }
            }
        val base = if (arguments.isEmpty()) {
            qualifiedName
        } else {
            "$qualifiedName<${arguments.joinToString(", ")}>"
        }
        return if (type.isMarkedNullable) "$base?" else base
    }

    private fun annotationQualifiedName(annotation: KSAnnotation): String? =
        annotation.annotationType.resolve().declaration.qualifiedName?.asString()

    private fun renderAnnotationValue(value: Any?): String =
        when (value) {
            null -> ""
            is String -> "\"" + value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n") + "\""
            is Char -> "'" + value.toString()
                .replace("\\", "\\\\")
                .replace("'", "\\'") + "'"
            is Boolean, is Byte, is Short, is Int, is Long, is Float, is Double -> value.toString()
            is List<*> -> value.joinToString(prefix = "[", postfix = "]") { renderAnnotationValue(it) }
            is KSType -> qualifiedTypeName(value) + "::class"
            is KSDeclaration -> (value.qualifiedName?.asString() ?: value.simpleName.asString()) + "::class"
            else -> value.toString()
        }
}
