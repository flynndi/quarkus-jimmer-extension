package io.quarkiverse.jimmer.runtime.client;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.Collections;
import java.util.regex.Pattern;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import org.babyfish.jimmer.client.meta.TypeName;
import org.babyfish.jimmer.client.runtime.Metadata;
import org.babyfish.jimmer.client.runtime.Operation;
import org.babyfish.jimmer.client.runtime.VirtualType;
import org.jboss.resteasy.reactive.RestPath;
import org.jboss.resteasy.reactive.RestQuery;
import org.jboss.resteasy.reactive.multipart.FilePart;
import org.jetbrains.annotations.Nullable;

public class Metadatas {

    private static final Pattern COMMA_PATTERN = Pattern.compile("\\s*,\\s*");

    private Metadatas() {
    }

    public static Metadata create(boolean isGenericSupported,
            @Nullable String groups,
            @Nullable String uriPrefix,
            boolean controllerNullityChecked) {
        return Metadata
                .newBuilder()
                .setOperationParser(new OperationParserImpl())
                .setParameterParameter(new ParameterParserImpl())
                .setVirtualTypeMap(
                        Collections.singletonMap(
                                TypeName.of(FilePart.class),
                                VirtualType.FILE))
                .setGenericSupported(isGenericSupported)
                .setGroups(groups != null && !groups.isEmpty() ? Arrays.asList(COMMA_PATTERN.split(groups)) : null)
                .setUriPrefix(uriPrefix)
                .setControllerNullityChecked(controllerNullityChecked)
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

        @Nullable
        @Override
        public String requestPart(Parameter javaParameter) {
            Class<?> type = javaParameter.getType();
            if (FilePart.class.isAssignableFrom(type)) {
                return javaParameter.getName();
            }
            return null;
        }

        @Override
        public String defaultValue(Parameter javaParameter) {
            HeaderParam headerParam = javaParameter.getAnnotation(HeaderParam.class);
            if (null != headerParam && !headerParam.value().isEmpty()) {
                return headerParam.value();
            }
            RestQuery restQuery = javaParameter.getAnnotation(RestQuery.class);
            if (null != restQuery && !restQuery.value().isEmpty()) {
                return restQuery.value();
            }
            return null;
        }

        @Override
        public boolean isOptional(Parameter javaParameter) {
            return true;
        }

        @Override
        public boolean isRequestBody(Parameter javaParameter) {
            RestQuery restQuery = javaParameter.getAnnotation(RestQuery.class);
            if (null != restQuery) {
                return false;
            }

            RestPath restPath = javaParameter.getAnnotation(RestPath.class);
            if (null != restPath) {
                return false;
            }

            Class<?> type = javaParameter.getType();
            if (FilePart.class.isAssignableFrom(type)) {
                return false;
            }

            Consumes methodConsumes = javaParameter.getAnnotation(Consumes.class);
            if (null != methodConsumes) {
                String[] value = methodConsumes.value();
                if (value.length == 0) {
                    return false;
                }
                return Arrays.asList(value).contains(MediaType.APPLICATION_JSON);
            }

            Consumes classConsumes = javaParameter.getDeclaringExecutable().getDeclaringClass().getAnnotation(Consumes.class);
            if (null != classConsumes) {
                String[] value = classConsumes.value();
                if (value.length == 0) {
                    return false;
                }
                return Arrays.asList(value).contains(MediaType.APPLICATION_JSON);
            }
            return false;
        }

        @Override
        public boolean isRequestPartRequired(Parameter javaParameter) {
            Class<?> type = javaParameter.getType();
            return FilePart.class.isAssignableFrom(type);
        }
    }
}
