package io.quarkiverse.jimmer.deployment;

import java.util.Map;

import org.jboss.jandex.DotName;

import io.quarkus.builder.item.SimpleBuildItem;

public final class JimmerBeanNameToDotNameBuildItem extends SimpleBuildItem {

    private final Map<DotName, Boolean> map;

    public JimmerBeanNameToDotNameBuildItem(Map<DotName, Boolean> map) {
        this.map = map;
    }

    public Map<DotName, Boolean> getMap() {
        return map;
    }
}
