package io.quarkiverse.jimmer.test.optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.builder.Version;
import io.quarkus.maven.dependency.ArtifactKey;
import io.quarkus.maven.dependency.Dependency;
import io.quarkus.test.QuarkusUnitTest;

class MissingClientMetadataApisTest {

    @RegisterExtension
    static final QuarkusUnitTest APP = OptionalIntegrationTestSupport.isolateExcludedDependencies(new QuarkusUnitTest(),
            withoutClientMetadataApis())
            .withApplicationRoot(archive -> archive.addClass(OptionalIntegrationTestSupport.class))
            .setForcedDependencies(List.of(Dependency.of("io.quarkus", "quarkus-vertx-http", Version.getVersion())))
            .overrideConfigKey("quarkus.datasource.devservices.enabled", "false")
            .overrideConfigKey("quarkus.redis.devservices.enabled", "false")
            .overrideConfigKey("quarkus.jimmer.client.openapi.path", "schema.yml")
            .overrideConfigKey("quarkus.jimmer.client.ts.path", "client.zip")
            .assertException(failure -> {
                String messages = OptionalIntegrationTestSupport.messages(failure);
                assertTrue(messages.contains("metadata APIs"), messages);
                assertTrue(messages.contains("quarkus.jimmer.client.openapi.path"), messages);
                assertTrue(messages.contains("quarkus.jimmer.client.ts.path"), messages);
                for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
                    assertFalse(cause instanceof LinkageError,
                            "Expected configuration validation, not a missing-class failure");
                }
            });

    @Test
    void httpAloneDoesNotSupplyTheClientMetadataApis() {
        fail("Expected a configuration failure before startup");
    }

    private static Set<ArtifactKey> withoutClientMetadataApis() {
        Set<ArtifactKey> excluded = new HashSet<>(OptionalIntegrationTestSupport.withoutRestClient());
        // Remove both REST extensions and their shared APIs; excluding only the server leaves
        // the metadata classes reachable through REST Client and the Maven test parent loader.
        Stream.of("quarkus-rest", "quarkus-rest-deployment", "quarkus-rest-dev",
                "quarkus-rest-jackson", "quarkus-rest-jackson-deployment",
                "quarkus-rest-common", "quarkus-rest-common-deployment",
                "quarkus-rest-client-jaxrs", "quarkus-rest-client-jaxrs-deployment",
                "quarkus-rest-client-config", "quarkus-rest-client-config-deployment",
                "quarkus-rest-client-spi-deployment", "quarkus-rest-server-spi-deployment")
                .map(name -> ArtifactKey.of("io.quarkus", name)).forEach(excluded::add);
        Stream.of("resteasy-reactive", "resteasy-reactive-processor", "resteasy-reactive-vertx",
                "resteasy-reactive-client", "resteasy-reactive-client-processor",
                "resteasy-reactive-common", "resteasy-reactive-common-processor", "resteasy-reactive-common-types")
                .map(name -> ArtifactKey.of("io.quarkus.resteasy.reactive", name)).forEach(excluded::add);
        excluded.add(ArtifactKey.of("jakarta.ws.rs", "jakarta.ws.rs-api"));
        return excluded;
    }
}
