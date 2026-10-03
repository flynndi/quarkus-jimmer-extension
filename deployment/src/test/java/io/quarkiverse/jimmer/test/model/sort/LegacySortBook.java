package io.quarkiverse.jimmer.test.model.sort;

import org.babyfish.jimmer.sql.Entity;
import org.babyfish.jimmer.sql.Id;
import org.babyfish.jimmer.sql.ManyToOne;
import org.jetbrains.annotations.Nullable;

@Entity
public interface LegacySortBook {
    @Id
    long id();

    @Nullable
    String name();

    @Nullable
    @ManyToOne
    LegacySortBook parent();
}
