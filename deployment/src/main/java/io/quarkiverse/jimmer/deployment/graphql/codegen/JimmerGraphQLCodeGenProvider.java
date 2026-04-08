package io.quarkiverse.jimmer.deployment.graphql.codegen;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import io.quarkus.bootstrap.prebuild.CodeGenException;
import io.quarkus.deployment.CodeGenContext;
import io.quarkus.deployment.CodeGenProvider;

public final class JimmerGraphQLCodeGenProvider implements CodeGenProvider {

    private static final String LANGUAGE_KEY = "quarkus.jimmer.language";
    private static final String JAVA = "java";
    private static final String KOTLIN = "kotlin";

    @Override
    public String providerId() {
        return "jimmer-graphql";
    }

    @Override
    public String inputDirectory() {
        return "java";
    }

    @Override
    public boolean trigger(CodeGenContext context) throws CodeGenException {
        List<JimmerGraphQLSourceType> types = scanTypes(context);
        if (types.isEmpty()) {
            return false;
        }
        Path outDir = context.outDir();
        try {
            Files.createDirectories(outDir);
        } catch (IOException ex) {
            throw new CodeGenException("Cannot create GraphQL codegen output directory: " + outDir, ex);
        }
        JimmerGraphQLSourceModel model = new JimmerGraphQLSourceModel(types);
        JimmerGraphQLSourceWriter writer = new JimmerGraphQLSourceWriter(outDir, model);
        return writer.write();
    }

    private static List<JimmerGraphQLSourceType> scanTypes(CodeGenContext context) throws CodeGenException {
        String language = context.config()
                .getOptionalValue(LANGUAGE_KEY, String.class)
                .map(value -> value.toLowerCase(java.util.Locale.ROOT))
                .orElse(JAVA);
        if (KOTLIN.equals(language)) {
            return new JimmerGraphQLKotlinSourceScanner().scan(resolveSiblingSourceDir(context.inputDir(), KOTLIN));
        }
        if (!JAVA.equals(language)) {
            throw new CodeGenException(
                    "Unsupported Jimmer language for GraphQL codegen: " + language + ", expected 'java' or 'kotlin'");
        }
        return new JimmerGraphQLSourceScanner().scan(context.inputDir());
    }

    private static Path resolveSiblingSourceDir(Path sourceDir, String siblingName) {
        Path parent = sourceDir.getParent();
        return parent == null ? sourceDir.resolveSibling(siblingName) : parent.resolve(siblingName);
    }
}
