package io.quarkiverse.jimmer.deployment;

import java.util.List;
import java.util.Optional;

import io.quarkus.deployment.IsDevelopment;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.devui.spi.page.CardPageBuildItem;
import io.quarkus.devui.spi.page.Page;

final class JimmerDevUIProcessor {

    @BuildStep(onlyIf = IsDevelopment.class)
    CardPageBuildItem createCard(List<RegistryBuildItem> registries) {
        CardPageBuildItem card = new CardPageBuildItem();

        Optional<String> schemaYaml = registries.stream().filter(r -> "OpenApiResource".equals(r.name()))
                .map(RegistryBuildItem::path)
                .findFirst();

        Optional<String> openApiUiPath = registries.stream().filter(r -> "OpenApiUiResource".equals(r.name()))
                .map(RegistryBuildItem::path)
                .findFirst();

        schemaYaml.ifPresent(s -> card.addPage(Page.externalPageBuilder("Schema yaml")
                .icon("font-awesome-solid:file-lines")
                .isYamlContent()
                .url(s)));

        openApiUiPath.ifPresent(s -> card.addPage(Page.externalPageBuilder("Jimmer swagger")
                .url(s, s)
                .isHtmlContent()
                .icon("font-awesome-solid:signs-post")));
        return card;
    }
}
