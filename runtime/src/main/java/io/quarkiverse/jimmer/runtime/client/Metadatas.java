package io.quarkiverse.jimmer.runtime.client;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import org.babyfish.jimmer.client.meta.TypeName;
import org.babyfish.jimmer.client.runtime.Metadata;
import org.babyfish.jimmer.client.runtime.Operation;
import org.babyfish.jimmer.client.runtime.VirtualType;
import org.babyfish.jimmer.client.runtime.impl.IllegalApiException;
import org.jboss.resteasy.reactive.RestCookie;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.RestHeader;
import org.jboss.resteasy.reactive.RestMatrix;
import org.jboss.resteasy.reactive.RestMulti;
import org.jboss.resteasy.reactive.RestPath;
import org.jboss.resteasy.reactive.RestQuery;
import org.jboss.resteasy.reactive.multipart.FilePart;
import org.jboss.resteasy.reactive.multipart.FileUpload;
import org.jetbrains.annotations.Nullable;

import io.quarkus.runtime.util.StringUtil;
import io.smallrye.mutiny.Multi;

public class Metadatas {

    private static final Pattern COMMA_PATTERN = Pattern.compile("\\s*,\\s*");

    private Metadatas() {
    }

    public static Metadata create(boolean isGenericSupported,
            @Nullable String groups,
            @Nullable String uriPrefix) {
        return Metadata
                .newBuilder()
                .setOperationParser(new OperationParserImpl())
                .setParameterParser(new ParameterParserImpl())
                .addIgnoredParameterTypes(jakarta.ws.rs.core.UriInfo.class, jakarta.ws.rs.core.HttpHeaders.class,
                        jakarta.ws.rs.core.Request.class, jakarta.ws.rs.core.SecurityContext.class,
                        jakarta.ws.rs.core.Configuration.class, jakarta.ws.rs.container.ResourceInfo.class,
                        jakarta.ws.rs.ext.Providers.class, jakarta.ws.rs.container.ResourceContext.class,
                        jakarta.ws.rs.container.AsyncResponse.class, jakarta.ws.rs.sse.Sse.class,
                        jakarta.ws.rs.sse.SseEventSink.class, io.vertx.core.http.HttpServerRequest.class,
                        io.vertx.core.http.HttpServerResponse.class, io.vertx.ext.web.RoutingContext.class)
                .addIgnoredParameterTypeNames("org.jboss.resteasy.reactive.server.spi.ServerRequestContext",
                        "org.jboss.resteasy.reactive.server.SimpleResourceInfo")
                .setVirtualTypeMap(
                        Map.of(TypeName.of(FilePart.class), VirtualType.FILE,
                                TypeName.of(FileUpload.class), VirtualType.FILE))
                .setGenericSupported(isGenericSupported)
                .setGroups(groups != null && !groups.isEmpty() ? Arrays.asList(COMMA_PATTERN.split(groups)) : null)
                .setUriPrefix(uriPrefix)
                .build();
    }

    private static class OperationParserImpl implements Metadata.OperationParser {

        @Override
        public String uri(AnnotatedElement element) {
            Path path = element.getAnnotation(Path.class);
            return path != null ? path.value() : null;
        }

        @Override
        public Operation.HttpMethod[] http(Method method) {
            if (null != method.getAnnotation(POST.class)) {
                return new Operation.HttpMethod[] { Operation.HttpMethod.POST };
            }
            if (null != method.getAnnotation(PUT.class)) {
                return new Operation.HttpMethod[] { Operation.HttpMethod.PUT };
            }
            if (null != method.getAnnotation(DELETE.class)) {
                return new Operation.HttpMethod[] { Operation.HttpMethod.DELETE };
            }
            if (null != method.getAnnotation(HEAD.class)) {
                return new Operation.HttpMethod[] { Operation.HttpMethod.HEAD };
            }
            if (null != method.getAnnotation(PATCH.class)) {
                return new Operation.HttpMethod[] { Operation.HttpMethod.PATCH };
            }
            if (null != method.getAnnotation(OPTIONS.class)) {
                return new Operation.HttpMethod[] { Operation.HttpMethod.OPTIONS };
            }
            return new Operation.HttpMethod[] { Operation.HttpMethod.GET };
        }

        @Override
        public boolean isStream(Method method) {
            return RestMulti.class == method.getReturnType() || Multi.class == method.getReturnType();
        }
    }

    private static class ParameterParserImpl implements Metadata.ParameterParser {

        @Nullable
        @Override
        public String requestHeader(Parameter javaParameter) {
            HeaderParam headerParam = javaParameter.getAnnotation(HeaderParam.class);
            if (headerParam != null) {
                return headerParam.value();
            }
            RestHeader restHeader = javaParameter.getAnnotation(RestHeader.class);
            if (restHeader == null) {
                return null;
            }
            if (!restHeader.value().isEmpty()) {
                return restHeader.value();
            }
            if (!javaParameter.isNamePresent()) {
                throw unsupported(javaParameter,
                        "An unnamed @RestHeader requires compilation with -parameters or an explicit header name");
            }
            // Match Quarkus REST's implicit header naming without using deployment classes at runtime.
            StringBuilder name = new StringBuilder();
            Iterator<String> parts = StringUtil.camelHumpsIterator(javaParameter.getName());
            while (parts.hasNext()) {
                String part = parts.next();
                if (!name.isEmpty()) {
                    name.append('-');
                }
                name.append(part.substring(0, 1).toUpperCase(Locale.ROOT)).append(part.substring(1));
            }
            return name.toString();
        }

