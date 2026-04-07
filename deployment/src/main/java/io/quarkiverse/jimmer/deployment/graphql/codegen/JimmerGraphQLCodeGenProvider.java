package io.quarkiverse.jimmer.deployment.graphql.codegen;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import io.quarkus.bootstrap.prebuild.CodeGenException;
import io.quarkus.deployment.CodeGenContext;
import io.quarkus.deployment.CodeGenProvider;

public final class JimmerGraphQLCodeGenProvider implements CodeGenProvider {

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
        JimmerGraphQLSourceScanner scanner = new JimmerGraphQLSourceScanner();
        List<JimmerGraphQLSourceType> types = scanner.scan(context.inputDir());
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
}
