package io.quarkiverse.jimmer.test.model.deletion;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import org.babyfish.jimmer.sql.Entity;
import org.babyfish.jimmer.sql.Id;
import org.babyfish.jimmer.sql.LogicalDeleted;
import org.babyfish.jimmer.sql.meta.LogicalDeletedValueGenerator;

@Entity
public interface ArchivedBook {
    @Id
    long id();

    @LogicalDeleted(generatorType = MarkerGenerator.class)
    long deletedMarker();

    @Singleton
    class MarkerGenerator implements LogicalDeletedValueGenerator<Long> {
        private final MarkerSource source;

        @Inject
        MarkerGenerator(MarkerSource source) {
            this.source = source;
        }

        @Override
        public Long generate() {
            return source.value();
        }
    }

    @Singleton
    @Named("bookMarkerSource")
    class MarkerSource {
        long value() {
            return 73L;
        }
    }
}
