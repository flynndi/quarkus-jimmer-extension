package io.quarkiverse.jimmer.deployment;

import java.util.Map;

import org.jboss.jandex.DotName;

import io.quarkus.builder.item.MultiBuildItem;

final class RepositoryBuildItem extends MultiBuildItem {

    private final DotName repositoryName;

    private final String dataSourceName;

    private final Map.Entry<DotName, DotName> dotIdDotNameEntry;

    public RepositoryBuildItem(DotName repositoryName, String dataSourceName, Map.Entry<DotName, DotName> dotIdDotNameEntry) {
        this.repositoryName = repositoryName;
        this.dataSourceName = dataSourceName;
        this.dotIdDotNameEntry = dotIdDotNameEntry;
    }

    public DotName getRepositoryName() {
        return repositoryName;
    }

    public String getDataSourceName() {
        return dataSourceName;
    }

    public Map.Entry<DotName, DotName> getDotIdDotNameEntry() {
        return dotIdDotNameEntry;
    }
}
