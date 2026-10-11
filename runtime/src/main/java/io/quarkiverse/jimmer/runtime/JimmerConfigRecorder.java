package io.quarkiverse.jimmer.runtime;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import jakarta.inject.Inject;

import io.quarkiverse.jimmer.runtime.cfg.JimmerConfigValidator;
import io.quarkiverse.jimmer.runtime.cfg.JimmerDataSourceRuntimeConfig;
import io.quarkiverse.jimmer.runtime.cfg.JimmerRuntimeConfig;
import io.quarkus.agroal.runtime.AgroalDataSourceUtil;
import io.quarkus.arc.ActiveResult;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class JimmerConfigRecorder {

    private final RuntimeValue<JimmerRuntimeConfig> runtimeConfig;

    @Inject
    public JimmerConfigRecorder(RuntimeValue<JimmerRuntimeConfig> runtimeConfig) {
        this.runtimeConfig = runtimeConfig;
    }

    public Supplier<ActiveResult> checkActiveSupplier(String dataSourceName) {
        return () -> {
            if (runtimeConfig.getValue().dataSources().get(dataSourceName).active().filter(active -> !active).isPresent()) {
                return ActiveResult.inactive("Jimmer for datasource '" + dataSourceName + "' was explicitly deactivated");
            }
            ActiveResult dataSourceActive = AgroalDataSourceUtil.dataSourceInstance(dataSourceName)
                    .getHandle().getBean().checkActive();
            if (!dataSourceActive.value()) {
                return ActiveResult.inactive("Jimmer for datasource '" + dataSourceName
                        + "' is inactive because its datasource is inactive", dataSourceActive);
            }
            return ActiveResult.active();
        };
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
