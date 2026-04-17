package io.quarkiverse.jimmer.runtime.graphql.facade;

import java.util.List;
import java.util.Map;

import org.babyfish.jimmer.meta.ImmutableProp;
import org.babyfish.jimmer.runtime.ImmutableSpi;

import graphql.execution.MergedField;
import graphql.language.Field;
import graphql.language.FragmentDefinition;
import graphql.language.FragmentSpread;
import graphql.language.InlineFragment;
import graphql.language.Selection;
import graphql.language.SelectionSet;
import graphql.schema.DataFetchingEnvironment;

final class JimmerGraphQLSelectionInspector {

    private final DataFetchingEnvironment env;

    JimmerGraphQLSelectionInspector(DataFetchingEnvironment env) {
        this.env = env;
    }

    boolean isUnloaded(Object value) {
        MergedField mergedField = env.getMergedField();
        SelectionSet selectionSet = mergedField.getSingleField().getSelectionSet();
        if (selectionSet == null) {
            return false;
        }
        if (value instanceof List<?> list) {
            for (Object element : list) {
                if (element instanceof ImmutableSpi spi && isUnloaded(spi, selectionSet)) {
                    return true;
                }
            }
            return false;
        }
        return value instanceof ImmutableSpi spi && isUnloaded(spi, selectionSet);
    }

    private boolean isUnloaded(ImmutableSpi spi, SelectionSet selectionSet) {
        for (Selection<?> selection : selectionSet.getSelections()) {
            if (selection instanceof FragmentSpread fragmentSpread) {
                if (isUnloaded(spi, fragmentSpread)) {
                    return true;
                }
            } else if (selection instanceof InlineFragment inlineFragment) {
                if (isUnloaded(spi, inlineFragment)) {
                    return true;
                }
            } else if (selection instanceof Field field && isUnloaded(spi, field)) {
                return true;
            }
        }
        return false;
    }

    private boolean isUnloaded(ImmutableSpi spi, Field field) {
        if (field.getArguments() != null && !field.getArguments().isEmpty()) {
            return false;
        }
        Map<String, ImmutableProp> props = spi.__type().getProps();
        ImmutableProp prop = props.get(field.getName());
        return prop != null && !spi.__isLoaded(prop.getId());
    }

    private boolean isUnloaded(ImmutableSpi spi, FragmentSpread fragmentSpread) {
        FragmentDefinition definition = env.getFragmentsByName().get(fragmentSpread.getName());
        return definition != null && isUnloaded(spi, definition.getSelectionSet());
    }

    private boolean isUnloaded(ImmutableSpi spi, InlineFragment inlineFragment) {
        return isUnloaded(spi, inlineFragment.getSelectionSet());
    }
}
