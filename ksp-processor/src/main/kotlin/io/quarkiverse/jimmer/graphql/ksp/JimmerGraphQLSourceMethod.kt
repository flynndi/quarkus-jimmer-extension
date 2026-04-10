package io.quarkiverse.jimmer.graphql.ksp

data class JimmerGraphQLSourceMethod(
    val name: String,
    val rawAccessorName: String,
    val returnType: String,
    val collection: Boolean,
    val elementType: String,
    val complex: Boolean,
    val transientResolver: Boolean,
    val graphQLName: String,
    val annotations: List<JimmerGraphQLSourceAnnotation>,
)
