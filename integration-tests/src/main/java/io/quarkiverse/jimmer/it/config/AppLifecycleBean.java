package io.quarkiverse.jimmer.it.config;

import java.io.*;
import java.nio.charset.Charset;
import java.sql.Connection;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.agroal.api.AgroalDataSource;
import io.quarkus.agroal.DataSource;
import io.quarkus.runtime.LaunchMode;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;

@ApplicationScoped
public class AppLifecycleBean {

    private static final Logger LOGGER = LoggerFactory.getLogger(AppLifecycleBean.class);

    @Inject
    AgroalDataSource agroalDataSource;

    @Inject
    @DataSource("DB2")
    AgroalDataSource agroalDataSourceDB2;

    void onStart(@Observes StartupEvent ev) throws Exception {
        LOGGER.info("Default Charset = " + Charset.defaultCharset());
        LOGGER.info("file.encoding = " + Charset.defaultCharset().displayName());
        LOGGER.info("Default Charset in use = " + this.getDefaultCharset());
        LOGGER.info("The application is starting...");
        LOGGER.info("The application model is " + LaunchMode.current().getDefaultProfile());
        this.initH2DB1();
        this.initH2DB2();
    }

    private String getDefaultCharset() {
        OutputStreamWriter outputStreamWriter = new OutputStreamWriter(new ByteArrayOutputStream());
        return outputStreamWriter.getEncoding();
    }

    void onStop(@Observes ShutdownEvent ev) {
        LOGGER.info("The application is stopping...");
    }

    private void initH2DB1() throws Exception {
        try (Connection con = agroalDataSource.getConnection()) {
            InputStream inputStream = AppLifecycleBean.class
                    .getClassLoader()
                    .getResourceAsStream("h2-database.sql");
            if (inputStream == null) {
                throw new RuntimeException("no `h2-database.sql`");
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
                con.createStatement().execute(builder.toString());
            }
        }
    }

    private void initH2DB2() throws Exception {
        try (Connection con = agroalDataSourceDB2.getConnection()) {
            InputStream inputStream = AppLifecycleBean.class
                    .getClassLoader()
                    .getResourceAsStream("h2-database2.sql");
            if (inputStream == null) {
                throw new RuntimeException("no `h2-database2.sql`");
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
                con.createStatement().execute(builder.toString());
            }
        }
    }
}
