package io.quarkiverse.jimmer.graphql.ksp

import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration

class JimmerGraphQLSymbolProcessor(
    private val environment: SymbolProcessorEnvironment,
) : SymbolProcessor {

    private val collectedTypes = linkedMapOf<String, JimmerGraphQLSourceType>()

    private val emittedFqns = linkedSetOf<String>()

    private val scanner = JimmerGraphQLKspScanner()

    private var generated = false

    override fun process(resolver: Resolver): List<KSAnnotated> {
        if (generated) {
            return emptyList()
        }
        collectRootTypes(resolver)
        if (collectedTypes.isEmpty()) {
            return emptyList()
        }
        generateSources()
        generated = true
        return emptyList()
    }

    private fun collectRootTypes(resolver: Resolver) {
        resolver.getAllFiles().forEach { file ->
            file.declarations
                .filterIsInstance<KSClassDeclaration>()
                .filter { it.classKind == ClassKind.INTERFACE }
                .forEach(::collectType)
        }
    }

    private fun collectType(type: KSClassDeclaration) {
        val qualifiedName = type.qualifiedName?.asString() ?: return
        if (qualifiedName in collectedTypes) {
            return
        }
        val kind = scanner.sourceKind(type)
        if (kind != JimmerGraphQLSourceKind.ENTITY && kind != JimmerGraphQLSourceKind.MAPPED_SUPERCLASS) {
            return
        }
        collectedTypes[qualifiedName] = scanner.scanType(type)
        type.superTypes.forEach { superType ->
            val declaration = superType.resolve().declaration as? KSClassDeclaration ?: return@forEach
            collectType(declaration)
        }
    }

    private fun generateSources() {
        val model = JimmerGraphQLSourceModel(collectedTypes.values.toList())
        val generator = JimmerGraphQLKotlinSourceGenerator(model)
        for (source in generator.generate()) {
            if (!emittedFqns.add(source.qualifiedName)) {
                error("GraphQL facade source generated twice in the same compilation: ${source.qualifiedName}")
            }
            val file = environment.codeGenerator.createNewFile(
                dependencies = Dependencies.ALL_FILES,
                packageName = source.packageName,
                fileName = source.fileName,
                extensionName = "kt",
            )
            file.bufferedWriter().use { writer ->
                writer.write(source.contents)
            }
        }
    }

    private fun error(message: String): Nothing {
        environment.logger.error(message)
        throw IllegalStateException(message)
    }
}
