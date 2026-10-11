package io.quarkiverse.jimmer.runtime;

import java.util.function.Function;

import javax.sql.DataSource;

import jakarta.enterprise.util.TypeLiteral;

import org.babyfish.jimmer.sql.JSqlClient;

import io.quarkus.agroal.runtime.AgroalDataSourceUtil;
import io.quarkus.arc.InjectableInstance;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class JimmerJavaDataSourcesRecorder {

    public Function<SyntheticCreationalContext<JSqlClient>, JSqlClient> quarkusJSqlClientFunction(String dataSourceName) {
        return context -> {
            DataSource dataSource = context.getInjectedReference(new TypeLiteral<InjectableInstance<DataSource>>() {
            }, AgroalDataSourceUtil.qualifier(dataSourceName)).get();
            return context.getInjectedReference(QuarkusJSqlClientProducer.class).createJSqlClient(dataSource, dataSourceName);
        };
    }
}
