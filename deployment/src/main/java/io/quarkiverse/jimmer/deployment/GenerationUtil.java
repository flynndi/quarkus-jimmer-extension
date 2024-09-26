package io.quarkiverse.jimmer.deployment;

import java.util.*;

import org.jboss.jandex.*;

public final class GenerationUtil {

    private GenerationUtil() {
    }

    static List<DotName> extendedSpringDataRepos(ClassInfo repositoryToImplement, IndexView index) {
        return new ArrayList<>(repositoryToImplement.interfaceNames());
    }

    private static boolean isMethodDeclaredInNamedQuery(ClassInfo entityClassInfo, String methodName,
            AnnotationInstance namedQuery) {
        AnnotationValue namedQueryName = namedQuery.value("name");
        if (namedQueryName == null) {
            return false;
        }

        return String.format("%s.%s", entityClassInfo.name().withoutPackagePrefix(), methodName).equals(namedQueryName.value());
    }

}
