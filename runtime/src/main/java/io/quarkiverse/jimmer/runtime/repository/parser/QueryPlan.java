package io.quarkiverse.jimmer.runtime.repository.parser;

import java.util.List;
import java.util.Optional;

import org.babyfish.jimmer.Page;

import io.quarkiverse.jimmer.runtime.repository.DynamicParam;

/** Execution metadata shared by the build-time parser and generated repositories. */
public record QueryPlan(Query query, ReturnShape returnShape, String methodDescription, List<Parameter> parameters) {

    public QueryPlan {
        parameters = List.copyOf(parameters);
    }

    public record Parameter(String name, boolean dynamic) {
    }

    public enum ReturnShape {
        SINGLE,
        OPTIONAL,
        ITERABLE,
        PAGE,
        INT,
        LONG,
        BOOLEAN,
        VOID;

        static ReturnShape of(Class<?> type) {
            if (type == Page.class) {
                return PAGE;
            }
            if (type == Optional.class) {
                return OPTIONAL;
            }
            if (Iterable.class.isAssignableFrom(type)) {
                return ITERABLE;
            }
            if (type == int.class) {
                return INT;
            }
            if (type == long.class) {
                return LONG;
            }
            if (type == boolean.class) {
                return BOOLEAN;
            }
            if (type == void.class) {
                return VOID;
            }
            return SINGLE;
        }
    }

    public void validateParameter(int index, Object value) {
        if (value == null && !parameters.get(index).dynamic()) {
            throwNullParameterException(index);
        }
    }

    void throwNullParameterException(int index) {
        throw new NullPointerException("The parameters[" + index + "](" + parameters.get(index).name() + ") of \""
                + methodDescription + "\" cannot be null. If you want to use dynamic queries, "
                + "that is, ignore this parameter, please annotate this parameter with \"@"
                + DynamicParam.class.getName() + "\"");
    }
}
