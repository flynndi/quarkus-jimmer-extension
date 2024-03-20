package io.quarkiverse.jimmer.it.config;

import org.eclipse.microprofile.rest.client.RestClientBuilder;
import org.eclipse.microprofile.rest.client.spi.RestClientListener;

import io.quarkiverse.jimmer.runtime.cloud.ExchangeRestClient;
import io.quarkus.rest.client.reactive.runtime.RestClientBuilderImpl;
import io.quarkus.rest.client.reactive.runtime.context.ClientHeadersFactoryContextResolver;
import io.vertx.core.http.HttpHeaders;

public class ClientBuilderListener implements RestClientListener {

    @Override
    public void onNewClient(Class<?> serviceInterface, RestClientBuilder builder) {
        if (serviceInterface.isAssignableFrom(ExchangeRestClient.class)) {
            RestClientBuilderImpl registerImpl = (RestClientBuilderImpl) builder
                    .register(new ClientHeadersFactoryContextResolver((incomingHeaders, clientOutgoingHeaders) -> {
                        clientOutgoingHeaders.add(HttpHeaders.AUTHORIZATION.toString(), "Bearer ClientBuilderListener");
                        return clientOutgoingHeaders;
                    }));
        }
    }
}
