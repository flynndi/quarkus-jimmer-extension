package io.quarkiverse.jimmer.runtime.repository.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.babyfish.jimmer.meta.ImmutableProp;
import org.babyfish.jimmer.meta.ImmutableType;
import org.babyfish.jimmer.meta.TargetLevel;

public class Path {

    private final Source source;

    private final List<ImmutableProp> props;

    public static Path of(Context ctx, boolean allowCollection, Source source, ImmutableType type) {
        return new PathParser(ctx, allowCollection).parse(source, type);
    }

    /** Bind the exact property names emitted at build time to the runtime entity metadata. */
    public static Path resolved(ImmutableType root, String... propNames) {
        List<ImmutableProp> props = new ArrayList<>(propNames.length);
        for (int i = 0; i < propNames.length; i++) {
            ImmutableType type = i == 0 ? root : props.get(i - 1).getTargetType();
            props.add(type.getProp(propNames[i]));
        }
        return new Path(null, props);
    }

    public Path(Source source, List<ImmutableProp> props) {
        this.source = source;
        this.props = List.copyOf(props);
    }

    public Source getSource() {
        return source != null ? source : new Source(toString());
    }

    public List<ImmutableProp> getProps() {
        return props;
    }

    public boolean isScalar() {
        return props.get(props.size() - 1).isScalar(TargetLevel.PERSISTENT);
    }

    public Class<?> getType() {
        return props.get(props.size() - 1).getElementClass();
    }

    @Override
    public String toString() {
        return props.stream().map(ImmutableProp::getName).collect(Collectors.joining("."));
    }
}
