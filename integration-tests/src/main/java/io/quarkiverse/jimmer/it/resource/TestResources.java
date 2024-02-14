package io.quarkiverse.jimmer.it.resource;

import java.util.List;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.babyfish.jimmer.sql.JSqlClient;

import io.quarkiverse.jimmer.it.entity.Book;
import io.quarkiverse.jimmer.it.entity.BookFetcher;
import io.quarkiverse.jimmer.it.entity.Tables;
import io.quarkiverse.jimmer.runtime.Jimmer;

@Path("/testResources")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class TestResources {

    @GET
    @Path("/test")
    public Response test() {
        JSqlClient defaultJSqlClient = Jimmer.getDefaultJSqlClient();
        List<Book> books = defaultJSqlClient
                .createQuery(Tables.BOOK_TABLE)
                .select(Tables.BOOK_TABLE.fetch(BookFetcher.$.allTableFields()))
                .execute();
        return Response.ok(books).build();
    }
}
