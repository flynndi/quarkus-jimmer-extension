package io.quarkiverse.jimmer.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.quarkiverse.jimmer.runtime.cfg.JimmerBuildTimeConfig;
import io.quarkus.agroal.spi.JdbcDataSourceBuildItem;
import io.quarkus.deployment.Capabilities;
import io.quarkus.devui.spi.page.Page;
import io.smallrye.config.SmallRyeConfigBuilder;

class JimmerDevUIProcessorTest {

    @Test
    void registersBuildTimePagesAndOnlyAvailableDocumentationLinks() {
        Map<String, Object> model = Map.of("overview", Map.of("enabled", true), "entities", List.of());
        var card = JimmerDevUIProcessor.card(true, model, List.of(
                new RegistryBuildItem("OpenApiResource", "/jimmer/openapi.yml"),
                new RegistryBuildItem("TypeScriptResource", "http://localhost:9191/jimmer/ts.zip")),
                Optional.of("/proxy/native/swagger/"), "/proxy");
        List<Page> pages = card.getPages().stream().map(builder -> builder.build()).toList();
        assertEquals(List.of("Overview", "Model", "Schema yaml", "TypeScript", "Swagger UI"),
                pages.stream().map(Page::getTitle).toList());
        assertSame(model, card.getBuildTimeData().get("jimmer").getContent());
        assertEquals("qwc-jimmer-overview.js", pages.get(0).getComponentLink());
        assertEquals("qwc-jimmer-model.js", pages.get(1).getComponentLink());
        assertEquals("/proxy/jimmer/openapi.yml", pages.get(2).getMetadata().get("externalUrl"));
        assertEquals("http://localhost:9191/jimmer/ts.zip", pages.get(3).getMetadata().get("externalUrl"));
        assertFalse(pages.get(3).isEmbed());
        assertEquals("/proxy/native/swagger/index.html?embed=true", pages.get(4).getMetadata().get("externalUrl"));
    }

    @Test
    void disabledExtensionKeepsOverviewWithoutDocumentationLinks() {
        var card = JimmerDevUIProcessor.card(false, Map.of("overview", Map.of("enabled", false)),
                List.of(new RegistryBuildItem("OpenApiResource", "/not-registered.yml")),
                Optional.of("/not-registered-ui"), "");
        assertEquals(List.of("Overview", "Model"),
                card.getPages().stream().map(builder -> builder.build().getTitle()).toList());
    }

    @Test
    @SuppressWarnings("unchecked")
    void overviewReportsOnlyRegisteredDatasourceMetadataAndFeatureAvailability() {
        JimmerBuildTimeConfig config = new SmallRyeConfigBuilder().addDefaultInterceptors()
                .withMapping(JimmerBuildTimeConfig.class).build().getConfigMapping(JimmerBuildTimeConfig.class);
        List<Map<String, String>> repositories = List.of(Map.of("name", "BookRepository"));
        var overview = JimmerDevUIProcessor.overview(config,
                List.of(new JdbcDataSourceBuildItem("books", "postgresql", Optional.empty(), true, false, false)),
                new Capabilities(Set.of()), false, false, repositories);
        assertEquals(List.of(Map.of("name", "books", "dbKind", "postgresql")), overview.get("dataSources"));
        assertSame(repositories, overview.get("repositories"));
        List<Map<String, String>> features = (List<Map<String, String>>) overview.get("features");
        assertEquals(Set.of("unavailable"), features.stream().map(feature -> feature.get("status"))
                .collect(java.util.stream.Collectors.toSet()));
    }
}
