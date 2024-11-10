package io.quarkiverse.jimmer.runtime.cloud;

import java.net.URI;
import java.util.Collection;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;

import org.babyfish.jimmer.impl.util.Classes;
import org.babyfish.jimmer.meta.ImmutableProp;
import org.babyfish.jimmer.runtime.ImmutableSpi;
import org.babyfish.jimmer.sql.ast.tuple.Tuple2;
import org.babyfish.jimmer.sql.fetcher.Fetcher;
import org.babyfish.jimmer.sql.runtime.MicroServiceExchange;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;

import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import io.quarkus.restclient.config.RestClientsConfig;

@ApplicationScoped
public class QuarkusExchange implements MicroServiceExchange {

    private final ObjectMapper objectMapper;

    private final RestClientsConfig restClientsConfig;

    public QuarkusExchange(ObjectMapper objectMapper, RestClientsConfig restClientsConfig) {
        this.objectMapper = objectMapper;
        this.restClientsConfig = restClientsConfig;
    }

    @Override
    public List<ImmutableSpi> findByIds(String microServiceName, Collection<?> ids, Fetcher<?> fetcher) throws Exception {
        RestClientsConfig.RestClientConfig restClientConfig = restClientsConfig.getClient(microServiceName);
        if (restClientConfig.url().isPresent()) {
            ExchangeRestClient quarkusExchangeRestClient = QuarkusRestClientBuilder.newBuilder()
                    .baseUrl(URI.create(restClientConfig.url().get()).toURL()).build(ExchangeRestClient.class);
            String json = quarkusExchangeRestClient.findByIds(ids.toString(), fetcher.toString());
            return objectMapper.readValue(
                    json,
                    objectMapper.getTypeFactory().constructParametricType(
                            List.class,
                            fetcher.getImmutableType().getJavaClass()));
        } else {
            throw new IllegalArgumentException("Can not find restClientConfig.url by microServiceName: " + microServiceName);
        }
    }

    @Override
    public List<Tuple2<Object, ImmutableSpi>> findByAssociatedIds(String microServiceName, ImmutableProp prop,
            Collection<?> targetIds, Fetcher<?> fetcher) throws Exception {
        RestClientsConfig.RestClientConfig restClientConfig = restClientsConfig.getClient(microServiceName);
        if (restClientConfig.url().isPresent()) {
            ExchangeRestClient quarkusExchangeRestClient = QuarkusRestClientBuilder.newBuilder()
                    .baseUrl(URI.create(restClientConfig.url().get()).toURL()).build(ExchangeRestClient.class);
            String json = quarkusExchangeRestClient.findByAssociatedIds(prop.getName(), targetIds.toString(),
                    fetcher.toString());
            TypeFactory typeFactory = objectMapper.getTypeFactory();
            return objectMapper.readValue(
                    json,
                    typeFactory.constructParametricType(
                            List.class,
                            typeFactory.constructParametricType(
                                    Tuple2.class,
                                    Classes.boxTypeOf(prop.getTargetType().getIdProp().getElementClass()),
                                    fetcher.getImmutableType().getJavaClass())));
        } else {
            throw new IllegalArgumentException("Can not find restClientConfig.url by microServiceName: " + microServiceName);
        }
    }
}