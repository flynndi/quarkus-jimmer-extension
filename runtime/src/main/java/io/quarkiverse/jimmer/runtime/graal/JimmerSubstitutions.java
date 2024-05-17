package io.quarkiverse.jimmer.runtime.graal;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.function.BooleanSupplier;

import org.babyfish.jimmer.sql.dialect.UpdateJoin;
import org.babyfish.jimmer.sql.runtime.Reader;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

public class JimmerSubstitutions {

    @TargetClass(className = "org.babyfish.jimmer.sql.dialect.PostgresDialect", onlyWith = JimmerSubstitutions.IsPGAbsent.class)
    public static final class PostgresDialectSubstitutions {

        @Substitute
        public UpdateJoin getUpdateJoin() {
            return null;
        }

        @Substitute
        public String getSelectIdFromSequenceSql(String sequenceName) {
            return null;
        }

        @Substitute
        public String getOverrideIdentityIdSql() {
            return null;
        }

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
        public boolean isIgnoreCaseLikeSupported() {
            return true;
        }

        @Substitute
        public boolean isArraySupported() {
            return true;
        }

        @Substitute
        public String arrayTypeSuffix() {
            return null;
        }

        @Substitute
        public String sqlType(Class<?> elementType) {
            return null;
        }

        @Substitute
        public <T> T[] getArray(ResultSet rs, int col, Class<T[]> arrayType) throws SQLException {
            return null;
        }

        @Substitute
        public Reader<?> unknownReader(Class<?> sqlType) {
            return null;
        }

        @Substitute
        public String transCacheOperatorTableDDL() {
            return null;
        }
    }

    @TargetClass(className = "org.babyfish.jimmer.sql.dialect.PostgresDialect", onlyWith = JimmerSubstitutions.IsPGAbsent.class)
    @Delete
    public static final class PostgresDialectDelete {
    }

    public static final class IsPGAbsent implements BooleanSupplier {

        private boolean pgAbsent;

        public IsPGAbsent() {
            try {
                Class.forName("org.postgresql.Driver");
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
