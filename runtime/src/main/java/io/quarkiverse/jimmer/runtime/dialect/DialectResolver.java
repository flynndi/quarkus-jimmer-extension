package io.quarkiverse.jimmer.runtime.dialect;

import java.lang.reflect.InvocationTargetException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.babyfish.jimmer.sql.dialect.Dialect;
import org.babyfish.jimmer.sql.dialect.H2Dialect;
import org.babyfish.jimmer.sql.dialect.MySql5Dialect;
import org.babyfish.jimmer.sql.dialect.MySqlDialect;
import org.babyfish.jimmer.sql.dialect.OracleDialect;
import org.babyfish.jimmer.sql.dialect.PostgresDialect;
import org.babyfish.jimmer.sql.dialect.SQLiteDialect;
import org.babyfish.jimmer.sql.dialect.SqlServerDialect;
import org.babyfish.jimmer.sql.dialect.TiDBDialect;
import org.jetbrains.annotations.Nullable;

import io.quarkus.datasource.common.runtime.DataSourceUtil;
import io.quarkus.datasource.common.runtime.DatabaseKind;

/**
 * Centralizes Jimmer dialect resolution across Quarkus {@link DatabaseKind}, JDBC metadata, and explicit configuration.
 * <p>
 * Keep this class free of configuration or database access during class initialization so native-image analysis can
 * constant-fold optional driver checks and prune unreachable dialect branches.
 */
public final class DialectResolver {

    private static final String POSTGRES_DIALECT_CLASS = "org.babyfish.jimmer.sql.dialect.PostgresDialect";
    private static final String H2_DIALECT_CLASS = "org.babyfish.jimmer.sql.dialect.H2Dialect";

    private static final String POSTGRES_DRIVER_CLASS = "org.postgresql.util.PGobject";
    private static final String H2_DRIVER_CLASS = "org.h2.value.ValueJson";

    private static final boolean POSTGRES_AVAILABLE = isDriverPresent(POSTGRES_DRIVER_CLASS);
    private static final boolean H2_AVAILABLE = isDriverPresent(H2_DRIVER_CLASS);

    private static volatile Map<String, Dialect> dataSourceDialects = Map.of();

    private DialectResolver() {
    }

