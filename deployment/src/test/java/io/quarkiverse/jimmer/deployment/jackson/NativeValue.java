package io.quarkiverse.jimmer.deployment.jackson;

import org.babyfish.jimmer.Immutable;

@Immutable
public interface NativeValue {
    String text();
}
