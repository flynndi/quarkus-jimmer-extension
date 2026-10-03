package io.quarkiverse.jimmer.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.transaction.Status;
import jakarta.transaction.SystemException;
import jakarta.transaction.TransactionManager;
import jakarta.transaction.Transactional;

import org.babyfish.jimmer.sql.JSqlClient;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.jimmer.runtime.repo.support.AbstractJavaRepository;
import io.quarkiverse.jimmer.test.model.CdiBook;
import io.quarkus.test.QuarkusUnitTest;

class RepositoryInterceptionTest {

    @RegisterExtension
    static final QuarkusUnitTest app = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addPackage(CdiBook.class.getPackage())
                    .addClass(TransactionalBookRepository.class)
                    .addAsResource(new StringAsset(CdiBook.class.getName() + "\n"), "META-INF/jimmer/entities"))
            .overrideConfigKey("quarkus.datasource.db-kind", "h2")
            .overrideConfigKey("quarkus.datasource.jdbc.url", "jdbc:h2:mem:repository-interception;DB_CLOSE_DELAY=-1")
            .overrideConfigKey("quarkus.datasource.devservices.enabled", "false")
            .overrideConfigKey("quarkus.redis.devservices.enabled", "false")
            .overrideConfigKey("quarkus.redis.hosts", "redis://127.0.0.1:1");

    @Inject
    TransactionalBookRepository repository;

    @Inject
    TransactionManager transactionManager;

    @Test
    void resolvesEntityTypeWhenArcConstructsAnInterceptedRepository() throws SystemException {
        assertNotEquals(TransactionalBookRepository.class, repository.getClass());
        assertEquals(CdiBook.class, repository.entityClass());
        assertEquals(Status.STATUS_ACTIVE, repository.transactionStatus());
        assertEquals(Status.STATUS_NO_TRANSACTION, transactionManager.getStatus());
    }

    @Singleton
    @Transactional
    public static class TransactionalBookRepository extends AbstractJavaRepository<CdiBook, Long> {

        private final TransactionManager transactionManager;

        public TransactionalBookRepository(JSqlClient sql, TransactionManager transactionManager) {
            super(sql);
            this.transactionManager = transactionManager;
        }

        public Class<CdiBook> entityClass() {
            return entityType;
        }

        public int transactionStatus() throws SystemException {
            return transactionManager.getStatus();
        }
    }
}
