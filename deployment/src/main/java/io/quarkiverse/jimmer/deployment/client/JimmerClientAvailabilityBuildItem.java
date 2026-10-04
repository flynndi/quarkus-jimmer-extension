package io.quarkiverse.jimmer.deployment.client;

import io.quarkus.builder.item.SimpleBuildItem;

/** Runtime dependencies available to client generation and its optional HTTP endpoints. */
public final class JimmerClientAvailabilityBuildItem extends SimpleBuildItem {

    private final boolean generatorAvailable;
    private final boolean httpAvailable;
    private final boolean metadataApisAvailable;

    public JimmerClientAvailabilityBuildItem(boolean generatorAvailable, boolean httpAvailable,
            boolean metadataApisAvailable) {
        this.generatorAvailable = generatorAvailable;
        this.httpAvailable = httpAvailable;
        this.metadataApisAvailable = metadataApisAvailable;
    }

    public boolean generatorAvailable() {
        return generatorAvailable;
    }

    public boolean httpAvailable() {
        return httpAvailable;
    }

    public boolean metadataApisAvailable() {
        return metadataApisAvailable;
    }

    public boolean endpointsAvailable() {
        return generatorAvailable && httpAvailable && metadataApisAvailable;
    }
}
