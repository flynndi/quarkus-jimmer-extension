package io.quarkiverse.jimmer.deployment;

import io.quarkiverse.jimmer.runtime.cfg.JimmerBuildTimeConfig;

import java.util.function.BooleanSupplier;

public class MicroServiceEnable implements BooleanSupplier {

    private final JimmerBuildTimeConfig jimmerBuildTimeConfig;

    public MicroServiceEnable(JimmerBuildTimeConfig jimmerBuildTimeConfig) {
        this.jimmerBuildTimeConfig = jimmerBuildTimeConfig;
    }

    @Override
    public boolean getAsBoolean() {
        return jimmerBuildTimeConfig.microServiceName().isPresent();
    }
}
