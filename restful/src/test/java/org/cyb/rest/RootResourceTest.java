package org.cyb.rest;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RootResourceTest {

    @Test
    public void should_get_uri_template_from_path_annotation() {
        ResourceRouter.RootResource resource = new RootResourceClass(Messages.class);
        UriTemplate template = resource.getUriTemplate();

        assertTrue(template.match("/messages/hello").isPresent());
    }

    @ParameterizedTest
    @CsvSource({"GET,/messages/hello,Messages.hello","GET,/messages/ah,Messages.ah"})
    public void should_match_resource_method(String httpMethod, String path, String resourceMethod) {
        ResourceRouter.RootResource resource = new RootResourceClass(Messages.class);
        ResourceRouter.ResourceMethod method = resource.matches(path, httpMethod, new String[]{MediaType.TEXT_PLAIN}, Mockito.mock(UriInfoBuilder.class)).get();
        assertEquals(resourceMethod, method.toString());
    }


    @Path("/messages")
    static class Messages {

        @GET
        @Path("/ah")
        @Produces(MediaType.TEXT_PLAIN)
        public String ah() {
            return "ah";
        }

       @GET
       @Path("/hello")
       @Produces(MediaType.TEXT_PLAIN)
       public String hello() {
           return "hello";
       }
    }
}
