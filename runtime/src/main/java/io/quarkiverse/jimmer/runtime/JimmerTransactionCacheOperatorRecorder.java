package io.quarkiverse.jimmer.runtime;

import java.util.function.Function;

import org.babyfish.jimmer.sql.JSqlClient;
import org.babyfish.jimmer.sql.cache.TransactionCacheOperator;
import org.babyfish.jimmer.sql.kt.KSqlClient;

import io.quarkiverse.jimmer.runtime.util.Assert;
import io.quarkiverse.jimmer.runtime.util.QuarkusSqlClientContainerUtil;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class JimmerTransactionCacheOperatorRecorder {

    public Function<SyntheticCreationalContext<TransactionCacheOperator>, TransactionCacheOperator> transactionJCacheOperatorFunction(
            String dataSourceName) {
        return quarkusTransactionCacheOperatorSyntheticCreationalContext -> {
            JSqlClient jSqlClient = QuarkusSqlClientContainerUtil.getQuarkusJSqlClientContainer(dataSourceName).getjSqlClient();
            TransactionCacheOperator transactionCacheOperator = new TransactionCacheOperator();
            transactionCacheOperator.initialize(jSqlClient);
            return transactionCacheOperator;
        };
    }

    public Function<SyntheticCreationalContext<TransactionCacheOperator>, TransactionCacheOperator> transactionKCacheOperatorFunction(
            String dataSourceName) {
        return quarkusTransactionCacheOperatorSyntheticCreationalContext -> {
            KSqlClient kSqlClient = QuarkusSqlClientContainerUtil.getQuarkusKSqlClientContainer(dataSourceName).getKSqlClient();
            TransactionCacheOperator transactionCacheOperator = new TransactionCacheOperator();
            Assert.notNull(kSqlClient, "KSqlClient is not null");
            transactionCacheOperator.initialize(kSqlClient.getJavaClient());
            return transactionCacheOperator;
        };
    }
}
