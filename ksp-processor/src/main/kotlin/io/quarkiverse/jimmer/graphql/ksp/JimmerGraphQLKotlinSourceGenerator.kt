package io.quarkiverse.jimmer.graphql.ksp

class JimmerGraphQLKotlinSourceGenerator(
    private val model: JimmerGraphQLSourceModel,
) {

    data class GeneratedSource(
        val qualifiedName: String,
        val packageName: String,
        val fileName: String,
        val contents: String,
    )

    fun generate(): List<GeneratedSource> {
        val sources = mutableListOf<GeneratedSource>()
        var wroteAny = false
        for (entity in model.entities()) {
            val facadeQualifiedName = facadeQualifiedName(entity.qualifiedName)
            sources += GeneratedSource(
                qualifiedName = facadeQualifiedName,
                packageName = model.graphqlPackageName(entity.qualifiedName),
                fileName = model.facadeClassName(entity.qualifiedName),
                contents = facadeSource(entity),
            )
            wroteAny = true
            val complexMethods = model.complexMethods(entity)
            if (complexMethods.isNotEmpty()) {
                val resolverName = resolverClassName(entity)
                sources += GeneratedSource(
                    qualifiedName = model.graphqlPackageName(entity.qualifiedName) + "." + resolverName,
                    packageName = model.graphqlPackageName(entity.qualifiedName),
                    fileName = resolverName,
                    contents = resolverSource(entity, complexMethods),
                )
            }
        }
        if (wroteAny) {
            for ((packageName, entityNames) in model.entityQualifiedNamesByGraphqlPackage()) {
                sources += GeneratedSource(
                    qualifiedName = "$packageName.JimmerGraphQLFacadeRegistry",
                    packageName = packageName,
                    fileName = "JimmerGraphQLFacadeRegistry",
                    contents = registrySource(packageName, entityNames),
                )
            }
        }
        return sources
    }

    private fun facadeSource(entity: JimmerGraphQLSourceType): String =
        buildString {
            appendPackage(entity)
            appendLine("@org.eclipse.microprofile.graphql.Type(\"${escape(entity.simpleName)}\")")
            appendLine(
                "class ${model.facadeClassName(entity.qualifiedName)}(" +
                    "private val raw: ${entity.qualifiedName}" +
                    ") : io.quarkiverse.jimmer.runtime.graphql.facade.JimmerGraphQLFacade<${entity.qualifiedName}> {"
            )
            appendLine()
            for (method in model.scalarMethods(entity)) {
                for (annotation in methodAnnotations(method)) {
                    appendIndented(annotation)
                }
                appendIndented(
                    "fun ${getterName(method)}(): ${method.returnType} = raw.${method.rawAccessorName}()"
                )
                appendLine()
            }
            appendIndented("override fun __raw(): ${entity.qualifiedName} = raw")
            appendLine("}")
        }

    private fun resolverSource(
        entity: JimmerGraphQLSourceType,
        complexMethods: List<JimmerGraphQLSourceMethod>,
    ): String =
        buildString {
            appendPackage(entity)
            appendLine("@org.eclipse.microprofile.graphql.GraphQLApi")
            appendLine("class ${resolverClassName(entity)} {")
            appendLine()
            appendIndented("@jakarta.inject.Inject")
            appendIndented(
                "lateinit var support: io.quarkiverse.jimmer.runtime.graphql.facade.JimmerGraphQLFacadeSupport"
            )
            appendLine()
            for (method in complexMethods) {
                for (annotation in methodResolverAnnotations(method)) {
                    appendIndented(annotation)
                }
                appendIndented(
                    "fun ${method.name}(" +
                        "@org.eclipse.microprofile.graphql.Source(name = \"${escape(method.graphQLName)}\") " +
                        "sources: List<${facadeQualifiedName(methodOwnerQualifiedName(entity))}>," +
                        " context: io.smallrye.graphql.api.Context" +
                        "): ${batchResolverReturnType(method)} {"
                )
                appendIndented(
                    "    val env = context.unwrap(graphql.schema.DataFetchingEnvironment::class.java)"
                )
                appendIndented("    ${batchResolverReturnStatement(method)}")
                appendIndented("}")
                appendLine()
            }
            appendLine("}")
        }

    private fun registrySource(
        packageName: String,
        entityNames: List<String>,
    ): String =
        buildString {
            appendPackage(packageName)
            appendLine("@jakarta.inject.Singleton")
            appendLine("@io.quarkus.arc.Unremovable")
            appendLine(
                "class JimmerGraphQLFacadeRegistry : " +
                    "io.quarkiverse.jimmer.runtime.graphql.facade.JimmerGraphQLGeneratedFacadeRegistry {"
            )
            appendLine()
            appendIndented("override fun supportsFacadeType(facadeType: Class<*>): Boolean {")
            for (entityName in entityNames) {
                appendIndented(
                    "    if (facadeType == ${facadeQualifiedName(entityName)}::class.java) return true"
                )
            }
            appendIndented("    return false")
            appendIndented("}")
            appendLine()
            appendIndented("override fun supportsRaw(raw: Any?): Boolean {")
            appendIndented(
                "    if (raw == null || raw is io.quarkiverse.jimmer.runtime.graphql.facade.JimmerGraphQLFacade<*>) return false"
            )
            for (entityName in entityNames) {
                appendIndented("    if (raw is $entityName) return true")
            }
            appendIndented("    return false")
            appendIndented("}")
            appendLine()
            appendIndented("override fun <T> wrap(raw: Any?, facadeType: Class<T>): T {")
            appendIndented("    if (raw == null) return null as T")
            appendIndented("    if (facadeType.isInstance(raw)) return facadeType.cast(raw)")
            for (entityName in entityNames) {
                appendIndented("    if (facadeType == ${facadeQualifiedName(entityName)}::class.java) {")
                appendIndented("        if (raw !is $entityName) {")
                appendIndented(
                    "            throw IllegalArgumentException(" +
                        "\"Raw value type does not match GraphQL facade registry mapping: \" + raw.javaClass.name + \" -> \" + facadeType.name" +
                        ")"
                )
                appendIndented("        }")
                appendIndented("        return facadeType.cast(${facadeQualifiedName(entityName)}(raw))")
                appendIndented("    }")
            }
            appendIndented(
                "    throw IllegalArgumentException(\"Unsupported GraphQL facade type: \" + facadeType.name)"
            )
            appendIndented("}")
            appendLine()
            appendIndented("override fun wrap(raw: Any?): Any? {")
            appendIndented(
                "    if (raw == null || raw is io.quarkiverse.jimmer.runtime.graphql.facade.JimmerGraphQLFacade<*>) return raw"
            )
            for (entityName in entityNames) {
                appendIndented("    if (raw is $entityName) return ${facadeQualifiedName(entityName)}(raw)")
            }
            appendIndented("    return raw")
            appendIndented("}")
            appendLine("}")
        }

    private fun methodOwnerQualifiedName(entity: JimmerGraphQLSourceType): String =
        entity.qualifiedName

    private fun methodAnnotations(method: JimmerGraphQLSourceMethod): List<String> =
        buildList {
            if (method.graphQLName != method.name) {
                add("@org.eclipse.microprofile.graphql.Name(\"${escape(method.graphQLName)}\")")
            }
            addAll(method.annotations.map(JimmerGraphQLSourceAnnotation::render))
        }

    private fun methodResolverAnnotations(method: JimmerGraphQLSourceMethod): List<String> =
        buildList {
            add("@org.eclipse.microprofile.graphql.Name(\"${escape(method.graphQLName)}\")")
            addAll(method.annotations.map(JimmerGraphQLSourceAnnotation::render))
        }

    private fun batchResolverReturnType(method: JimmerGraphQLSourceMethod): String =
        "List<${batchResolverElementType(method)}>"

    private fun batchResolverElementType(method: JimmerGraphQLSourceMethod): String =
        when {
            method.collection && model.isEntityType(nonNullableTypeName(method.elementType)) ->
                entityCollectionFacadeType(method)
            model.isEntityType(nonNullableTypeName(method.elementType)) ->
                facadeTypeName(method.elementType)
            else -> method.returnType
        }

    private fun batchResolverReturnStatement(method: JimmerGraphQLSourceMethod): String =
        when {
            method.collection && model.isEntityType(nonNullableTypeName(method.elementType)) ->
                "return support.loadFacadeListBatch(sources, \"${escape(method.name)}\", env, ${facadeQualifiedName(nonNullableTypeName(method.elementType))}::class.java)"
            model.isEntityType(nonNullableTypeName(method.elementType)) ->
                "return support.loadFacadeBatch(sources, \"${escape(method.name)}\", env, ${facadeQualifiedName(nonNullableTypeName(method.elementType))}::class.java)"
            else ->
                "return support.loadValueBatch(sources, \"${escape(method.name)}\", env)"
        }

    private fun resolverClassName(entity: JimmerGraphQLSourceType): String =
        entity.simpleName + "GqlSourceResolver"

    private fun facadeQualifiedName(qualifiedEntityName: String): String =
        model.graphqlPackageName(qualifiedEntityName) + "." + model.facadeClassName(qualifiedEntityName)

    private fun facadeTypeName(typeName: String): String {
        val facadeQualifiedName = facadeQualifiedName(nonNullableTypeName(typeName))
        return if (typeName.endsWith("?")) {
            "$facadeQualifiedName?"
        } else {
            facadeQualifiedName
        }
    }

    private fun entityCollectionFacadeType(method: JimmerGraphQLSourceMethod): String {
        val facadeListType = "List<${facadeTypeName(method.elementType)}>"
        return if (method.returnType.endsWith("?")) {
            "$facadeListType?"
        } else {
            facadeListType
        }
    }

    private fun getterName(method: JimmerGraphQLSourceMethod): String =
        if (isBooleanType(method.returnType) &&
            method.name.startsWith("is") &&
            method.name.length > 2 &&
            method.name[2].isUpperCase()
        ) {
            method.name
        } else {
            "get" + method.name.replaceFirstChar { ch ->
                if (ch.isLowerCase()) ch.titlecase() else ch.toString()
            }
        }

    private fun isBooleanType(typeName: String): Boolean =
        typeName.removeSuffix("?") in setOf("kotlin.Boolean", "java.lang.Boolean")

    private fun nonNullableTypeName(typeName: String): String =
        typeName.removeSuffix("?")

    private fun StringBuilder.appendPackage(entity: JimmerGraphQLSourceType) {
        appendPackage(model.graphqlPackageName(entity.qualifiedName))
    }

    private fun StringBuilder.appendPackage(packageName: String) {
        appendLine("package $packageName")
        appendLine()
    }

    private fun StringBuilder.appendIndented(line: String) {
        appendLine("    $line")
    }

    private fun escape(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"")
}
