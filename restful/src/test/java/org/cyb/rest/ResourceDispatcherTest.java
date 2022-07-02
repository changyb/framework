package org.cyb.rest;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.core.*;
import jakarta.ws.rs.ext.RuntimeDelegate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.*;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ResourceDispatcherTest {

    private RuntimeDelegate delegate;

    @BeforeEach
    public void before() {
        delegate = Mockito.mock(RuntimeDelegate.class);
        RuntimeDelegate.setInstance(delegate);
        when(delegate.createResponseBuilder()).thenReturn(new Response.ResponseBuilder() {
            private Object entity;
            private int status;

            @Override
            public Response build() {
                OutboundResponse response = Mockito.mock(OutboundResponse.class);
                when(response.getEntity()).thenReturn(entity);
                return response;
            }

            @Override
            public Response.ResponseBuilder clone() {
                return null;
            }

            @Override
            public Response.ResponseBuilder status(int i) {
                return null;
            }

            @Override
            public Response.ResponseBuilder status(int status, String s) {
                this.status = status;
                return this;
            }

            @Override
            public Response.ResponseBuilder entity(Object o) {
                this.entity = o;
                return this;
            }

            @Override
            public Response.ResponseBuilder entity(Object o, Annotation[] annotations) {
                return null;
            }

            @Override
            public Response.ResponseBuilder allow(String... strings) {
                return null;
            }

            @Override
            public Response.ResponseBuilder allow(Set<String> set) {
                return null;
            }

            @Override
            public Response.ResponseBuilder cacheControl(CacheControl cacheControl) {
                return null;
            }

            @Override
            public Response.ResponseBuilder encoding(String s) {
                return null;
            }

            @Override
            public Response.ResponseBuilder header(String s, Object o) {
                return null;
            }

            @Override
            public Response.ResponseBuilder replaceAll(MultivaluedMap<String, Object> multivaluedMap) {
                return null;
            }

            @Override
            public Response.ResponseBuilder language(String s) {
                return null;
            }

            @Override
            public Response.ResponseBuilder language(Locale locale) {
                return null;
            }

            @Override
            public Response.ResponseBuilder type(MediaType mediaType) {
                return null;
            }

            @Override
            public Response.ResponseBuilder type(String s) {
                return null;
            }

            @Override
            public Response.ResponseBuilder variant(Variant variant) {
                return null;
            }

            @Override
            public Response.ResponseBuilder contentLocation(URI uri) {
                return null;
            }

            @Override
            public Response.ResponseBuilder cookie(NewCookie... newCookies) {
                return null;
            }

            @Override
            public Response.ResponseBuilder expires(Date date) {
                return null;
            }

            @Override
            public Response.ResponseBuilder lastModified(Date date) {
                return null;
            }

            @Override
            public Response.ResponseBuilder location(URI uri) {
                return null;
            }

            @Override
            public Response.ResponseBuilder tag(EntityTag entityTag) {
                return null;
            }

            @Override
            public Response.ResponseBuilder tag(String s) {
                return null;
            }

            @Override
            public Response.ResponseBuilder variants(Variant... variants) {
                return null;
            }

            @Override
            public Response.ResponseBuilder variants(List<Variant> list) {
                return null;
            }

            @Override
            public Response.ResponseBuilder links(Link... links) {
                return null;
            }

            @Override
            public Response.ResponseBuilder link(URI uri, String s) {
                return null;
            }

            @Override
            public Response.ResponseBuilder link(String s, String s1) {
                return null;
            }
        });

    }

    @Test
    public void should() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        ResourceContext context = mock(ResourceContext.class);

        when(request.getServletPath()).thenReturn("/users");
        when(context.getResource(eq(Users.class))).thenReturn(new Users());
        Router router = new Router(Users.class);

        OutboundResponse response = router.dispatch(request, context);
        GenericEntity<String> entity = (GenericEntity<String>)response.getEntity();
        assertEquals("all", entity.getEntity());
    }

    static class Router implements ResourceRouter {
        private Map<Pattern, Class<?>> routerTable = new HashMap<>();

        public Router(Class<Users> rootResource) {
            Path path = rootResource.getAnnotation(Path.class);
            routerTable.put(Pattern.compile(path.value() + "(/.*)?"), rootResource);
        }

        @Override
        public OutboundResponse dispatch(HttpServletRequest request, ResourceContext resourceContext) {
            String path = request.getServletPath();

            Pattern matched = routerTable.keySet().stream().filter(pattern -> pattern.matcher(path).matches()).findFirst().get();
            Class<?> resource = routerTable.get(matched);

            Method method = Arrays.stream(resource.getMethods()).filter(m -> m.isAnnotationPresent(GET.class)).findFirst().get();
            Object object = resourceContext.getResource(resource);

            try {
                Object result = method.invoke(object);
                GenericEntity entity = new GenericEntity(result, method.getGenericReturnType());
                return (OutboundResponse) Response.ok(entity).build();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Path("/users")
    static class Users {
        @GET
        @Produces(MediaType.TEXT_PLAIN)
        public String asText() {
            return "all";
        }

        @GET
        @Path("{id}")
        @Produces(MediaType.TEXT_HTML)
        public String asHTML(@PathParam("id") int id) {
            return "all";
        }
    }
}
