package io.quarkiverse.jimmer.test;

import java.sql.SQLException;

import javax.sql.DataSource;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;

import io.quarkiverse.jimmer.test.model.CdiBook;
import io.quarkus.test.QuarkusUnitTest;

public final class RepositoryStartupTestSupport {

    private RepositoryStartupTestSupport() {
    }

    public static QuarkusUnitTest application(String language, Class<?>... beans) {
        return new QuarkusUnitTest()
                .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                        .addPackage(CdiBook.class.getPackage())
                        .addClasses(beans)
                        .addClass(RepositoryStartupTestSupport.class)
                        .addAsResource(new StringAsset(CdiBook.class.getName() + "\n"), "META-INF/jimmer/entities"))
                .overrideConfigKey("quarkus.jimmer.language", language)
                .overrideConfigKey("quarkus.jimmer.database-validation-mode", "ERROR")
                .overrideConfigKey("quarkus.jimmer.trigger-type", "BINLOG_ONLY")
                .overrideConfigKey("quarkus.jimmer.books.trigger-type", "BINLOG_ONLY")
                .overrideConfigKey("quarkus.datasource.devservices.enabled", "false")
                .overrideConfigKey("quarkus.redis.devservices.enabled", "false")
                .overrideConfigKey("quarkus.datasource.db-kind", "h2")
                .overrideConfigKey("quarkus.datasource.jdbc.url",
                        "jdbc:h2:mem:repository-startup-" + language + "-default;DB_CLOSE_DELAY=-1")
                .overrideConfigKey("quarkus.datasource.books.db-kind", "h2")
                .overrideConfigKey("quarkus.datasource.books.jdbc.url",
                        "jdbc:h2:mem:repository-startup-" + language + "-named;DB_CLOSE_DELAY=-1");
    }

    public static void createSchema(DataSource dataSource, String bookName) throws SQLException {
        try (var connection = dataSource.getConnection();
                var statement = connection.createStatement()) {
            // No init script creates this table: repository construction must leave it to the startup observer.
            statement.execute("create table CDI_BOOK (ID bigint primary key, NAME varchar(100) not null)");
            try (var insert = connection.prepareStatement("insert into CDI_BOOK(ID, NAME) values (1, ?)")) {
                insert.setString(1, bookName);
                insert.executeUpdate();
            }
        }
    }
}
