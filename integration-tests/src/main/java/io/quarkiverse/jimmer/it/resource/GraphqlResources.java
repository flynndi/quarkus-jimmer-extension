//package io.quarkiverse.jimmer.it.resource;
//
//import java.util.List;
//
//import org.eclipse.microprofile.graphql.GraphQLApi;
//import org.eclipse.microprofile.graphql.Query;
//
//import graphql.schema.DataFetchingEnvironment;
//import io.quarkiverse.jimmer.it.entity.BookStore;
//import io.quarkiverse.jimmer.it.entity.Tables;
//import io.quarkiverse.jimmer.it.entity.UserRole;
//import io.quarkiverse.jimmer.runtime.Jimmer;
//import io.quarkiverse.jimmer.runtime.graphql.DataFetchingEnvironments;
//import io.smallrye.graphql.api.Context;
//
//@GraphQLApi
//public class GraphqlResources {
//
//    @Query("graphqlTest")
//    public List<UserRole> getAllUserRole(Context context) {
//        DataFetchingEnvironment env = context.unwrap(DataFetchingEnvironment.class);
//        List<UserRole> db2 = Jimmer.getJSqlClient("DB2")
//                .createQuery(Tables.USER_ROLE_TABLE)
//                .select(
//                        Tables.USER_ROLE_TABLE.fetch(
//                                DataFetchingEnvironments.createFetcher(
//                                        UserRole.class,
//                                        env)))
//                .execute();
//        System.out.println("db2 = " + db2);
//        return db2;
//    }
//
//    @Query("bookStores")
//    public boolean getAllBookStore(Context context) {
//        DataFetchingEnvironment env = context.unwrap(DataFetchingEnvironment.class);
//        List<BookStore> execute = Jimmer.getDefaultJSqlClient()
//                .createQuery(Tables.BOOK_STORE_TABLE)
//                .select(Tables.BOOK_STORE_TABLE.fetch(DataFetchingEnvironments.createFetcher(BookStore.class, env)))
//                .execute();
//        System.out.println("execute = " + execute);
//        return false;
//    }
//}
