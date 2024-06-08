package io.quarkiverse.jimmer.deployment;

import java.util.List;
import java.util.Optional;

import io.quarkus.deployment.IsDevelopment;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.devui.spi.page.CardPageBuildItem;
import io.quarkus.devui.spi.page.Page;

class JimmerDevUIProcessor {

    @BuildStep(onlyIf = IsDevelopment.class)
    public CardPageBuildItem createCard(List<RegistryBuildItem> registries) {
        CardPageBuildItem card = new CardPageBuildItem();

        Optional<String> schemaYaml = registries.stream().filter(r -> "OpenApiResource".equals(r.name()))
                .map(RegistryBuildItem::path)
                .findFirst();

        schemaYaml.ifPresent(s -> card.addPage(Page.externalPageBuilder("Schema yaml")
                .icon("font-awesome-solid:file-lines")
                .isYamlContent()
                .url(s)));
        return card;
    }
}