        @Nullable
        @Override
        public String requestParam(Parameter javaParameter) {
            QueryParam queryParam = javaParameter.getAnnotation(QueryParam.class);
            if (queryParam != null) {
                return queryParam.value();
            }
            RestQuery restQuery = javaParameter.getAnnotation(RestQuery.class);
            if (null == restQuery) {
                return null;
            }
            return restQuery.value();
        }

        @Nullable
        @Override
        public String pathVariable(Parameter javaParameter) {
            PathParam pathParam = javaParameter.getAnnotation(PathParam.class);
            if (pathParam != null) {
                return pathParam.value();
            }
            RestPath restPath = javaParameter.getAnnotation(RestPath.class);
            if (null == restPath) {
                return null;
            }
            return restPath.value();
        }

        @Nullable
        @Override
        public String requestPart(Parameter javaParameter) {
            RestForm restForm = javaParameter.getAnnotation(RestForm.class);
            FormParam formParam = javaParameter.getAnnotation(FormParam.class);
            if (restForm == null && formParam == null) {
                if (FilePart.class.isAssignableFrom(javaParameter.getType())) {
                    throw unsupported(javaParameter,
                            "File uploads require @RestForm or @FormParam and @Consumes(multipart/form-data)");
                }
                return null;
            }
            Consumes consumes = consumes(javaParameter);
            if (consumes == null || Arrays.stream(consumes.value())
                    .noneMatch(value -> mediaType(value).equals(MediaType.MULTIPART_FORM_DATA))) {
                throw unsupported(javaParameter, "Form parameters are supported only with @Consumes(multipart/form-data)");
            }
            String name = restForm != null ? restForm.value() : formParam.value();
            if (FileUpload.ALL.equals(name)) {
                throw unsupported(javaParameter, "A wildcard upload cannot be represented as a named client request part");
            }
            return name;
        }

        @Override
        public String defaultValue(Parameter javaParameter) {
            DefaultValue defaultValue = javaParameter.getAnnotation(DefaultValue.class);
            return defaultValue != null ? defaultValue.value() : null;
        }

        @Override
        public Boolean isOptional(Parameter javaParameter) {
            if (pathVariable(javaParameter) != null) {
                return false;
            }
            if (defaultValue(javaParameter) != null) {
                return true;
            }
            if (hasAnnotation(javaParameter, "jakarta.validation.constraints.NotNull",
                    "jakarta.validation.constraints.NotBlank", "jakarta.validation.constraints.NotEmpty",
                    "jakarta.annotation.Nonnull", "org.jspecify.annotations.NonNull")) {
                return false;
            }
            if (hasAnnotation(javaParameter, "jakarta.annotation.Nullable", "org.jspecify.annotations.Nullable")) {
                return true;
            }
            if (requestParam(javaParameter) != null || requestHeader(javaParameter) != null
                    || requestPart(javaParameter) != null) {
                return true;
            }
            // JAX-RS has no @RequestBody(required=...) equivalent. Preserve Jimmer's non-null metadata
            // unless the application explicitly declares the body nullable with a runtime-visible annotation.
            return null;
        }

        @Override
        public boolean isRequestBody(Parameter javaParameter) {
            if (requestParam(javaParameter) != null || pathVariable(javaParameter) != null
                    || requestHeader(javaParameter) != null || requestPart(javaParameter) != null) {
                return false;
            }
            if (javaParameter.isAnnotationPresent(jakarta.ws.rs.core.Context.class)
                    || javaParameter.isAnnotationPresent(jakarta.ws.rs.container.Suspended.class)) {
                return false;
            }
            if (javaParameter.isAnnotationPresent(BeanParam.class)
                    || javaParameter.isAnnotationPresent(CookieParam.class)
                    || javaParameter.isAnnotationPresent(MatrixParam.class)
                    || javaParameter.isAnnotationPresent(RestCookie.class)
                    || javaParameter.isAnnotationPresent(RestMatrix.class)) {
                throw unsupported(javaParameter,
                        "Bean, cookie and matrix parameter bindings are not supported by Jimmer client metadata");
            }
            Consumes consumes = consumes(javaParameter);
            if (consumes != null && Arrays.stream(consumes.value()).noneMatch(value -> {
                String type = mediaType(value);
                return type.equals(MediaType.APPLICATION_JSON) || type.equals(MediaType.WILDCARD)
                        || type.equals("application/*");
            })) {
                throw unsupported(javaParameter,
                        "Jimmer client request bodies support application/json; use named form parameters for multipart requests");
            }
            return true;
        }

        @Override
        public boolean isRequestPartRequired(Parameter javaParameter) {
            // This SPI means "must use multipart binding", not "the part is mandatory".
            return requestPart(javaParameter) != null;
        }

        private static Consumes consumes(Parameter parameter) {
            Consumes consumes = parameter.getDeclaringExecutable().getAnnotation(Consumes.class);
            return consumes != null ? consumes
                    : parameter.getDeclaringExecutable().getDeclaringClass().getAnnotation(Consumes.class);
        }

        private static String mediaType(String value) {
            return value.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        }

        private static boolean hasAnnotation(Parameter parameter, String... names) {
            return Arrays.stream(names).anyMatch(name -> Arrays.stream(parameter.getAnnotations())
                    .anyMatch(annotation -> annotation.annotationType().getName().equals(name))
                    || Arrays.stream(parameter.getAnnotatedType().getAnnotations())
                            .anyMatch(annotation -> annotation.annotationType().getName().equals(name)));
        }

        private static IllegalApiException unsupported(Parameter parameter, String message) {
            return new IllegalApiException(message + ": " + parameter.getDeclaringExecutable()
                    + ", parameter " + parameter.getName());
        }
    }
}
