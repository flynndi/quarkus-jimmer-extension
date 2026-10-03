package io.quarkiverse.jimmer.test.model;

import org.babyfish.jimmer.View;
import org.babyfish.jimmer.sql.fetcher.DtoMetadata;

/** A small fixture with the same metadata contract as a Jimmer-generated DTO. */
public record CdiBookView(long id, String name) implements View<CdiBook> {

    public static final DtoMetadata<CdiBook, CdiBookView> METADATA = new DtoMetadata<>(
            CdiBookView.class, CdiBookFetcher.$.allScalarFields(),
            book -> new CdiBookView(book.id(), book.name()));

    @Override
    public CdiBook toEntity() {
        return CdiBookDraft.$.produce(draft -> draft.setId(id).setName(name));
    }
}
