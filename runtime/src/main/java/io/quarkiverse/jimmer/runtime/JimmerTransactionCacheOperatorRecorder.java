package io.quarkiverse.jimmer.runtime;

import java.util.function.Function;

import org.babyfish.jimmer.sql.JSqlClient;
import org.babyfish.jimmer.sql.kt.KSqlClient;

import io.quarkiverse.jimmer.runtime.cache.impl.QuarkusTransactionCacheOperator;
import io.quarkiverse.jimmer.runtime.util.Assert;
import io.quarkiverse.jimmer.runtime.util.QuarkusSqlClientContainerUtil;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class JimmerTransactionCacheOperatorRecorder {

    public Function<SyntheticCreationalContext<QuarkusTransactionCacheOperator>, QuarkusTransactionCacheOperator> transactionJCacheOperatorFunction(
            String dataSourceName) {
        return quarkusTransactionCacheOperatorSyntheticCreationalContext -> {
            JSqlClient jSqlClient = QuarkusSqlClientContainerUtil.getJquarkusSqlClientContainer(dataSourceName).getjSqlClient();
            QuarkusTransactionCacheOperator quarkusTransactionCacheOperator = new QuarkusTransactionCacheOperator();
            quarkusTransactionCacheOperator.initialize(jSqlClient);
            return quarkusTransactionCacheOperator;
        };
    }

    public Function<SyntheticCreationalContext<QuarkusTransactionCacheOperator>, QuarkusTransactionCacheOperator> transactionKCacheOperatorFunction(
            String dataSourceName) {
        return quarkusTransactionCacheOperatorSyntheticCreationalContext -> {
            KSqlClient kSqlClient = QuarkusSqlClientContainerUtil.getKquarkusSqlClientContainer(dataSourceName).getKSqlClient();
            QuarkusTransactionCacheOperator quarkusTransactionCacheOperator = new QuarkusTransactionCacheOperator();
            Assert.notNull(kSqlClient, "KSqlClient is not null");
            quarkusTransactionCacheOperator.initialize(kSqlClient.getJavaClient());
            return quarkusTransactionCacheOperator;
        };
    }
}
