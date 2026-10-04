package io.quarkiverse.jimmer.runtime.cloud;

import java.io.IOException;
import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;

import org.babyfish.jimmer.impl.util.Classes;
import org.babyfish.jimmer.meta.ImmutableProp;
import org.babyfish.jimmer.runtime.ImmutableSpi;
import org.babyfish.jimmer.sql.ast.tuple.Tuple2;
import org.babyfish.jimmer.sql.fetcher.Fetcher;
import org.babyfish.jimmer.sql.runtime.MicroServiceExchange;
import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;

import io.quarkus.arc.DefaultBean;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import io.quarkus.restclient.config.RestClientsConfig;

@ApplicationScoped
@DefaultBean
public class QuarkusExchange implements MicroServiceExchange {

    private static final Logger LOG = Logger.getLogger(QuarkusExchange.class);

    private final ObjectMapper objectMapper;

    private final RestClientsConfig restClientsConfig;

    private final ConcurrentMap<String, ExchangeRestClient> clients = new ConcurrentHashMap<>();

    private final ReentrantLock clientCreationLock = new ReentrantLock();

    // Keep clients alive until every synchronous exchange finishes, and prevent creation during destruction.
    private final ReentrantReadWriteLock lifecycleLock = new ReentrantReadWriteLock(true);

    private boolean closed;

    public QuarkusExchange(ObjectMapper objectMapper, RestClientsConfig restClientsConfig) {
        this.objectMapper = objectMapper;
        this.restClientsConfig = restClientsConfig;
    }

    @Override
    public List<ImmutableSpi> findByIds(String microServiceName, Collection<?> ids, Fetcher<?> fetcher) throws Exception {
        lifecycleLock.readLock().lock();
        try {
            String json = client(microServiceName).findByIds(objectMapper.writeValueAsString(ids), fetcher.toString());
            return objectMapper.readValue(
                    json,
                    objectMapper.getTypeFactory().constructParametricType(
                            List.class,
                            fetcher.getImmutableType().getJavaClass()));
        } finally {
            lifecycleLock.readLock().unlock();
        }
    }

    @Override
    public List<Tuple2<Object, ImmutableSpi>> findByAssociatedIds(String microServiceName, ImmutableProp prop,
            Collection<?> targetIds, Fetcher<?> fetcher) throws Exception {
        lifecycleLock.readLock().lock();
        try {
            String json = client(microServiceName).findByAssociatedIds(prop.getName(),
                    objectMapper.writeValueAsString(targetIds),
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
        } finally {
            lifecycleLock.readLock().unlock();
        }
    }

    private ExchangeRestClient client(String microServiceName) {
        if (closed) {
            throw new IllegalStateException("The Jimmer microservice exchange is closed");
        }
        ExchangeRestClient client = clients.get(microServiceName);
        if (client != null) {
            return client;
        }
        clientCreationLock.lock();
        try {
            client = clients.get(microServiceName);
            if (client != null) {
                return client;
            }
            RestClientsConfig.RestClientConfig config = restClientsConfig.clients().get(microServiceName);
            if (config == null || config.url().isEmpty()) {
                throw new IllegalArgumentException(
                        "Can not find restClientConfig.url by microServiceName: " + microServiceName);
            }
            client = QuarkusRestClientBuilder.newBuilder()
                    .baseUri(URI.create(config.url().get())).build(ExchangeRestClient.class);
            clients.put(microServiceName, client);
            return client;
        } finally {
            clientCreationLock.unlock();
        }
    }

    @PreDestroy
    void destroy() {
        lifecycleLock.writeLock().lock();
        try {
            closed = true;
            clients.forEach((name, client) -> {
                try {
                    client.close();
                } catch (IOException | RuntimeException e) {
                    LOG.warnf(e, "Failed to close the Jimmer REST client for microservice %s", name);
                }
            });
            clients.clear();
        } finally {
            lifecycleLock.writeLock().unlock();
        }
    }
}
