package io.quarkiverse.jimmer.deployment.jackson;

import org.babyfish.jimmer.sql.Embeddable;

@Embeddable
public interface NativeAddress {
    String street();
}
