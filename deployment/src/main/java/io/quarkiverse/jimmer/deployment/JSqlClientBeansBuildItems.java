package io.quarkiverse.jimmer.deployment;

import io.quarkus.builder.item.MultiBuildItem;

public final class JSqlClientBeansBuildItems extends MultiBuildItem {

    private final String dataSourceName;

    public JSqlClientBeansBuildItems(String dataSourceName) {
        this.dataSourceName = dataSourceName;
    }

    public String getDataSourceName() {
        return dataSourceName;
    }
}
