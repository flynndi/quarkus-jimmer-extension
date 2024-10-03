package io.quarkiverse.jimmer.deployment;

import io.quarkus.builder.item.MultiBuildItem;

final class SqlClientBuildItem extends MultiBuildItem {

    private final String datasourceName;

    public SqlClientBuildItem(String datasourceName) {
        this.datasourceName = datasourceName;
    }

    public String getDatasourceName() {
        return datasourceName;
    }
}
