package io.quarkiverse.jimmer.it.config;

import java.io.*;
import java.sql.Connection;
import java.sql.Statement;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.agroal.api.AgroalDataSource;
import io.quarkus.agroal.DataSource;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;

@ApplicationScoped
public class AppLifecycleBean {

    private static final Logger LOGGER = LoggerFactory.getLogger(AppLifecycleBean.class);

    @Inject
    AgroalDataSource agroalDataSource;

    @Inject
    @DataSource(Constant.DATASOURCE2)
    AgroalDataSource agroalDataSourceDB2;

    void onStart(@Observes StartupEvent ev) throws Exception {
        LOGGER.info("The application is starting...");
        this.initH2DB1();
        this.initH2DB2();
    }

    void onStop(@Observes ShutdownEvent ev) {
        LOGGER.info("The application is stopping...");
    }

    private void initH2DB1() throws Exception {
        this.readResource(agroalDataSource, "h2-database.sql");
    }

    private void initH2DB2() throws Exception {
        this.readResource(agroalDataSourceDB2, "h2-database2.sql");
    }

    private void readResource(AgroalDataSource agroalDataSource, String resourceName) throws Exception {
        try (Connection connection = agroalDataSource.getConnection()) {
            InputStream inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourceName);
            if (inputStream == null) {
                throw new RuntimeException("no " + resourceName);
            }
            try (Reader reader = new InputStreamReader(inputStream)) {
                char[] buf = new char[1024];
                StringBuilder builder = new StringBuilder();
                while (true) {
                    int len = reader.read(buf);
                    if (len == -1) {
                        break;
                    }
                    builder.append(buf, 0, len);
                }
                try (Statement statement = connection.createStatement()) {
                    statement.execute(builder.toString());
                }
            }
        }
    }
}
