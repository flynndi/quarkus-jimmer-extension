package io.quarkiverse.jimmer.deployment;

import org.babyfish.jimmer.meta.ImmutableType;

import io.quarkus.builder.item.MultiBuildItem;

public final class EntityToImmutableTypeBuildItem extends MultiBuildItem {

    private final String entityClass;

    private final ImmutableType immutableType;

    public EntityToImmutableTypeBuildItem(String entityClass, ImmutableType immutableType) {
        this.entityClass = entityClass;
        this.immutableType = immutableType;
    }

    public String getEntityClass() {
        return entityClass;
    }

    public ImmutableType getImmutableType() {
        return immutableType;
    }
}
