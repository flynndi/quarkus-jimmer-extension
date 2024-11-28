package io.quarkiverse.jimmer.runtime.graal;

import java.sql.SQLException;
import java.util.function.BooleanSupplier;

import org.babyfish.jimmer.sql.runtime.Reader;
import org.jetbrains.annotations.Nullable;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

/**
 * Have no effect
 */
public class JimmerSubstitutions {

    @TargetClass(className = "org.babyfish.jimmer.sql.dialect.PostgresDialect", onlyWith = JimmerSubstitutions.IsPGAbsent.class)
    public static final class PostgresDialectSubstitutions {

        @Substitute
        public Class<?> getJsonBaseType() {
            return null;
        }

        @Substitute
        public Object jsonToBaseValue(@Nullable String json) throws SQLException {
            return null;
        }

        @Substitute
        public @Nullable String baseValueToJson(@Nullable Object baseValue) throws SQLException {
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
