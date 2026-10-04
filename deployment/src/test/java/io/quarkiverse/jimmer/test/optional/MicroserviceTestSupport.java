package io.quarkiverse.jimmer.test.optional;

import java.util.Collection;
import java.util.List;

import jakarta.inject.Singleton;

import org.babyfish.jimmer.meta.ImmutableProp;
import org.babyfish.jimmer.runtime.ImmutableSpi;
import org.babyfish.jimmer.sql.ast.tuple.Tuple2;
import org.babyfish.jimmer.sql.fetcher.Fetcher;
import org.babyfish.jimmer.sql.runtime.MicroServiceExchange;

import io.quarkus.agroal.DataSource;

final class MicroserviceTestSupport {

    @Singleton
    public static class DefaultExchange implements MicroServiceExchange {
        @Override
        public List<ImmutableSpi> findByIds(String microServiceName, Collection<?> ids, Fetcher<?> fetcher) {
            return List.of();
        }

        @Override
        public List<Tuple2<Object, ImmutableSpi>> findByAssociatedIds(String microServiceName, ImmutableProp prop,
                Collection<?> targetIds, Fetcher<?> fetcher) {
            return List.of();
        }
    }

    @Singleton
    @DataSource("<default>")
    public static class NamedExchange extends DefaultExchange {
    }

    @Singleton
    public static class OtherDefaultExchange extends DefaultExchange {
    }
}
