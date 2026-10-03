package io.quarkiverse.jimmer.deployment;

import org.eclipse.microprofile.config.ConfigProvider;

import io.quarkus.deployment.builditem.LaunchModeBuildItem;
import io.quarkus.vertx.http.deployment.NonApplicationRootPathBuildItem;
import io.quarkus.vertx.http.runtime.VertxHttpBuildTimeConfig;
import io.quarkus.vertx.http.runtime.management.ManagementInterfaceBuildTimeConfig;
import io.smallrye.config.SmallRyeConfig;

/** Loaded only after the HTTP capability has been checked, never as a build-step signature. */
final class JimmerHttpPaths {

    private JimmerHttpPaths() {
    }

    static String managementUrl(String path, LaunchModeBuildItem launchMode) {
        SmallRyeConfig config = ConfigProvider.getConfig().unwrap(SmallRyeConfig.class);
        VertxHttpBuildTimeConfig http = config.getConfigMapping(VertxHttpBuildTimeConfig.class);
        ManagementInterfaceBuildTimeConfig management = config.getConfigMapping(ManagementInterfaceBuildTimeConfig.class);
        NonApplicationRootPathBuildItem paths = new NonApplicationRootPathBuildItem(http.rootPath(),
                http.nonApplicationRootPath(), management.enabled() ? management.rootPath() : null);
        return paths.resolveManagementPath(path, management, launchMode);
    }
}
