package io.quarkiverse.jimmer.deployment;

import org.jboss.jandex.DotName;

import io.quarkus.builder.item.MultiBuildItem;

public final class RepositoryBuildItem extends MultiBuildItem {
    private final DotName repositoryName;
    private final String dataSourceName;

    public RepositoryBuildItem(DotName repositoryName, String dataSourceName) {
        this.repositoryName = repositoryName;
        this.dataSourceName = dataSourceName;
    }

    public DotName getRepositoryName() {
        return repositoryName;
    }

    public String getDataSourceName() {
        return dataSourceName;
    }
}
