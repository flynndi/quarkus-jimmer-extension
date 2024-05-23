//package io.quarkiverse.jimmer.deployment;
//
//import net.bytebuddy.ByteBuddy;
//import net.bytebuddy.agent.ByteBuddyAgent;
//import net.bytebuddy.dynamic.loading.ClassReloadingStrategy;
//import net.bytebuddy.implementation.FixedValue;
//import net.bytebuddy.matcher.ElementMatchers;
//
//public final class PostgresDialectOverriderGenerator {
//
//    public void overriderClass() {
//        ByteBuddyAgent.install();
//        String className = "org.babyfish.jimmer.sql.dialect.PostgresDialect";
//        Class<?> postgresDialectClass = null;
//        try {
//            postgresDialectClass = Class.forName(className, true, Thread.currentThread().getContextClassLoader());
//        } catch (ClassNotFoundException e) {
//            throw new RuntimeException(e);
//        }
//
//        //        String pgObjectClassName = "org.postgresql.util.PGobject";
//        //        Class<?> pgObjectClass = null;
//        //        try {
//        //            pgObjectClass = Class.forName(pgObjectClassName);
//        //        } catch (ClassNotFoundException e) {
//        //            return;
//        //        }
//
//        new ByteBuddy()
//                .redefine(postgresDialectClass)
//                .method(ElementMatchers.named("getJsonBaseType"))
//                .intercept(FixedValue.nullValue())
//                .method(ElementMatchers.named("jsonToBaseValue"))
//                .intercept(FixedValue.nullValue())
//                .method(ElementMatchers.named("baseValueToJson"))
//                .intercept(FixedValue.nullValue())
//                .method(ElementMatchers.named("unknownReader"))
//                .intercept(FixedValue.nullValue())
//                .make()
//                .load(Thread.currentThread().getContextClassLoader(), ClassReloadingStrategy.fromInstalledAgent());
//    }
//}
