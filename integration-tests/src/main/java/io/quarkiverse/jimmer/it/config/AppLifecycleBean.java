package io.quarkiverse.jimmer.it.config;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.agroal.api.AgroalDataSource;
import io.quarkus.runtime.LaunchMode;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;

@ApplicationScoped
public class AppLifecycleBean {

    private static final Logger LOGGER = LoggerFactory.getLogger(AppLifecycleBean.class);

    @Inject
    AgroalDataSource agroalDataSource;

    void onStart(@Observes StartupEvent ev) throws SQLException {
        LOGGER.info("Default Charset = " + Charset.defaultCharset());
        LOGGER.info("file.encoding = " + Charset.defaultCharset().displayName());
        LOGGER.info("Default Charset in use = " + this.getDefaultCharset());
        LOGGER.info("The application is starting...");
        LOGGER.info("The application model is " + LaunchMode.current().getDefaultProfile());

        Connection connection = agroalDataSource.getConnection();
        try (Statement statement = connection.createStatement()) {
            statement.execute(
                    """

                            CREATE TABLE book_store (
                            	id int NOT NULL,
                            	name text NOT NULL,
                            	website text NULL,
                            	CONSTRAINT book_store_pkey PRIMARY KEY (id),
                            	CONSTRAINT business_key_book_store UNIQUE (name)
                            );

                            CREATE TABLE book (
                                id int NOT NULL,
                                name text NOT NULL,
                                edition int4 NOT NULL,
                                price numeric(10, 2) NOT NULL,
                                store_id int NOT NULL,
                                tenant varchar NULL,
                                CONSTRAINT book_pkey PRIMARY KEY (id),
                                CONSTRAINT business_key_book UNIQUE (name, edition),
                                CONSTRAINT fk_book__book_store FOREIGN KEY (store_id) REFERENCES book_store(id)
                            );

                            INSERT INTO public.book_store
                            (id, name, website)
                            VALUES(1, 'O''REILLY', NULL);
                            INSERT INTO public.book_store
                            (id, name, website)
                            VALUES(2, 'MANNING', NULL);

                            INSERT INTO public.book
                            (id, name, edition, price, store_id, tenant)
                            VALUES(1, 'Learning GraphQL', 1, 50.00, 1, '1');
                            INSERT INTO public.book
                            (id, name, edition, price, store_id, tenant)
                            VALUES(2, 'Learning GraphQL', 2, 55.00, 1, '1');
                            INSERT INTO public.book
                            (id, name, edition, price, store_id, tenant)
                            VALUES(3, 'Learning GraphQL', 3, 51.00, 1, '1');
                            INSERT INTO public.book
                            (id, name, edition, price, store_id, tenant)
                            VALUES(4, 'Effective TypeScript', 1, 73.00, 1, '1');
                            INSERT INTO public.book
                            (id, name, edition, price, store_id, tenant)
                            VALUES(5, 'Effective TypeScript', 2, 69.00, 1, '1');
                            INSERT INTO public.book
                            (id, name, edition, price, store_id, tenant)
                            VALUES(6, 'Effective TypeScript', 3, 88.00, 1, '1');
                            INSERT INTO public.book
                            (id, name, edition, price, store_id, tenant)
                            VALUES(7, 'Programming TypeScript', 1, 47.50, 1, '1');
                            INSERT INTO public.book
                            (id, name, edition, price, store_id, tenant)
                            VALUES(8, 'Programming TypeScript', 2, 45.00, 1, '1');
                            INSERT INTO public.book
                            (id, name, edition, price, store_id, tenant)
                            VALUES(9, 'Programming TypeScript', 3, 48.00, 1, '1');
                            INSERT INTO public.book
                            (id, name, edition, price, store_id, tenant)
                            VALUES(10, 'GraphQL in Action', 1, 80.00, 2, '1');
                            INSERT INTO public.book
                            (id, name, edition, price, store_id, tenant)
                            VALUES(11, 'GraphQL in Action', 2, 81.00, 2, '1');
                            INSERT INTO public.book
                            (id, name, edition, price, store_id, tenant)
                            VALUES(12, 'GraphQL in Action', 3, 80.00, 2, '2');

                            """);
        }
    }

    private String getDefaultCharset() {
        OutputStreamWriter outputStreamWriter = new OutputStreamWriter(new ByteArrayOutputStream());
        return outputStreamWriter.getEncoding();
    }

    void onStop(@Observes ShutdownEvent ev) {
        LOGGER.info("The application is stopping...");
    }
}