    public static void registerDataSources(Map<String, String> dbKinds, Map<String, String> dbVersions) {
        if (dbKinds.isEmpty()) {
            dataSourceDialects = Map.of();
            return;
        }
        Map<String, Dialect> resolved = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : dbKinds.entrySet()) {
            String name = entry.getKey();
            Dialect dialect = resolve(entry.getValue(), dbVersions.get(name), false);
            if (dialect != null) {
                resolved.put(name, dialect);
            }
        }
        dataSourceDialects = Map.copyOf(resolved);
    }

    @Nullable
    public static Dialect forDataSource(String dataSourceName) {
        return dataSourceDialects.get(dataSourceName);
    }

    @Nullable
    public static Dialect fromDatabaseKind(@Nullable String dbKind, @Nullable String dbVersion) {
        return resolve(dbKind, dbVersion, false);
    }

    @Nullable
    public static Dialect fromProductName(@Nullable String databaseProductName) {
        return resolve(databaseProductName, null, true);
    }

    @Nullable
    public static String optionalDriverClass(String dialectClassName) {
        return switch (dialectClassName) {
            case POSTGRES_DIALECT_CLASS -> POSTGRES_DRIVER_CLASS;
            case H2_DIALECT_CLASS -> H2_DRIVER_CLASS;
            default -> null;
        };
    }

    @Nullable
    public static String missingDriverMessage(String dialectClassName) {
        if (POSTGRES_DIALECT_CLASS.equals(dialectClassName) && !POSTGRES_AVAILABLE) {
            return "requires org.postgresql:postgresql (" + POSTGRES_DRIVER_CLASS + ")";
        }
        if (H2_DIALECT_CLASS.equals(dialectClassName) && !H2_AVAILABLE) {
            return "requires com.h2database:h2 (" + H2_DRIVER_CLASS + ")";
        }
        return null;
    }

    @Nullable
    public static Dialect instantiateConfigured(String dataSourceName, Optional<String> dialectClassName) {
        if (dialectClassName.isEmpty()) {
            return null;
        }
        String className = dialectClassName.get();
        String property = DataSourceUtil.isDefault(dataSourceName) ? "quarkus.jimmer.dialect"
                : "quarkus.jimmer.\"" + dataSourceName.replace("\"", "\\\"") + "\".dialect";
        String missingDriver = missingDriverMessage(className);
        if (missingDriver != null) {
            throw new IllegalArgumentException(
                    "The class \"" + className + "\" specified by `" + property + "` " + missingDriver);
        }
        Class<?> clazz;
        try {
            clazz = Class.forName(className, true, Thread.currentThread().getContextClassLoader());
        } catch (ClassNotFoundException ex) {
            throw new IllegalArgumentException(
                    "The class \"" + className + "\" specified by `" + property + "` cannot be found");
        }
        if (!Dialect.class.isAssignableFrom(clazz) || clazz.isInterface()) {
            throw new IllegalArgumentException(
                    "The class \"" + className + "\" specified by `" + property + "` must be a valid dialect implementation");
        }
        try {
            return (Dialect) clazz.getConstructor().newInstance();
        } catch (InvocationTargetException ex) {
            throw new IllegalArgumentException(
                    "Cannot create instance for the class \"" + className + "\" specified by `" + property + "`",
                    ex.getTargetException());
        } catch (Exception ex) {
            throw new IllegalArgumentException(
                    "Cannot create instance for the class \"" + className + "\" specified by `" + property + "`",
                    ex);
        }
    }

    @Nullable
    private static Dialect resolve(
            @Nullable String kindOrProductName,
            @Nullable String dbVersion,
            boolean failOnMissingDriver) {
        String normalized = DatabaseKind.normalize(kindOrProductName);
        if (normalized == null) {
            return null;
        }
        if ("microsoft sql server".equals(normalized)) {
            normalized = DatabaseKind.MSSQL;
        }
        return switch (normalized) {
            case DatabaseKind.H2 -> {
                if (!H2_AVAILABLE) {
                    if (failOnMissingDriver) {
                        throw new IllegalStateException("H2Dialect requires com.h2database:h2 (" + H2_DRIVER_CLASS + ")");
                    }
                    yield null;
                }
                yield new H2Dialect();
            }
            case DatabaseKind.POSTGRESQL -> {
                if (!POSTGRES_AVAILABLE) {
                    if (failOnMissingDriver) {
                        throw new IllegalStateException(
                                "PostgresDialect requires org.postgresql:postgresql (" + POSTGRES_DRIVER_CLASS + ")");
                    }
                    yield null;
                }
                yield new PostgresDialect();
            }
            case DatabaseKind.MYSQL -> isMySql5(dbVersion) ? new MySql5Dialect() : new MySqlDialect();
            case DatabaseKind.MARIADB -> new MySqlDialect();
            case DatabaseKind.ORACLE -> new OracleDialect();
            case DatabaseKind.MSSQL -> new SqlServerDialect();
            case "sqlite" -> new SQLiteDialect();
            case "tidb" -> new TiDBDialect();
            default -> null;
        };
    }

    private static boolean isMySql5(@Nullable String dbVersion) {
        if (dbVersion == null) {
            return false;
        }
        String trimmed = dbVersion.trim();
        return trimmed.equals("5") || trimmed.startsWith("5.");
    }

    /** Checks availability without initializing the optional driver's class. */
    private static boolean isDriverPresent(String className) {
        try {
            Class.forName(className, false, Dialect.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException ex) {
            return false;
        }
    }
}
