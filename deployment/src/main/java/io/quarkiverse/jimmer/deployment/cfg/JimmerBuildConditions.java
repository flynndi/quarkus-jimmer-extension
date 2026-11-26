package io.quarkiverse.jimmer.deployment.cfg;

import java.util.function.BooleanSupplier;

import io.quarkiverse.jimmer.runtime.cfg.JimmerBuildTimeConfig;
import io.quarkus.bootstrap.classloading.QuarkusClassLoader;

/** Build-time conditions shared by Jimmer's feature processors. */
public final class JimmerBuildConditions {

    private JimmerBuildConditions() {
    }

    public static final class Jackson2Present implements BooleanSupplier {
        @Override
        public boolean getAsBoolean() {
            return QuarkusClassLoader.isClassPresentAtRuntime("com.fasterxml.jackson.databind.ObjectMapper");
        }
    }

    public static final class RedissonKryoPresent implements BooleanSupplier {
        @Override
        public boolean getAsBoolean() {
            return QuarkusClassLoader.isClassPresentAtRuntime("org.redisson.codec.Kryo5Codec");
        }
    }

    public static final class Enabled implements BooleanSupplier {
        private final JimmerBuildTimeConfig config;

        public Enabled(JimmerBuildTimeConfig config) {
            this.config = config;
        }

        @Override
        public boolean getAsBoolean() {
            return config.enable();
        }
    }

    public static final class JavaEnabled implements BooleanSupplier {
        private final JimmerBuildTimeConfig config;

        public JavaEnabled(JimmerBuildTimeConfig config) {
            this.config = config;
        }

        @Override
        public boolean getAsBoolean() {
            return config.enable() && config.language().equalsIgnoreCase("java");
        }
    }

    public static final class KotlinEnabled implements BooleanSupplier {
        private final JimmerBuildTimeConfig config;

        public KotlinEnabled(JimmerBuildTimeConfig config) {
            this.config = config;
        }

        @Override
        public boolean getAsBoolean() {
            return config.enable() && config.language().equalsIgnoreCase("kotlin")
                    && QuarkusClassLoader.isClassPresentAtRuntime("org.babyfish.jimmer.sql.kt.KSqlClient");
        }
    }
}
