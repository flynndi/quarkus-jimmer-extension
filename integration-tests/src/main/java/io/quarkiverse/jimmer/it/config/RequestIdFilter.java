package io.quarkiverse.jimmer.it.config;

import java.io.IOException;
import java.util.Collections;
import java.util.UUID;

import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.ext.Provider;

import org.jboss.logging.MDC;
import org.jboss.resteasy.reactive.server.ServerRequestFilter;
import org.jboss.resteasy.reactive.server.ServerResponseFilter;

@Provider
public class RequestIdFilter implements ClientRequestFilter {

    private final String REQUEST_ID_HEADER = "X-Request-ID";

    private final String REQUEST_ID_MDC_KEY = "requestId";

    @ServerRequestFilter
    void getRequestFilter(ContainerRequestContext requestContext) {
        var requestId = requestContext.getHeaderString(REQUEST_ID_HEADER);
        MDC.put(REQUEST_ID_MDC_KEY, requestId != null ? requestId : UUID.randomUUID().toString());
    }

    @ServerResponseFilter
    void getResponseFilter(ContainerResponseContext responseContext) {
        MDC.remove(REQUEST_ID_MDC_KEY);
    }

    @Override
    public void filter(ClientRequestContext requestContext) throws IOException {
        var requestId = MDC.get(REQUEST_ID_MDC_KEY);
        if (requestId != null) {
            requestContext.getHeaders().put(REQUEST_ID_HEADER, Collections.singletonList(requestId));
        }
    }
}
