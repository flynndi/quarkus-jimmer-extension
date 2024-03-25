package io.quarkiverse.jimmer.deployment;

import io.quarkus.builder.item.MultiBuildItem;
import kotlin.reflect.KClass;

final class EntityToKClassBuildItem extends MultiBuildItem {

    private final String entityClass;

    private final KClass<?> kClass;

    public EntityToKClassBuildItem(String entityClass, KClass<?> kClass) {
        this.entityClass = entityClass;
        this.kClass = kClass;
    }

    public String getEntityClass() {
        return entityClass;
    }

    public KClass<?> getkClass() {
        return kClass;
    }
}
