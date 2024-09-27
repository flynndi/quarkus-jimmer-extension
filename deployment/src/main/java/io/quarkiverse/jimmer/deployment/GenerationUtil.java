package io.quarkiverse.jimmer.deployment;

import java.util.*;

import org.jboss.jandex.*;

public final class GenerationUtil {

    private GenerationUtil() {
    }

    static List<DotName> extendedSpringDataRepos(ClassInfo repositoryToImplement, IndexView index) {
        return new ArrayList<>(repositoryToImplement.interfaceNames());
    }

    static Set<MethodInfo> interfaceMethods(Collection<DotName> interfaces, IndexView index) {
        Set<MethodInfo> result = new HashSet<>();
        for (DotName dotName : interfaces) {
            ClassInfo classInfo = index.getClassByName(dotName);
            result.addAll(classInfo.methods());
            List<DotName> extendedInterfaces = classInfo.interfaceNames();
            if (!extendedInterfaces.isEmpty()) {
                result.addAll(interfaceMethods(extendedInterfaces, index));
            }
        }
        return result;
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
