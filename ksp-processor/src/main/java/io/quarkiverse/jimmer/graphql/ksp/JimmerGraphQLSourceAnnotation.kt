package io.quarkiverse.jimmer.graphql.ksp

data class JimmerGraphQLSourceAnnotation(
    val typeName: String,
    val arguments: List<Pair<String?, String>> = emptyList(),
) {

    fun render(): String =
        if (arguments.isEmpty()) {
            "@$typeName"
        } else {
            buildString {
                append('@')
                append(typeName)
                append('(')
                append(
                    arguments.joinToString(", ") { (name, value) ->
                        if (name.isNullOrBlank()) {
                            value
                        } else {
                            "$name = $value"
                        }
                    }
                )
                append(')')
            }
        }
}
