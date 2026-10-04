package io.quarkiverse.jimmer.deployment.client;

import io.quarkus.builder.item.MultiBuildItem;

public final class JimmerClientEndpointBuildItem extends MultiBuildItem {

    private final String name;

    private final String path;

    public JimmerClientEndpointBuildItem(String name, String path) {
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
