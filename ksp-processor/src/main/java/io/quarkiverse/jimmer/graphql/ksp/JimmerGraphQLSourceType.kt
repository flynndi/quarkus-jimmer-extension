package io.quarkiverse.jimmer.graphql.ksp

data class JimmerGraphQLSourceType(
    val packageName: String,
    val simpleName: String,
    val qualifiedName: String,
    val kind: JimmerGraphQLSourceKind,
    val extendsTypes: List<String>,
    val methods: List<JimmerGraphQLSourceMethod>,
) {

    fun isEntity(): Boolean = kind == JimmerGraphQLSourceKind.ENTITY

    fun isMappedSuperclass(): Boolean = kind == JimmerGraphQLSourceKind.MAPPED_SUPERCLASS
}
