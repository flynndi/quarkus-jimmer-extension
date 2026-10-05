package io.quarkiverse.jimmer.runtime;

import java.util.function.Function;
import java.util.function.Supplier;

import jakarta.enterprise.util.TypeLiteral;

import org.babyfish.jimmer.sql.JSqlClient;
import org.babyfish.jimmer.sql.cache.TransactionCacheOperator;
import org.babyfish.jimmer.sql.kt.KSqlClient;
import org.babyfish.jimmer.sql.runtime.JSqlClientImplementor;

import io.quarkus.agroal.runtime.AgroalDataSourceUtil;
import io.quarkus.arc.InjectableInstance;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class JimmerTransactionCacheOperatorRecorder {

    public Function<SyntheticCreationalContext<TransactionCacheOperator>, TransactionCacheOperator> transactionJCacheOperatorFunction(
            String dataSourceName) {
        return context -> new LazyTransactionCacheOperator(() -> context.getInjectedReference(
                new TypeLiteral<InjectableInstance<JSqlClient>>() {
                }, AgroalDataSourceUtil.qualifier(dataSourceName)).get());
    }

    public Function<SyntheticCreationalContext<TransactionCacheOperator>, TransactionCacheOperator> transactionKCacheOperatorFunction(
            String dataSourceName) {
        return context -> new LazyTransactionCacheOperator(() -> context.getInjectedReference(
                new TypeLiteral<InjectableInstance<KSqlClient>>() {
                }, AgroalDataSourceUtil.qualifier(dataSourceName)).get().getJavaClient());
    }

    public static final class LazyTransactionCacheOperator extends TransactionCacheOperator {
        private final Supplier<JSqlClient> client;
        private volatile boolean initialized;

        private LazyTransactionCacheOperator(Supplier<JSqlClient> client) {
            this.client = client;
        }

        @Override
        protected void onInitialize(JSqlClientImplementor sqlClient) {
            super.onInitialize(sqlClient);
            initialized = true;
        }

        @Override
        public void flush() {
            // Accessing the ArC client proxy initializes the real client and its selected operator.
            // Resolve it only when flushing, after the operator bean has finished creation.
            client.get().getCaches();
            if (initialized) {
                super.flush();
            }
        }
    }
}
