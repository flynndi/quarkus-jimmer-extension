package io.quarkiverse.jimmer.runtime.client;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.regex.Pattern;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import org.babyfish.jimmer.client.runtime.Metadata;
import org.babyfish.jimmer.client.runtime.Operation;
import org.jboss.resteasy.reactive.RestPath;
import org.jboss.resteasy.reactive.RestQuery;
import org.jetbrains.annotations.Nullable;

public class Metadatas {

    private static final Pattern COMMA_PATTERN = Pattern.compile("\\s*,\\s*");

    private Metadatas() {
    }

    public static Metadata create(boolean isGenericSupported, @Nullable String groups) {
        return Metadata
                .newBuilder()
                .setOperationParser(new OperationParserImpl())
                .setParameterParameter(new ParameterParserImpl())
                .setGenericSupported(isGenericSupported)
                .setGroups(
                        groups != null && !groups.isEmpty() ? Arrays.asList(COMMA_PATTERN.split(groups)) : null)
                .build();
    }

    private static class OperationParserImpl implements Metadata.OperationParser {

        @Override
        public String uri(AnnotatedElement element) {
            Path path = element.getAnnotation(Path.class);
            if (null != path) {
                String uri = path.value();
                if (null != uri) {
                    return uri;
                }
            }
            return null;
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
            return new Operation.HttpMethod[] { Operation.HttpMethod.GET };
        }
    }

    private static class ParameterParserImpl implements Metadata.ParameterParser {

        @Nullable
        @Override
        public String requestHeader(Parameter javaParameter) {
            HeaderParam headerParam = javaParameter.getAnnotation(HeaderParam.class);
            if (null == headerParam) {
                return null;
            }
            return headerParam.value();
        }

        @Nullable
        @Override
        public String requestParam(Parameter javaParameter) {
            RestQuery restQuery = javaParameter.getAnnotation(RestQuery.class);
            if (null == restQuery) {
                return null;
            }
            return restQuery.value();
        }

        @Nullable
        @Override
        public String pathVariable(Parameter javaParameter) {
            RestPath restPath = javaParameter.getAnnotation(RestPath.class);
            if (null == restPath) {
                return null;
            }
            return restPath.value();
        }

        @Override
        public String defaultValue(Parameter javaParameter) {
            return null;
        }

        @Override
        public boolean isOptional(Parameter javaParameter) {
            RestQuery restQuery = javaParameter.getAnnotation(RestQuery.class);
            return null != restQuery;
        }

        @Override
        public boolean isRequestBody(Parameter javaParameter) {
            Consumes consumes = javaParameter.getAnnotation(Consumes.class);
            if (null != consumes) {
                String[] value = consumes.value();
                if (value.length == 0) {
                    return false;
                }
                return Arrays.asList(value).contains(MediaType.APPLICATION_JSON);
            }
            return false;
        }
    }
}
