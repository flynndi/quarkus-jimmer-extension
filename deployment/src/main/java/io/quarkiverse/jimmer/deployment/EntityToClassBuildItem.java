package io.quarkiverse.jimmer.deployment;

import io.quarkus.builder.item.MultiBuildItem;

final class EntityToClassBuildItem extends MultiBuildItem {

    private final String entityClass;

    private final Class<?> clazz;

    public EntityToClassBuildItem(String entityClass, Class<?> clazz) {
        this.entityClass = entityClass;
        this.clazz = clazz;
    }

    public String getEntityClass() {
        return entityClass;
    }

    public Class<?> getClazz() {
        return clazz;
    }
}
