package io.quarkiverse.jimmer.runtime.dialect;

import org.babyfish.jimmer.sql.dialect.Dialect;

/**
 * Classpath constants used to discard unavailable dialect branches during native-image analysis.
 * Keep this class build-time initialized and free of configuration or database access.
 */
public final class DialectAvailability {

    public static final boolean POSTGRES = present("org.postgresql.util.PGobject");
    public static final boolean H2 = present("org.h2.value.ValueJson");

    private DialectAvailability() {
    }

    /** Checks availability without initializing the optional driver's class. */
    private static boolean present(String name) {
        try {
            Class.forName(name, false, Dialect.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException ex) {
            return false;
        }
    }
}
