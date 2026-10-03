package io.quarkiverse.jimmer.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.eclipse.microprofile.config.spi.ConfigProviderResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkus.deployment.Capabilities;
import io.quarkus.deployment.Capability;
import io.quarkus.deployment.builditem.LaunchModeBuildItem;
import io.quarkus.maven.dependency.ResolvedDependency;
import io.quarkus.maven.dependency.ResolvedDependencyBuilder;
import io.quarkus.runtime.LaunchMode;
import io.quarkus.vertx.http.runtime.VertxHttpBuildTimeConfig;
import io.quarkus.vertx.http.runtime.management.ManagementInterfaceBuildTimeConfig;
import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;

class JimmerDevUILinksTest {

    private static final Capabilities HTTP = new Capabilities(Set.of(Capability.VERTX_HTTP));
    private static final LaunchModeBuildItem DEV = new LaunchModeBuildItem(LaunchMode.DEVELOPMENT,
            Optional.empty(), false, Optional.empty(), false);
    private static final ResolvedDependency SWAGGER = ResolvedDependencyBuilder.newInstance()
            .setGroupId("io.quarkus").setArtifactId("quarkus-swagger-ui").setVersion("3.39.1").setRuntimeCp().build();

    private ClassLoader originalLoader;
    private SmallRyeConfig config;

    @BeforeEach
    void isolateConfig() {
        originalLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(new ClassLoader(originalLoader) {
        });
    }

    @AfterEach
    void restoreConfig() {
        if (config != null) {
            ConfigProviderResolver.instance().releaseConfig(config);
        }
        Thread.currentThread().setContextClassLoader(originalLoader);
    }

    @Test
    void relativePathUsesHttpAndNonApplicationRootsWithDevContext() {
        configure(Map.of("quarkus.http.root-path", "/app", "quarkus.http.non-application-root-path", "internal"));
        assertEquals(Optional.of("/proxy/app/internal/swagger-ui"), swagger(true, HTTP, List.of(SWAGGER), "/proxy/"));
    }

    @Test
    void absolutePathDoesNotInheritApplicationRoots() {
        configure(Map.of("quarkus.http.root-path", "/app", "quarkus.http.non-application-root-path", "internal",
                "quarkus.swagger-ui.path", "/native/swagger"));
        assertEquals(Optional.of("/native/swagger"), swagger(true, HTTP, List.of(SWAGGER), ""));
    }

    @Test
    void managementLinkUsesItsOwnOriginAndRootWithoutDevContext() {
        configure(Map.of("quarkus.http.root-path", "/app", "quarkus.http.non-application-root-path", "internal",
                "quarkus.management.enabled", "true", "quarkus.management.root-path", "/operations",
                "quarkus.management.host", "127.0.0.1", "quarkus.management.port", "9191"));
        assertEquals(Optional.of("http://127.0.0.1:9191/operations/swagger-ui"),
                swagger(true, HTTP, List.of(SWAGGER), "/proxy"));
    }

    @Test
    void managementOptOutKeepsTheMainServerNonApplicationRoot() {
        configure(Map.of("quarkus.http.root-path", "/app", "quarkus.http.non-application-root-path", "internal",
                "quarkus.management.enabled", "true", "quarkus.management.root-path", "/operations",
                "quarkus.smallrye-openapi.management.enabled", "false"));
        assertEquals(Optional.of("/app/internal/swagger-ui"), swagger(true, HTTP, List.of(SWAGGER), ""));
    }

    @Test
    void missingRuntimeExtensionAndDisabledFeaturesDoNotCreateLinks() {
        configure(Map.of());
        ResolvedDependency deploymentOnly = ResolvedDependencyBuilder.newInstance()
                .setGroupId("io.quarkus").setArtifactId("quarkus-swagger-ui").setVersion("3.39.1")
                .setDeploymentCp().build();
        assertFalse(JimmerDevUILinks.hasSwaggerUi(List.of(deploymentOnly)));
        assertTrue(swagger(true, HTTP, List.of(), "").isEmpty());
        assertTrue(swagger(true, new Capabilities(Set.of()), List.of(SWAGGER), "").isEmpty());
        assertTrue(swagger(false, HTTP, List.of(SWAGGER), "").isEmpty());
    }

    @Test
    void explicitlyDisabledNativeUiDoesNotCreateLink() {
        configure(Map.of("quarkus.swagger-ui.enabled", "false"));
        assertTrue(swagger(true, HTTP, List.of(SWAGGER), "").isEmpty());
    }

    private Optional<String> swagger(boolean enabled, Capabilities capabilities, List<ResolvedDependency> dependencies,
            String context) {
        return JimmerDevUILinks.swaggerUiUrl(enabled, capabilities, dependencies, config, DEV, context);
    }

    private void configure(Map<String, String> values) {
        config = new SmallRyeConfigBuilder().addDefaultInterceptors().withValidateUnknown(false)
                .withMapping(VertxHttpBuildTimeConfig.class).withMapping(ManagementInterfaceBuildTimeConfig.class)
                .withDefaultValues(values).build();
        ConfigProviderResolver.instance().registerConfig(config, Thread.currentThread().getContextClassLoader());
    }
}
