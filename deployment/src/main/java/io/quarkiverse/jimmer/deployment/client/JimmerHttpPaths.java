package io.quarkiverse.jimmer.deployment.client;

import org.eclipse.microprofile.config.ConfigProvider;

import io.quarkus.deployment.builditem.LaunchModeBuildItem;
import io.quarkus.vertx.http.deployment.NonApplicationRootPathBuildItem;
import io.quarkus.vertx.http.runtime.VertxHttpBuildTimeConfig;
import io.quarkus.vertx.http.runtime.management.ManagementInterfaceBuildTimeConfig;
import io.smallrye.config.SmallRyeConfig;

/** Loaded only after the HTTP capability has been checked, never as a build-step signature. */
public final class JimmerHttpPaths {

    private JimmerHttpPaths() {
    }

    static String managementUrl(String path, LaunchModeBuildItem launchMode) {
        return url(path, launchMode, true);
    }

    public static String url(String path, LaunchModeBuildItem launchMode, boolean managementRoute) {
        SmallRyeConfig config = ConfigProvider.getConfig().unwrap(SmallRyeConfig.class);
        VertxHttpBuildTimeConfig http = config.getConfigMapping(VertxHttpBuildTimeConfig.class);
        ManagementInterfaceBuildTimeConfig management = config.getConfigMapping(ManagementInterfaceBuildTimeConfig.class);
        NonApplicationRootPathBuildItem paths = new NonApplicationRootPathBuildItem(http.rootPath(),
                http.nonApplicationRootPath(), management.enabled() ? management.rootPath() : null);
        // A framework route that opts out of management stays under the HTTP non-application root.
        return managementRoute ? paths.resolveManagementPath(path, management, launchMode) : paths.resolvePath(path);
    }
}
