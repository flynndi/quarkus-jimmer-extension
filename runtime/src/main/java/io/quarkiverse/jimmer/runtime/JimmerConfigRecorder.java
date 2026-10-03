package io.quarkiverse.jimmer.runtime;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;

import io.quarkiverse.jimmer.runtime.cfg.JimmerConfigValidator;
import io.quarkiverse.jimmer.runtime.cfg.JimmerDataSourceRuntimeConfig;
import io.quarkiverse.jimmer.runtime.cfg.JimmerRuntimeConfig;
import io.quarkus.agroal.runtime.AgroalDataSourceUtil;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class JimmerConfigRecorder {

    private final RuntimeValue<JimmerRuntimeConfig> runtimeConfig;

    @Inject
    public JimmerConfigRecorder(RuntimeValue<JimmerRuntimeConfig> runtimeConfig) {
        this.runtimeConfig = runtimeConfig;
    }

    public void validateActiveDataSources(List<String> dataSourceNames) {
        Map<String, JimmerDataSourceRuntimeConfig> activeConfigs = new LinkedHashMap<>();
        for (String name : dataSourceNames) {
            JimmerDataSourceRuntimeConfig config = runtimeConfig.getValue().dataSources().get(name);
            if (config.active().filter(active -> !active).isPresent()) {
                continue;
            }
            // Bean metadata is sufficient: neither get() nor a JDBC connection is needed.
            if (AgroalDataSourceUtil.dataSourceInstance(name).getHandle().getBean().isActive()) {
                activeConfigs.put(name, config);
            }
        }
        JimmerConfigValidator.validateDataSources(activeConfigs);
    }
}
