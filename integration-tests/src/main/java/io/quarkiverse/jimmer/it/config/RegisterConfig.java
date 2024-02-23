package io.quarkiverse.jimmer.it.config;

import io.quarkiverse.jimmer.it.entity.*;
import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection(targets =
        {
                Author.class,
                AuthorDraft.class,
                AuthorDraft.Producer.class,
                AuthorFetcher.class,
                AuthorProps.class,
                AuthorTable.class,
                AuthorTableEx.class,
                Book.class,
                BookDraft.class,
                BookDraft.Producer.class,
                BookFetcher.class,
                BookProps.class,
                BookStore.class,
                BookStoreDraft.class,
                BookStoreDraft.Producer.class,
                BookStoreFetcher.class,
                BookStoreProps.class,
                BookStoreTable.class,
                BookStoreTableEx.class,
                BookTable.class,
                BookTableEx.class,
                Fetchers.class,
                Gender.class,
                Objects.class,
                TableExes.class,
                Tables.class,
                TenantAware.class,
                TenantAwareDraft.class,
                TenantAwareDraft.Producer.class,
                TenantAwareProps.class,
                UserRole.class,
                UserRoleDraft.class,
                UserRoleDraft.Producer.class,
                UserRoleFetcher.class,
                UserRoleProps.class,
                UserRoleTable.class,
                UserRoleTableEx.class
        })
public class RegisterConfig {
}
