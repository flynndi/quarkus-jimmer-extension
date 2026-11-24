package io.quarkiverse.jimmer.test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.net.URLClassLoader;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.Map;

import javax.sql.DataSource;

import org.babyfish.jimmer.sql.dialect.Dialect;
import org.babyfish.jimmer.sql.dialect.H2Dialect;
import org.babyfish.jimmer.sql.dialect.MySqlDialect;
import org.h2.Driver;
import org.junit.jupiter.api.Test;

import io.quarkiverse.jimmer.runtime.dialect.DialectDetector;

class DialectDetectorTest {

    @Test
    void detectsH2WhenItsDriverIsAvailable() {
        assertInstanceOf(H2Dialect.class, new DialectDetector.Impl(null).detectDialect(connection("H2")));
    }

    @Test
    void driversMissingFromJimmerFailClearlyWithoutBreakingOtherDialects() throws Exception {
        URL runtime = DialectDetector.class.getProtectionDomain().getCodeSource().getLocation();
        URL jimmer = Dialect.class.getProtectionDomain().getCodeSource().getLocation();
        URL h2 = Driver.class.getProtectionDomain().getCodeSource().getLocation();
        ClassLoader parent = new ClassLoader(getClass().getClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.startsWith("io.quarkiverse.jimmer.runtime.dialect.")
                        || name.startsWith("org.babyfish.jimmer.sql.dialect.")
                        || name.startsWith("org.postgresql.") || name.startsWith("org.h2.")) {
                    throw new ClassNotFoundException(name);
                }
                return super.loadClass(name, resolve);
            }
        };
        // Only the extension can see H2. A Jimmer dialect cannot link to its driver here.
        try (URLClassLoader dialectLoader = new URLClassLoader(new URL[] { jimmer }, parent);
                URLClassLoader loader = new URLClassLoader(new URL[] { runtime, h2 }, dialectLoader)) {
            Class<?> detectorType = loader.loadClass(DialectDetector.Impl.class.getName());
            Object detector = detectorType.getConstructor(DataSource.class).newInstance((Object) null);
            var detect = detectorType.getMethod("detectDialect", Connection.class);
            for (var entry : Map.of("PostgreSQL", "org.postgresql:postgresql", "H2", "com.h2database:h2").entrySet()) {
                var failure = assertThrows(InvocationTargetException.class,
                        () -> detect.invoke(detector, connection(entry.getKey())));
                var cause = assertInstanceOf(IllegalStateException.class, failure.getCause());
                assertTrue(cause.getMessage().contains(entry.getValue()), cause.getMessage());
            }
            Object mysql = detect.invoke(detector, connection("MySQL"));
            assertSame(dialectLoader.loadClass(MySqlDialect.class.getName()), mysql.getClass());
            assertNull(detect.invoke(detector, connection("Unknown")));
        }
    }

    private Connection connection(String productName) {
        DatabaseMetaData metadata = (DatabaseMetaData) Proxy.newProxyInstance(DatabaseMetaData.class.getClassLoader(),
                new Class<?>[] { DatabaseMetaData.class }, (proxy, method, args) -> {
                    if (method.getName().equals("getDatabaseProductName")) {
                        return productName;
                    }
                    throw new AssertionError("Unexpected metadata access: " + method.getName());
                });
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[] { Connection.class },
                (proxy, method, args) -> {
                    if (method.getName().equals("getMetaData")) {
                        return metadata;
                    }
                    throw new AssertionError("Unexpected connection access: " + method.getName());
                });
    }
}
