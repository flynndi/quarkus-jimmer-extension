package io.quarkiverse.jimmer.deployment;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import org.eclipse.microprofile.config.ConfigProvider;

import io.quarkiverse.jimmer.deployment.devui.JimmerDevUIModel;
import io.quarkiverse.jimmer.runtime.cfg.JimmerBuildTimeConfig;
import io.quarkus.agroal.spi.JdbcDataSourceBuildItem;
import io.quarkus.deployment.Capabilities;
import io.quarkus.deployment.Capability;
import io.quarkus.deployment.IsDevelopment;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.LaunchModeBuildItem;
import io.quarkus.deployment.pkg.builditem.CurateOutcomeBuildItem;
import io.quarkus.devui.spi.DevContextBuildItem;
import io.quarkus.devui.spi.page.CardPageBuildItem;
import io.quarkus.devui.spi.page.Page;

final class JimmerDevUIProcessor {

    @BuildStep(onlyIf = IsDevelopment.class)
    CardPageBuildItem createCard(JimmerBuildTimeConfig config, CombinedIndexBuildItem index,
            List<RepositoryMetadata> legacyRepositories, List<EntityToClassBuildItem> repositories,
            List<JdbcDataSourceBuildItem> dataSources, List<RegistryBuildItem> registries,
            CurateOutcomeBuildItem application, Capabilities capabilities, LaunchModeBuildItem launchMode,
            Optional<DevContextBuildItem> devContext) {
        Map<String, String> repositoryEntities = new TreeMap<>();
        repositories
                .forEach(repository -> repositoryEntities.put(repository.getEntityClass(), repository.getClazz().getName()));
        Map<String, Object> model = JimmerDevUIModel.create(index.getIndex(), legacyRepositories, repositoryEntities);
        String contextRoot = devContext.map(DevContextBuildItem::getDevUIContextRoot).orElse("");
        var dependencies = application.getApplicationModel().getDependencies();
        Optional<String> swaggerUi = JimmerDevUILinks.swaggerUiUrl(config.enable(), capabilities, dependencies,
                ConfigProvider.getConfig(), launchMode, contextRoot);
        Map<String, Object> jimmer = Map.of(
                "overview", overview(config, dataSources, capabilities, JimmerDevUILinks.hasSwaggerUi(dependencies),
                        swaggerUi.isPresent(), model.get("repositories")),
                "entities", model.get("entities"));
        return card(config.enable(), jimmer, registries, swaggerUi, contextRoot);
    }

    static CardPageBuildItem card(boolean enabled, Map<String, Object> jimmer, List<RegistryBuildItem> registries,
            Optional<String> swaggerUi, String contextRoot) {
        CardPageBuildItem card = new CardPageBuildItem();
        card.addBuildTimeData("jimmer", jimmer);
        card.addPage(Page.webComponentPageBuilder().title("Overview").componentLink("qwc-jimmer-overview.js")
                .icon("font-awesome-solid:circle-info"));
        card.addPage(Page.webComponentPageBuilder().title("Model").componentLink("qwc-jimmer-model.js")
                .icon("font-awesome-solid:diagram-project"));
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

    private static Optional<String> registryPath(List<RegistryBuildItem> registries, String name) {
        return registries.stream().filter(registry -> name.equals(registry.name())).map(RegistryBuildItem::path).findFirst();
    }

    static Map<String, Object> overview(JimmerBuildTimeConfig config, List<JdbcDataSourceBuildItem> dataSources,
            Capabilities capabilities, boolean swaggerInstalled, boolean swaggerEnabled, Object repositories) {
        boolean http = capabilities.isPresent(Capability.VERTX_HTTP);
        List<Map<String, String>> features = new ArrayList<>();
        features.add(feature("OpenAPI", config.enable(), config.client().openapi().path().isPresent(), http,
                "quarkus.jimmer.client.openapi.path; requires an HTTP extension"));
        features.add(feature("TypeScript", config.enable(), config.client().ts().path().isPresent(), http,
                "quarkus.jimmer.client.ts.path; requires an HTTP extension"));
        features.add(feature("REST error translation", config.enable(),
                config.errorTranslator().filter(translator -> !translator.disabled()).isPresent(),
                capabilities.isPresent(Capability.REST) && capabilities.isPresent(Capability.REST_JACKSON),
                "quarkus.jimmer.error-translator.disabled=false; requires quarkus-rest-jackson"));
        features.add(feature("Microservice bridge", config.enable(),
                config.microServiceName().filter(name -> !name.isEmpty()).isPresent(),
                http && capabilities.isPresent(Capability.REST_CLIENT_REACTIVE),
                "quarkus.jimmer.micro-service-name; requires HTTP and quarkus-rest-client"));
        features.add(feature("Swagger UI", config.enable(), swaggerEnabled, http && swaggerInstalled,
                "Application-provided quarkus-swagger-ui; uses native quarkus.swagger-ui configuration"));
        return Map.of("enabled", config.enable(), "language", config.language(),
                "microServiceName", config.microServiceName().orElse(""),
                "dataSources", dataSources.stream().sorted(Comparator.comparing(JdbcDataSourceBuildItem::getName))
                        .map(source -> Map.of("name", source.getName(), "dbKind", source.getDbKind())).toList(),
                "features", features, "repositories", repositories);
    }

    private static Map<String, String> feature(String name, boolean enabled, boolean configured, boolean available,
            String detail) {
        String status = !enabled ? "disabled" : !available ? "unavailable" : configured ? "enabled" : "disabled";
        return Map.of("name", name, "status", status, "detail", detail);
    }
}
