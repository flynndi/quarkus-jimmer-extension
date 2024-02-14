package io.quarkiverse.jimmer.runtime.util;

import java.lang.annotation.Annotation;

import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.Instance;

import io.quarkiverse.jimmer.runtime.QuarkusJSqlClientContainer;
import io.quarkus.arc.Arc;
import io.quarkus.datasource.common.runtime.DataSourceUtil;

public final class QuarkusJSqlClientContainerUtil {

    private QuarkusJSqlClientContainerUtil() {
    }

    public static QuarkusJSqlClientContainer getQuarkusJSqlClientContainer(String dataSourceName) {
        return instantiateBeanOrClass(QuarkusJSqlClientContainer.class, getQuarkusJSqlClientContainerQualifier(dataSourceName));
    }

    public static Annotation getQuarkusJSqlClientContainerQualifier(String dataSourceName) {
        if (DataSourceUtil.isDefault(dataSourceName)) {
            return Default.Literal.INSTANCE;
        }
        return new io.quarkus.agroal.DataSource.DataSourceLiteral(dataSourceName);
    }

    public static <T> T instantiateBeanOrClass(Class<T> type, Annotation annotation) {
        Instance<T> instance = Arc.container().select(type, annotation);
        if (instance.isAmbiguous()) {
            throw new IllegalArgumentException("Multiple beans match the type: " + type);
        } else if (instance.isUnsatisfied()) {
            try {
                return type.getConstructor().newInstance();
            } catch (Exception e) {
                throw new IllegalStateException("Unable to instantiate the class: " + type);
            }
        } else {
            return instance.get();
        }
    }
}
