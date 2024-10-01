package io.quarkiverse.jimmer.deployment;

import java.util.Map;
import java.util.Set;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.MethodInfo;

import io.quarkus.builder.item.MultiBuildItem;

public final class SomeInfos extends MultiBuildItem {

    private final ClassInfo repositoryToImplement;

    private final Set<MethodInfo> methodInfos;

    private final Map.Entry<DotName, DotName> extraTypesResult;

    public SomeInfos(ClassInfo repositoryToImplement, Set<MethodInfo> methodInfos,
            Map.Entry<DotName, DotName> extraTypesResult) {
        this.repositoryToImplement = repositoryToImplement;
        this.methodInfos = methodInfos;
        this.extraTypesResult = extraTypesResult;
    }

    public ClassInfo getRepositoryToImplement() {
        return repositoryToImplement;
    }

    public Set<MethodInfo> getMethodInfos() {
        return methodInfos;
    }

    public Map.Entry<DotName, DotName> getExtraTypesResult() {
        return extraTypesResult;
    }
}
