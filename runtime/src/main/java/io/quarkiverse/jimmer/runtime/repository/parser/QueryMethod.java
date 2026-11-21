package io.quarkiverse.jimmer.runtime.repository.parser;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;

import org.babyfish.jimmer.meta.ImmutableType;

import io.quarkiverse.jimmer.runtime.repository.DynamicParam;

public class QueryMethod {

    private final Method javaMethod;

    private final QueryPlan plan;

    private final Class<?> viewType;

    private final int pageableParamIndex;

    private final int sortParamIndex;

    private final int specificationParamIndex;

    private final int fetcherParamIndex;

    private final int viewTypeParamIndex;

    public QueryMethod(
            Method javaMethod,
            Query query,
            Class<?> viewType,
            int pageableParamIndex,
            int sortParamIndex,
            int specificationParamIndex,
            int fetcherParamIndex,
            int viewTypeParamIndex) {
        this.javaMethod = javaMethod;
        this.viewType = viewType;
        this.pageableParamIndex = pageableParamIndex;
        this.sortParamIndex = sortParamIndex;
        this.specificationParamIndex = specificationParamIndex;
        this.fetcherParamIndex = fetcherParamIndex;
        this.viewTypeParamIndex = viewTypeParamIndex;
        Parameter[] parameters = javaMethod.getParameters();
        List<QueryPlan.Parameter> planParameters = new ArrayList<>(parameters.length);
        for (Parameter parameter : parameters) {
            planParameters.add(new QueryPlan.Parameter(parameter.getName(), parameter.isAnnotationPresent(DynamicParam.class)));
        }
        this.plan = new QueryPlan(query, QueryPlan.ReturnShape.of(javaMethod.getReturnType()), javaMethod.toString(),
                planParameters);
    }

    public static QueryMethod of(Context ctx, ImmutableType type, Method method) {
        return QueryMethodParser.parse(ctx, type, method);
    }

    public Method getJavaMethod() {
        return javaMethod;
    }

    public Query getQuery() {
        return plan.query();
    }

    public QueryPlan getPlan() {
        return plan;
    }

    public Class<?> getViewType() {
        return viewType;
    }

    public int getPageableParamIndex() {
        return pageableParamIndex;
    }

    public int getSortParamIndex() {
        return sortParamIndex;
    }

    public int getSpecificationParamIndex() {
        return specificationParamIndex;
    }

    public int getFetcherParamIndex() {
        return fetcherParamIndex;
    }

    public int getViewTypeParamIndex() {
        return viewTypeParamIndex;
    }

    public boolean isDynamicParam(int index) {
        return plan.parameters().get(index).dynamic();
    }

    public void throwNullParameterException(int index) {
        plan.throwNullParameterException(index);
    }

    @Override
    public String toString() {
        return "QueryMethod{" +
                "javaMethod=" + javaMethod +
                ", query=" + plan.query() +
                ", viewType=" + viewType +
                ", pageableParamIndex=" + pageableParamIndex +
                ", sortParamIndex=" + sortParamIndex +
                ", fetcherParamIndex=" + fetcherParamIndex +
                ", viewTypeParamIndex=" + viewTypeParamIndex +
                '}';
    }
}
