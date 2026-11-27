package io.quarkiverse.jimmer.runtime;

import java.util.function.Function;

import javax.sql.DataSource;

import jakarta.enterprise.util.TypeLiteral;
import jakarta.inject.Inject;

import org.babyfish.jimmer.sql.JSqlClient;

import io.quarkiverse.jimmer.runtime.cfg.JimmerBuildTimeConfig;
import io.quarkiverse.jimmer.runtime.cfg.JimmerRuntimeConfig;
import io.quarkiverse.jimmer.runtime.cfg.support.QuarkusCacheOperatorProvider;
import io.quarkiverse.jimmer.runtime.internal.SqlClientDestroyer;
import io.quarkus.agroal.runtime.AgroalDataSourceUtil;
import io.quarkus.arc.InjectableInstance;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class JimmerJavaDataSourcesRecorder {

    private final JimmerBuildTimeConfig buildTimeConfig;

    private final RuntimeValue<JimmerRuntimeConfig> runtimeConfig;

    @Inject
    public JimmerJavaDataSourcesRecorder(
            JimmerBuildTimeConfig buildTimeConfig,
            RuntimeValue<JimmerRuntimeConfig> runtimeConfig) {
        this.buildTimeConfig = buildTimeConfig;
        this.runtimeConfig = runtimeConfig;
    }

    public Function<SyntheticCreationalContext<JSqlClient>, JSqlClient> quarkusJSqlClientFunction(String dataSourceName) {
        return context -> SqlClientDestroyer.create(context, dataSourceName, beans -> {
            DataSource dataSource = context.getInjectedReference(new TypeLiteral<InjectableInstance<DataSource>>() {
            }, AgroalDataSourceUtil.qualifier(dataSourceName)).get();
            return new QuarkusSqlClientFactory(
                    runtimeConfig.getValue(),
                    buildTimeConfig,
                    dataSource,
                    beans,
                    builder -> builder.setCacheOperator(QuarkusCacheOperatorProvider.find(beans)),
                    false)
                    .create();
        });
    }
}
