package io.quarkiverse.jimmer.test.optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.jar.JarFile;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.ProdBuildResults;
import io.quarkus.test.ProdModeTestResults;
import io.quarkus.test.QuarkusProdModeTest;

class OpenApiNativeMetadataTest {

    @RegisterExtension
    static final QuarkusProdModeTest APP = new QuarkusProdModeTest()
            .withEmptyApplication()
            .setApplicationName("openapi-metadata")
            .setBuildNative(true)
            .overrideConfigKey("quarkus.native.sources-only", "true")
            .overrideConfigKey("quarkus.class-loading.removed-artifacts", "org.babyfish.jimmer:jimmer-client")
            .overrideConfigKey("quarkus.devservices.enabled", "false")
            .overrideConfigKey("quarkus.datasource.jdbc", "false");

    @ProdBuildResults
    ProdModeTestResults build;

    @Test
    void optionalOpenApiClassesAreAbsentFromNativeLambdaMetadata() throws IOException {
        var runner = build.getBuildDir().resolve("native-sources/openapi-metadata-runner.jar");
        try (var jar = new JarFile(runner.toFile())) {
            var entry = jar.getJarEntry("META-INF/native-image/lambda/reachability-metadata.json");
            assertNotNull(entry, "Expected Quarkus native lambda metadata in the runner jar");
            try (var stream = jar.getInputStream(entry)) {
                var metadata = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                assertFalse(metadata.contains("io.quarkiverse.jimmer.runtime.client.openapi."),
                        "Native lambda registrations must not link optional OpenAPI classes");
            }
        }
    }
}
