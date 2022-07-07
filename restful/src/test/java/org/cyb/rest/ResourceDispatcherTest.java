package org.cyb.rest;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ResourceDispatcherTest {

    private RuntimeDelegate delegate;

    private Runtime runtime;

    private HttpServletRequest request;

    private ResourceContext context;

    private UriInfoBuilder builder;

    @BeforeEach
    public void before() {
        runtime = mock(Runtime.class);
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

        request = mock(HttpServletRequest.class);
        context = mock(ResourceContext.class);
        when(request.getServletPath()).thenReturn("/users");
        when(request.getMethod()).thenReturn("GET");
        when(request.getHeaders(eq(HttpHeaders.ACCEPT))).thenReturn(
          new Vector<>(List.of(MediaType.WILDCARD)).elements()
        );

        builder = mock(UriInfoBuilder.class);
        when(runtime.createUriInfoBuilder(same(request))).thenReturn(builder);
    }

    @Test
    public void should_use_matched_root_resource() {
        ResourceRouter.RootResource matched = mock(ResourceRouter.RootResource.class);
        UriTemplate matchedUriTemplate = mock(UriTemplate.class);
        UriTemplate.MatchResult result = mock(UriTemplate.MatchResult.class);
        when(matched.getUriTemplate()).thenReturn(matchedUriTemplate);
        when(matchedUriTemplate.match(eq("/users"))).thenReturn(Optional.of(result));
        ResourceRouter.ResourceMethod method = mock(ResourceRouter.ResourceMethod.class);
        when(matched.matches(eq("/users"), eq("GET"),
                eq(new String[]{MediaType.WILDCARD}), eq(builder))).thenReturn(Optional.of(method));
        GenericEntity entity = new GenericEntity("matched", String.class);
        when(method.call(any(), any())).thenReturn(entity);

        ResourceRouter.RootResource unmatched = mock(ResourceRouter.RootResource.class);
        UriTemplate unmatchedUriTemplate = mock(UriTemplate.class);
        when(unmatched.getUriTemplate()).thenReturn(unmatchedUriTemplate);
        when(unmatchedUriTemplate.match(eq("/users"))).thenReturn(Optional.empty());

        DefaultResourceRouter router = new DefaultResourceRouter(runtime, List.of(matched, unmatched));
        OutboundResponse response = router.dispatch(request, context);
        assertSame(entity, response.getGenericEntity());
        assertEquals(200, response.getStatus());
    }

    /*@Test
    public void should() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        ResourceContext context = mock(ResourceContext.class);

        when(request.getServletPath()).thenReturn("/users");
        when(context.getResource(eq(Users.class))).thenReturn(new Users());
        Router router = new Router(runtime, Arrays.asList(new ResourceClass(Users.class)));

        OutboundResponse response = router.dispatch(request, context);
        GenericEntity<String> entity = (GenericEntity<String>)response.getEntity();
        assertEquals("all", entity.getEntity());
    }

    static class Router implements ResourceRouter {
        private Runtime runtime;
        private List<Resource> rootResources;

        public Router(Runtime runtime, List<Resource> rootResources) {
            this.runtime = runtime;
            this.rootResources = rootResources;
        }

        @Override
        public OutboundResponse dispatch(HttpServletRequest request, ResourceContext resourceContext) {
//            UriInfoBuilder builder = runtime.createUriBuilder(request);
            ResourceMethod resourceMethod = rootResources.stream().map(root -> root.matches(request.getServletPath(), "GET", new String[0], null))
                    .filter(Optional::isPresent).findFirst().get().get();

            try {
                GenericEntity entity = resourceMethod.call(resourceContext, null);
                return (OutboundResponse) Response.ok(entity).build();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    static class ResourceClass implements ResourceRouter.Resource {
        private Pattern pattern;
        private String path;
        private Class<?> resourceClass;
        private Map<URITemplate, ResourceRouter.ResourceMethod> methods = new HashMap<>();

        record URITemplate(Pattern uri, String[] mediaType){}

        public ResourceClass(Class<?> resourceClass) {
            this.resourceClass = resourceClass;
            path = resourceClass.getAnnotation(Path.class).value();
            pattern = Pattern.compile(path + "(/.*)?");

            for (Method method : Arrays.stream(resourceClass.getMethods()).filter(m -> m.isAnnotationPresent(GET.class)).toList()) {
                methods.put(new URITemplate(pattern, method.getAnnotation(Produces.class).value()),
                        new NormalResourceMethod(resourceClass, method));
            }

            for (Method method : Arrays.stream(resourceClass.getMethods()).filter(m -> m.isAnnotationPresent(Path.class)).toList()) {
                Path path = method.getAnnotation(Path.class);
                Pattern pattern = Pattern.compile(this.path + ("(/" + path + ")?"));
                methods.put(new URITemplate(pattern, method.getAnnotation(Produces.class).value()),
                        new SubResourceLocator(resourceClass, method, new String[0]));
            }
        }

        @Override
        public Optional<ResourceRouter.ResourceMethod> matches(String path, String method, String[] mediaTypes, UriInfoBuilder builder) {
            if (!pattern.matcher(path).matches()) {
                return Optional.empty();
            }

            return methods.entrySet().stream().filter(e -> e.getKey().uri.matcher(path).matches())
                    .map(e -> e.getValue()).findFirst();
        }
    }

    static class NormalResourceMethod implements ResourceRouter.ResourceMethod {
        private Class<?> resourceClass;
        private Method method;

        public NormalResourceMethod(Class<?> resourceClass, Method method) {
            this.resourceClass = resourceClass;
            this.method = method;
        }

        @Override
        public GenericEntity<?> call(ResourceContext resourceContext, UriInfoBuilder builder) {
            Object resource = resourceContext.getResource(resourceClass);
            try {
                return new GenericEntity<>(method.invoke(resource), method.getGenericReturnType());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    static class SubResourceLocator implements ResourceRouter.ResourceMethod {
        private Class<?> resourceClass;
        private Method method;
        private String[] mediaTypes;

        public SubResourceLocator(Class<?> resourceClass, Method method, String[] mediaTypes) {
            this.resourceClass = resourceClass;
            this.method = method;
            this.mediaTypes = mediaTypes;
        }

        @Override
        public GenericEntity<?> call(ResourceContext resourceContext, UriInfoBuilder builder) {
            Object resource = resourceContext.getResource(resourceClass);
            try {
                Object subResource = method.invoke(resource);
                return new SubResource(subResource).matches(builder.getUnmatchedPath(),
                        "GET", mediaTypes, builder).get().call(resourceContext, builder);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    static class SubResource implements ResourceRouter.Resource {

        private Class<? extends Object> subResourceClass;
        private Object subResource;

        public SubResource(Object subResource) {
            this.subResource = subResource;
            this.subResourceClass = subResource.getClass();
        }

        @Override
        public Optional<ResourceRouter.ResourceMethod> matches(String path, String method, String[] mediaTypes, UriInfoBuilder builder) {
            return Optional.empty();
        }
    }

    @Path("/users")
    static class Users {
        @GET
        @Produces(MediaType.TEXT_PLAIN)
        public String asText() {
            return "all";
        }

        @Path("/orders")
        public Orders getOrders() {
            return new Orders();
        }
    }

    static class Orders {
        @GET
        public String asText() {
            return "all";
        }
    }*/
}
