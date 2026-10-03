package io.quarkiverse.jimmer.deployment;

import io.quarkus.builder.item.MultiBuildItem;

/** Internal metadata for generating legacy interface-based repositories. */
public final class RepositoryMetadata extends MultiBuildItem {

    private final Class<?> domainType;

    private final Class<?> repositoryInterface;

    private final String dataSourceName;

    public RepositoryMetadata(Class<?> domainType, Class<?> repositoryInterface, String dataSourceName) {
        this.domainType = domainType;
        this.repositoryInterface = repositoryInterface;
        this.dataSourceName = dataSourceName;
    }

    public Class<?> getDomainType() {
        return domainType;
    }

    public Class<?> getRepositoryInterface() {
        return repositoryInterface;
    }

    public String getDataSourceName() {
        return dataSourceName;
    }
}
