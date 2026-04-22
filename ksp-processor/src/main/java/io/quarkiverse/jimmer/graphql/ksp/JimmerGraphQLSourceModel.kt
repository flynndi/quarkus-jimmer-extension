package io.quarkiverse.jimmer.graphql.ksp

class JimmerGraphQLSourceModel(types: List<JimmerGraphQLSourceType>) {

    companion object {
        private const val GRAPHQL_PACKAGE_SUFFIX = ".graphql"
    }

    private val typesByQualifiedName: Map<String, JimmerGraphQLSourceType> =
        LinkedHashMap<String, JimmerGraphQLSourceType>().apply {
            for (type in types) {
                val previous = putIfAbsent(type.qualifiedName, type)
                require(previous == null || previous == type) {
                    "Duplicate GraphQL source type: ${type.qualifiedName}"
                }
            }
        }

    fun entities(): List<JimmerGraphQLSourceType> =
        typesByQualifiedName.values.filter { it.isEntity() }

    fun type(qualifiedName: String): JimmerGraphQLSourceType? =
        typesByQualifiedName[qualifiedName]

    fun isEntityType(qualifiedName: String): Boolean =
        typesByQualifiedName[qualifiedName]?.isEntity() == true

    fun scalarMethods(type: JimmerGraphQLSourceType): List<JimmerGraphQLSourceMethod> =
        collectMethods(type, complex = false)

    fun complexMethods(type: JimmerGraphQLSourceType): List<JimmerGraphQLSourceMethod> =
        collectMethods(type, complex = true)

    fun facadeClassName(qualifiedName: String): String =
        requireNotNull(type(qualifiedName)) { "Illegal GraphQL source type: $qualifiedName" }.simpleName + "Gql"

    fun entityQualifiedNamesByGraphqlPackage(): Map<String, List<String>> =
        linkedMapOf<String, MutableList<String>>().also { entityNamesByPackage ->
            for (entity in entities()) {
                entityNamesByPackage.computeIfAbsent(graphqlPackageName(entity.qualifiedName)) { mutableListOf() }
                    .add(entity.qualifiedName)
            }
        }

    fun graphqlPackageName(qualifiedName: String): String {
        val type = requireNotNull(type(qualifiedName)) { "Illegal GraphQL source type: $qualifiedName" }
        return when {
            type.packageName.isEmpty() -> "graphql"
            type.packageName.endsWith(GRAPHQL_PACKAGE_SUFFIX) -> type.packageName
            else -> type.packageName + GRAPHQL_PACKAGE_SUFFIX
        }
    }

    private fun collectMethods(
        type: JimmerGraphQLSourceType,
        complex: Boolean,
    ): List<JimmerGraphQLSourceMethod> {
        val methods = linkedMapOf<String, JimmerGraphQLSourceMethod>()
        collectMethods(type, methods, complex)
        return methods.values.toList()
    }

    private fun collectMethods(
        type: JimmerGraphQLSourceType,
        methods: LinkedHashMap<String, JimmerGraphQLSourceMethod>,
        complex: Boolean,
    ) {
        for (parentName in type.extendsTypes) {
            val parent = typesByQualifiedName[parentName]
            if (parent != null && (parent.isEntity() || parent.isMappedSuperclass())) {
                collectMethods(parent, methods, complex)
            }
        }
        for (method in type.methods) {
            if (method.complex == complex) {
                methods[method.name] = method
            }
        }
    }
}
