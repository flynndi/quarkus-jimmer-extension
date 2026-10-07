package io.quarkiverse.jimmer.deployment.jackson;

import org.babyfish.jimmer.sql.Id;
import org.babyfish.jimmer.sql.MappedSuperclass;

@MappedSuperclass
public interface NativeBase {
    @Id
    long id();
}
