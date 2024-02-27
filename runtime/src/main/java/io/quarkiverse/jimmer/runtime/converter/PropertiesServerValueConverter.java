//package io.quarkiverse.jimmer.runtime.converter;
//
//import static io.quarkus.runtime.configuration.ConverterSupport.DEFAULT_QUARKUS_CONVERTER_PRIORITY;
//
//import java.io.Serializable;
//
//import jakarta.annotation.Priority;
//
//import org.eclipse.microprofile.config.spi.Converter;
//
//import io.quarkiverse.jimmer.runtime.cfg.JimmerBuildTimeConfig1;
//
//@Priority(DEFAULT_QUARKUS_CONVERTER_PRIORITY)
//public class PropertiesServerValueConverter implements Converter<JimmerBuildTimeConfig1.Server>, Serializable {
//
//    private static final long serialVersionUID = 4452863383998867844L;
//
//    @Override
//    public JimmerBuildTimeConfig1.Server convert(String s) throws IllegalArgumentException, NullPointerException {
//        return s.isEmpty() ? null : new JimmerBuildTimeConfig1.Server();
//    }
//}
