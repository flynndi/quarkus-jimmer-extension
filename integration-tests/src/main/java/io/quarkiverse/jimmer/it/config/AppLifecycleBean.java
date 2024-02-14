package io.quarkiverse.jimmer.it.config;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkus.runtime.LaunchMode;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;

@ApplicationScoped
public class AppLifecycleBean {

    private static final Logger LOGGER = LoggerFactory.getLogger(AppLifecycleBean.class);

    void onStart(@Observes StartupEvent ev) {
        LOGGER.info("Default Charset = " + Charset.defaultCharset());
        LOGGER.info("file.encoding = " + Charset.defaultCharset().displayName());
        LOGGER.info("Default Charset in use = " + this.getDefaultCharset());
        LOGGER.info("The application is starting...");
        LOGGER.info("The application model is " + LaunchMode.current().getDefaultProfile());
    }

    private String getDefaultCharset() {
        OutputStreamWriter outputStreamWriter = new OutputStreamWriter(new ByteArrayOutputStream());
        return outputStreamWriter.getEncoding();
    }

    void onStop(@Observes ShutdownEvent ev) {
        LOGGER.info("The application is stopping...");
    }
}
