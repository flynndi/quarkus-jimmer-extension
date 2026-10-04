package io.quarkiverse.jimmer.deployment.cfg;

import java.util.function.BooleanSupplier;

import io.quarkiverse.jimmer.runtime.cfg.JimmerBuildTimeConfig;

/** Build-time conditions shared by Jimmer's feature processors. */
public final class JimmerBuildConditions {

    private JimmerBuildConditions() {
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
            return config.enable() && config.language().equalsIgnoreCase("kotlin");
        }
    }
}
