package io.quarkiverse.jimmer.runtime.graal;

import java.util.function.BooleanSupplier;

import org.babyfish.jimmer.sql.runtime.Reader;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

public class JimmerSubstitutions {

    @TargetClass(className = "org.babyfish.jimmer.sql.dialect.PostgresDialect", onlyWith = JimmerSubstitutions.IsPGAbsent.class)
    public static final class PostgresDialectSubstitutions {

        @Substitute
        public Class<?> getJsonBaseType() {
            return null;
        }

        @Substitute
        public Object jsonToBaseValue(Object json, ObjectMapper objectMapper) throws Exception {
            return null;
        }

        @Substitute
        public Object baseValueToJson(Object baseValue, JavaType javaType, ObjectMapper objectMapper) throws Exception {
            return null;
        }

        @Substitute
        public Reader<?> unknownReader(Class<?> sqlType) {
            return null;
        }
    }

    public static final class IsPGAbsent implements BooleanSupplier {

        private boolean pgAbsent;

        public IsPGAbsent() {
            try {
                Class.forName("org.postgresql.Driver", true, Thread.currentThread().getContextClassLoader());
                pgAbsent = false;
            } catch (ClassNotFoundException e) {
                pgAbsent = true;
            }
        }

        @Override
        public boolean getAsBoolean() {
            return pgAbsent;
        }
    }
}
