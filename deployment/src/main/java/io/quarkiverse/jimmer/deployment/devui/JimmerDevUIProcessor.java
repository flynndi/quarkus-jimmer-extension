package io.quarkiverse.jimmer.deployment.devui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import org.eclipse.microprofile.config.ConfigProvider;

import io.quarkiverse.jimmer.deployment.cache.JimmerCacheRetryBuildItem;
import io.quarkiverse.jimmer.deployment.client.JimmerClientAvailabilityBuildItem;
import io.quarkiverse.jimmer.deployment.client.JimmerClientEndpointBuildItem;
import io.quarkiverse.jimmer.deployment.microservice.JimmerMicroserviceSupport;
import io.quarkiverse.jimmer.deployment.repo.EntityToClassBuildItem;
import io.quarkiverse.jimmer.deployment.repository.RepositoryMetadata;
import io.quarkiverse.jimmer.runtime.cfg.JimmerBuildTimeConfig;
import io.quarkiverse.jimmer.runtime.devui.JimmerDevUIService;
import io.quarkus.agroal.spi.JdbcDataSourceBuildItem;
import io.quarkus.deployment.Capabilities;
import io.quarkus.deployment.Capability;
import io.quarkus.deployment.IsDevelopment;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.LaunchModeBuildItem;
import io.quarkus.deployment.pkg.builditem.CurateOutcomeBuildItem;
import io.quarkus.devui.spi.DevContextBuildItem;
import io.quarkus.devui.spi.JsonRPCProvidersBuildItem;
import io.quarkus.devui.spi.page.CardPageBuildItem;
import io.quarkus.devui.spi.page.Page;

final class JimmerDevUIProcessor {

    @BuildStep
    JsonRPCProvidersBuildItem registerRuntimeService() {
        // Dev UI registers this unscoped class as a bean only in local development. Keep the item available in
        // every mode so Quarkus can also recognize JSON-RPC entry points during execution-model validation.
        return new JsonRPCProvidersBuildItem(JimmerDevUIService.class);
    }

    @BuildStep(onlyIf = IsDevelopment.class)
    CardPageBuildItem createCard(JimmerBuildTimeConfig config, CombinedIndexBuildItem index,
            List<RepositoryMetadata> legacyRepositories, List<EntityToClassBuildItem> repositories,
            List<JdbcDataSourceBuildItem> dataSources, List<JimmerClientEndpointBuildItem> registries,
            CurateOutcomeBuildItem application, Capabilities capabilities, LaunchModeBuildItem launchMode,
            Optional<DevContextBuildItem> devContext, JimmerCacheRetryBuildItem cacheRetry,
            JimmerClientAvailabilityBuildItem clientAvailability) {
        Map<String, String> repositoryEntities = new TreeMap<>();
        repositories
                .forEach(repository -> repositoryEntities.put(repository.getEntityClass(), repository.getClazz().getName()));
        Map<String, Object> model = JimmerDevUIModel.create(index.getIndex(), legacyRepositories, repositoryEntities);
        String contextRoot = devContext.map(DevContextBuildItem::getDevUIContextRoot).orElse("");
        var dependencies = application.getApplicationModel().getDependencies();
        Optional<String> swaggerUi = JimmerDevUILinks.swaggerUiUrl(config.enable(), capabilities, dependencies,
                ConfigProvider.getConfig(), launchMode, contextRoot);
        boolean caffeineInstalled = dependencies.stream().anyMatch(dependency -> dependency.isRuntimeCp()
                && "io.quarkus".equals(dependency.getGroupId())
                && "quarkus-caffeine".equals(dependency.getArtifactId()));
        Map<String, Object> jimmer = Map.of(
                "overview", overview(config, dataSources, capabilities, JimmerDevUILinks.hasSwaggerUi(dependencies),
                        swaggerUi.isPresent(), caffeineInstalled, cacheRetry, clientAvailability, model.get("repositories")),
                "entities", model.get("entities"));
        return card(config.enable(), jimmer, registries, swaggerUi, contextRoot);
    }

    static CardPageBuildItem card(boolean enabled, Map<String, Object> jimmer, List<JimmerClientEndpointBuildItem> registries,
            Optional<String> swaggerUi, String contextRoot) {
        CardPageBuildItem card = new CardPageBuildItem();
        card.addBuildTimeData("jimmer", jimmer);
        card.addPage(Page.webComponentPageBuilder().title("Overview").componentLink("qwc-jimmer-overview.js")
                .icon("font-awesome-solid:circle-info"));
        card.addPage(Page.webComponentPageBuilder().title("Model").componentLink("qwc-jimmer-model.js")
                .icon("font-awesome-solid:diagram-project"));
        card.addPage(Page.webComponentPageBuilder().title("Runtime").componentLink("qwc-jimmer-runtime.js")
                .icon("font-awesome-solid:circle-play"));
        if (!enabled) {
            return card;
        }

        registryPath(registries, "OpenApiResource").ifPresent(path -> card.addPage(Page.externalPageBuilder("Schema yaml")
                .icon("font-awesome-solid:file-lines")
                .isYamlContent()
                .url(JimmerDevUILinks.withDevContext(path, contextRoot))));
        registryPath(registries, "TypeScriptResource").ifPresent(path -> card.addPage(Page.externalPageBuilder("TypeScript")
                .icon("font-awesome-solid:download")
                .url(JimmerDevUILinks.withDevContext(path, contextRoot))
                .doNotEmbed()));
        swaggerUi.ifPresent(url -> card.addPage(Page.externalPageBuilder("Swagger UI")
                .icon("font-awesome-solid:signs-post").isHtmlContent()
                .url(url.replaceFirst("/+$", "") + "/index.html?embed=true", url)));
        return card;
    }

