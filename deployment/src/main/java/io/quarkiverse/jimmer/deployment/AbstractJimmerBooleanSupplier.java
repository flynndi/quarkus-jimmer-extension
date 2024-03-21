package io.quarkiverse.jimmer.deployment;

import java.util.function.BooleanSupplier;

import io.quarkiverse.jimmer.runtime.cfg.JimmerBuildTimeConfig;

abstract class AbstractJimmerBooleanSupplier implements BooleanSupplier {

    protected final JimmerBuildTimeConfig jimmerBuildTimeConfig;

    protected AbstractJimmerBooleanSupplier(JimmerBuildTimeConfig jimmerBuildTimeConfig) {
        this.jimmerBuildTimeConfig = jimmerBuildTimeConfig;
    }

    @Override
    public abstract boolean getAsBoolean();
}
