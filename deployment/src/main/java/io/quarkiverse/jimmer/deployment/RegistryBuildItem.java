package io.quarkiverse.jimmer.deployment;

import io.quarkus.builder.item.MultiBuildItem;

final class RegistryBuildItem extends MultiBuildItem {

    private final String name;

    private final String path;

    public RegistryBuildItem(String name, String path) {
        this.name = name;
        this.path = path;
    }

    public String name() {
        return name;
    }

    public String path() {
        return path;
    }
}
