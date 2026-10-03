package io.quarkiverse.jimmer.runtime;

import java.util.function.Function;
import java.util.function.Supplier;

import jakarta.enterprise.util.TypeLiteral;

import org.babyfish.jimmer.sql.JSqlClient;
import org.babyfish.jimmer.sql.cache.TransactionCacheOperator;
import org.babyfish.jimmer.sql.runtime.JSqlClientImplementor;

import io.quarkiverse.jimmer.runtime.java.QuarkusJSqlClientContainer;
import io.quarkiverse.jimmer.runtime.kotlin.QuarkusKSqlClientContainer;
import io.quarkiverse.jimmer.runtime.util.QuarkusSqlClientContainerUtil;
import io.quarkus.arc.InjectableInstance;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class JimmerTransactionCacheOperatorRecorder {

    public Function<SyntheticCreationalContext<TransactionCacheOperator>, TransactionCacheOperator> transactionJCacheOperatorFunction(
            String dataSourceName) {
        return context -> new LazyTransactionCacheOperator(() -> context.getInjectedReference(
                new TypeLiteral<InjectableInstance<QuarkusJSqlClientContainer>>() {
                }, QuarkusSqlClientContainerUtil.getQuarkusSqlClientContainerQualifier(dataSourceName))
                .get().getjSqlClient());
    }

    public Function<SyntheticCreationalContext<TransactionCacheOperator>, TransactionCacheOperator> transactionKCacheOperatorFunction(
            String dataSourceName) {
        return context -> new LazyTransactionCacheOperator(() -> context.getInjectedReference(
                new TypeLiteral<InjectableInstance<QuarkusKSqlClientContainer>>() {
                }, QuarkusSqlClientContainerUtil.getQuarkusSqlClientContainerQualifier(dataSourceName))
                .get().getKSqlClient().getJavaClient());
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
            // Building the client initializes its selected operator with the real client, not a lazy delegate.
            // Do this after CDI bean creation has completed, to avoid recursive operator resolution.
            client.get().getCaches();
            if (initialized) {
                super.flush();
            }
        }
    }
}
