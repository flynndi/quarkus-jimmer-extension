package io.quarkiverse.jimmer.deployment.devui;

import java.net.URI;
import java.util.Collection;
import java.util.Optional;

import org.eclipse.microprofile.config.Config;

import io.quarkiverse.jimmer.deployment.client.JimmerHttpPaths;
import io.quarkus.deployment.Capabilities;
import io.quarkus.deployment.Capability;
import io.quarkus.deployment.builditem.LaunchModeBuildItem;
import io.quarkus.maven.dependency.ResolvedDependency;

/** Resolves optional documentation links without depending on Swagger UI deployment classes or final routes. */
final class JimmerDevUILinks {

    private JimmerDevUILinks() {
    }

    static boolean hasSwaggerUi(Collection<ResolvedDependency> dependencies) {
        return dependencies.stream().anyMatch(dependency -> dependency.isRuntimeCp()
                && "io.quarkus".equals(dependency.getGroupId())
                && "quarkus-swagger-ui".equals(dependency.getArtifactId()));
    }

    static Optional<String> swaggerUiUrl(boolean jimmerEnabled, Capabilities capabilities,
            Collection<ResolvedDependency> dependencies, Config config, LaunchModeBuildItem launchMode,
            String devContextRoot) {
        if (!jimmerEnabled || capabilities.isMissing(Capability.VERTX_HTTP) || !hasSwaggerUi(dependencies)
                || !config.getOptionalValue("quarkus.swagger-ui.enabled", Boolean.class).orElse(true)) {
            return Optional.empty();
        }
        String path = config.getOptionalValue("quarkus.swagger-ui.path", String.class).orElse("swagger-ui");
        boolean management = config.getOptionalValue("quarkus.smallrye-openapi.management.enabled", Boolean.class)
                .orElse(true);
        // Only load HTTP implementation classes after verifying the optional extension is present.
        return Optional.of(withDevContext(JimmerHttpPaths.url(path, launchMode, management), devContextRoot));
    }

    static String withDevContext(String url, String devContextRoot) {
        // Management links already include their own origin and must not inherit the main server's context.
        return devContextRoot == null || devContextRoot.isBlank() || URI.create(url).isAbsolute()
                ? url
                : devContextRoot.replaceFirst("/+$", "") + url;
    }
}