    private static Optional<String> registryPath(List<JimmerClientEndpointBuildItem> registries, String name) {
        return registries.stream().filter(registry -> name.equals(registry.name())).map(JimmerClientEndpointBuildItem::path)
                .findFirst();
    }

    static Map<String, Object> overview(JimmerBuildTimeConfig config, List<JdbcDataSourceBuildItem> dataSources,
            Capabilities capabilities, boolean swaggerInstalled, boolean swaggerEnabled, boolean caffeineInstalled,
            JimmerCacheRetryBuildItem cacheRetry, JimmerClientAvailabilityBuildItem clientAvailability,
            Object repositories) {
        boolean http = capabilities.isPresent(Capability.VERTX_HTTP);
        List<Map<String, String>> features = new ArrayList<>();
        features.add(Map.of("name", "Client generation", "status", !config.enable() ? "disabled"
                : clientAvailability.generatorAvailable() ? "available" : "unavailable",
                "detail",
                "Optional org.babyfish.jimmer:jimmer-client dependency. Programmatic generation does not require HTTP endpoints."));
        List<String> missingClientDependencies = new ArrayList<>();
        if (!clientAvailability.generatorAvailable()) {
            missingClientDependencies.add("org.babyfish.jimmer:jimmer-client");
        }
        if (!clientAvailability.httpAvailable()) {
            missingClientDependencies.add("an HTTP extension");
        }
        if (!clientAvailability.metadataApisAvailable()) {
            missingClientDependencies.add("JAX-RS and Quarkus REST metadata APIs");
        }
        String clientDependencyDetail = missingClientDependencies.isEmpty() ? ""
                : "; missing " + String.join(", ", missingClientDependencies);
        features.add(feature("OpenAPI", config.enable(), config.client().openapi().path().isPresent(),
                clientAvailability.endpointsAvailable(), "quarkus.jimmer.client.openapi.path" + clientDependencyDetail));
        features.add(feature("TypeScript", config.enable(), config.client().ts().path().isPresent(),
                clientAvailability.endpointsAvailable(), "quarkus.jimmer.client.ts.path" + clientDependencyDetail));
        features.add(feature("REST error translation", config.enable(),
                config.errorTranslator().filter(translator -> !translator.disabled()).isPresent(),
                capabilities.isPresent(Capability.REST) && capabilities.isPresent(Capability.REST_JACKSON),
                "quarkus.jimmer.error-translator.disabled=false; requires quarkus-rest-jackson"));
        features.add(Map.of("name", "Default HTTP exchange", "status", !config.enable() ? "disabled"
                : !JimmerMicroserviceSupport.httpExchangeAvailable(capabilities) ? "unavailable"
                        : JimmerMicroserviceSupport.httpExchangeEnabled(config, capabilities) ? "available" : "disabled",
                "detail",
                "Default outbound adapter when no application MicroServiceExchange is provided. Availability does not identify the CDI bean in use."));
        features.add(feature("Microservice exporter", config.enable(),
                JimmerMicroserviceSupport.exporterEnabled(config, capabilities),
                JimmerMicroserviceSupport.exporterAvailable(capabilities),
                "Inbound routes; requires an HTTP extension and Jackson. Independent of the outbound exchange."));
        features.add(Map.of("name", "Cache retry task", "status", !config.enable() ? "disabled"
                : !cacheRetry.schedulerAvailable() ? "unavailable"
                        : cacheRetry.retryJobRegistered() ? "registered" : "disabled",
                "detail", "Build-time task registration only. Runtime shows the retry interval and scheduler configuration."));
        features.add(Map.of("name", "Redis cache integration", "status", !config.enable() ? "disabled"
                : capabilities.isPresent(Capability.REDIS_CLIENT) ? "available" : "unavailable",
                "detail", "Availability does not create caches. Configure a CacheFactory or application cache binders."));
        features.add(Map.of("name", "Caffeine cache integration", "status", !config.enable() ? "disabled"
                : caffeineInstalled ? "available" : "unavailable",
                "detail", "Availability does not create caches. Configure a CacheFactory or application cache binders."));
        features.add(feature("Swagger UI", config.enable(), swaggerEnabled, http && swaggerInstalled,
                "Application-provided quarkus-swagger-ui; uses native quarkus.swagger-ui configuration"));
        return Map.of("enabled", config.enable(), "language", config.language(),
                "microServiceName", config.microServiceName().orElse(""),
                "dataSources", dataSources.stream().sorted(Comparator.comparing(JdbcDataSourceBuildItem::getName))
                        .map(source -> Map.of("name", source.getName(), "dbKind", source.getDbKind())).toList(),
                "features", features, "repositories", repositories,
                "cacheRetry", Map.of("schedulerAvailable", cacheRetry.schedulerAvailable(),
                        "retryJobRegistered", cacheRetry.retryJobRegistered()));
    }

    private static Map<String, String> feature(String name, boolean enabled, boolean configured, boolean available,
            String detail) {
        String status = !enabled ? "disabled" : !available ? "unavailable" : configured ? "enabled" : "disabled";
        return Map.of("name", name, "status", status, "detail", detail);
    }
}
