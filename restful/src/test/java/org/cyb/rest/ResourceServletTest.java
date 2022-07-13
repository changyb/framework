package org.cyb.rest;

import jakarta.servlet.Servlet;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.core.*;
import jakarta.ws.rs.ext.MessageBodyWriter;
import jakarta.ws.rs.ext.Providers;
import jakarta.ws.rs.ext.RuntimeDelegate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.function.Executable;
import org.mockito.Mockito;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ResourceServletTest extends ServletTest {
    private Runtime runtime;
    private ResourceRouter router;
    private ResourceContext resourceContext;
    private Providers providers;

    private OutboundResponseBuilder response;

    @BeforeEach
    public void before() {
        response = new OutboundResponseBuilder();
        RuntimeDelegate delegate = Mockito.mock(RuntimeDelegate.class);
        RuntimeDelegate.setInstance(delegate);
        when(delegate.createHeaderDelegate(eq(NewCookie.class)))
                .thenReturn(new RuntimeDelegate.HeaderDelegate<>() {
                    @Override
                    public NewCookie fromString(String s) {
                        return null;
                    }

                    @Override
                    public String toString(NewCookie newCookie) {
                        return newCookie.getName() + "=" + newCookie.getValue();
                    }
                });

    }

    @Override
    protected Servlet getServlet() {
        runtime = Mockito.mock(Runtime.class);
        router = Mockito.mock(ResourceRouter.class);
        resourceContext = Mockito.mock(ResourceContext.class);
        providers = mock(Providers.class);

        when(runtime.getResourceRouter()).thenReturn(router);
        when(runtime.createResourceContext(any(), any())).thenReturn(resourceContext);
        when(runtime.getProviders()).thenReturn(providers);

        return new ResourceServlet(runtime);
    }

    //TODO: use status code as http status
    @Test
    public void should_use_status_from_response() throws Exception {
        response.status(Response.Status.NOT_MODIFIED).returnFrom(router);

        HttpResponse<String> httpResponse = get("/test");
        assertEquals(Response.Status.NOT_MODIFIED.getStatusCode(), httpResponse.statusCode());
    }

    //TODO: use headers as http headers
    @Test
    public void should_use_http_headers_from_response() throws Exception {
        response.status(Response.Status.NOT_MODIFIED)
                .headers("Set-Cookie", new NewCookie.Builder("SESSION_ID").value("session").build(),
                new NewCookie.Builder("USER_ID").value("user").build()).returnFrom(router);

        HttpResponse<String> httpResponse = get("/test");

        assertArrayEquals(new String[]{"SESSION_ID=session", "USER_ID=user"},
                httpResponse.headers().allValues("Set-Cookie").toArray(String[]::new));

    }

    //TODO: writer body using MessageBodyWriter
    @Test
    public void should_write_entity_to_http_response_using_message_body_writer() throws Exception {
        response.entity(new GenericEntity<>("entity", String.class), new Annotation[0])
                .returnFrom(router);

        HttpResponse<String> httpResponse = get("/test");
        assertEquals("entity", httpResponse.body());
    }

    @Test
    public void should_use_response_from_web_application_exception() throws Exception {
        response.status(Response.Status.FORBIDDEN)
                .headers(HttpHeaders.SET_COOKIE,
                        new NewCookie.Builder("SESSION_ID").value("session").build())
                .entity(new GenericEntity<>("error", String.class), new Annotation[0])
                .throwFrom(router);

        HttpResponse<String> httpResponse = get("/test");
        assertEquals(Response.Status.FORBIDDEN.getStatusCode(), httpResponse.statusCode());
        assertArrayEquals(new String[]{"SESSION_ID=session"},
                httpResponse.headers().allValues(HttpHeaders.SET_COOKIE).toArray(String[]::new));
        assertEquals("error", httpResponse.body());

    }

    @Test
    public void should_build_response_by_exception_mapper_if_null_response_from_web_application_exception() throws Exception {
        when(router.dispatch(any(), eq(resourceContext))).thenThrow(RuntimeException.class);
        when(providers.getExceptionMapper(eq(RuntimeException.class)))
                .thenReturn(exception -> response.status(Response.Status.FORBIDDEN).build());

        HttpResponse<String> httpResponse = get("/test");
        assertEquals(Response.Status.FORBIDDEN.getStatusCode(), httpResponse.statusCode());
    }

    @Test
    public void should_not_call_message_body_writer_if_entity_is_null() throws Exception {
        response.entity(null, new Annotation[0]).returnFrom(router);
        HttpResponse<String> httpResponse = get("/test");

        assertEquals(Response.Status.OK.getStatusCode(), httpResponse.statusCode());
        assertEquals("", httpResponse.body());
    }

    @Test
    public void should_use_response_from_web_application_exception_thrown_by_exception_mapper() throws  Exception {
        when(router.dispatch(any(), eq(resourceContext))).thenThrow(RuntimeException.class);
        when(providers.getExceptionMapper(eq(RuntimeException.class)))
                .thenReturn(exception -> {
                    throw new WebApplicationException(response.status(Response.Status.FORBIDDEN).build());
                });
        HttpResponse<String> httpResponse = get("/test");
        assertEquals(Response.Status.FORBIDDEN.getStatusCode(), httpResponse.statusCode());

    }

    @Test
    public void should_map_exception_thrown_by_exception_mapper() throws Exception {
        when(router.dispatch(any(), eq(resourceContext))).thenThrow(RuntimeException.class);
        when(providers.getExceptionMapper(eq(RuntimeException.class)))
                .thenReturn(exception -> {
                    throw new IllegalArgumentException();
                });
        when(providers.getExceptionMapper(eq(IllegalArgumentException.class)))
                .thenReturn(exception -> response.status(Response.Status.FORBIDDEN).build());

        HttpResponse<String> httpResponse = get("/test");
        assertEquals(Response.Status.FORBIDDEN.getStatusCode(), httpResponse.statusCode());
    }


    @TestFactory
    public List<DynamicTest> RespondWhenExtensionMissing() {
        List tests = new ArrayList<>();
        Map<String, Executable> extensions = Map.of(
                "MessageBodyWriter", () -> new OutboundResponseBuilder().entity(new GenericEntity<>(1, Integer.class), new Annotation[0]).returnFrom(router),
                "HeaderDelegate", () -> new OutboundResponseBuilder().headers(HttpHeaders.DATE, new Date()).returnFrom(router),
                "ExceptionMapper", () -> when(router.dispatch(any(), eq(resourceContext))).thenThrow(IllegalStateException.class));
        for (String name : extensions.keySet())
            tests.add(DynamicTest.dynamicTest(name + " not found", () -> {
                extensions.get(name).execute();
                when(providers.getExceptionMapper(eq(NullPointerException.class))).thenReturn(e -> new OutboundResponseBuilder().status(Response.Status.INTERNAL_SERVER_ERROR).build());
                HttpResponse<String> httpResponse = get("/test");
                assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), httpResponse.statusCode());

            }));
        return tests;
    }

    @TestFactory
    public List<DynamicTest> RespondForException() {
        List<DynamicTest> tests = new ArrayList<>();
        Map<String, Consumer<Consumer<RuntimeException>>> exceptions = Map.of(
                "Other Exception", this::otherExceptionTrownFrom,
                "WebApplicationException", this::webApplicationExceptionThrownFrom);
        for (Map.Entry<String, Consumer<RuntimeException>> caller : getCallers().entrySet())
            for (Map.Entry<String, Consumer<Consumer<RuntimeException>>> exceptionThrownFrom : exceptions.entrySet())
                tests.add(DynamicTest.dynamicTest(caller.getKey() + " throws " + exceptionThrownFrom.getKey(),
            () -> exceptionThrownFrom.getValue().accept(caller.getValue())));
        return tests;
    }


    @ExceptionThrownFrom
    private void providers_getMessageBodyWriter(RuntimeException exception) {
        response.entity(new GenericEntity<>(2.5, Double.class), new Annotation[0]).returnFrom(router);
        when(providers.getMessageBodyWriter(eq(Double.class), eq(Double.class), eq(new Annotation[0]), eq(MediaType.TEXT_PLAIN_TYPE)))
                .thenThrow(exception);
    }
/*
    @Test
    public void should_use_response_from_web_application_exception_thrown_by_providers_when_find_message_body_writer() throws Exception {
        webApplicationExceptionThrownFrom(this::providers_getMessageBodyWriter);
    }

    @Test
    public void should_use_response_from_web_application_exception_thrown_by_message_body_writer() throws Exception {
        webApplicationExceptionThrownFrom(this::messageBodyWriter_writeTo);
    }

    @Test
    public void should_map_exception_thrown_by_provides_when_find_message_body_writer() {
        otherExceptionTrownFrom(this::providers_getMessageBodyWriter);
    }

    @Test
    public void should_map_exception_thrown_by_message_body_writer() {
        otherExceptionTrownFrom(this::messageBodyWriter_writeTo);
    }*/

    @TestFactory
    public List<DynamicTest> should_respond_based_on_exception_thrown() {
        List<DynamicTest> tests = new ArrayList<>();

        Map<String, Consumer<Consumer<RuntimeException>>> exceptions = Map.of("Other Exception", this::otherExceptionTrownFrom,
                "WebApplicaitonException", this::webApplicationExceptionThrownFrom);
        Map<String, Consumer<RuntimeException>> callers = getCallers();

        for (Map.Entry<String,Consumer<RuntimeException>> caller : callers.entrySet()) {
            for (Map.Entry<String, Consumer<Consumer<RuntimeException>>> exceptionThrownFrom : exceptions.entrySet()) {
                tests.add(DynamicTest.dynamicTest(caller.getKey() + " throws " + exceptionThrownFrom.getKey(),
                        () -> exceptionThrownFrom.getValue().accept(caller.getValue())));
            }
        }
        return tests;
    }

    private Map<String, Consumer<RuntimeException>> getCallers() {
        Map<String, Consumer<RuntimeException>> callers = new HashMap<>();

        for (Method method : Arrays.stream(this.getClass().getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(ExceptionThrownFrom.class)).toList()) {
            String name = method.getName();
            String callerName = name.substring(0, 1).toUpperCase() + name.substring(1).replace('_', '.');
            callers.put(callerName, e -> {
               try {
                   method.invoke(this, e);
               } catch (Exception ex) {
                   throw new RuntimeException(ex);
               }
            });

        }
        return callers;
//        return Map.of("Providers.getMessageBodyWriter", this::providers_getMessageBodyWriter,
//                "MessageBodyWriter.writerTo", this::messageBodyWriter_writeTo);
    }

    @Retention(RetentionPolicy.RUNTIME)
    @interface ExceptionThrownFrom {
    }


    private void otherExceptionTrownFrom(Consumer<RuntimeException> caller) {
        RuntimeException exception = new IllegalArgumentException();

        caller.accept(exception);

        when(providers.getExceptionMapper(eq(IllegalArgumentException.class))).thenReturn(e -> new OutboundResponseBuilder().status(Response.Status.FORBIDDEN).build());

        HttpResponse<String> httpResponse = get("/test");

        assertEquals(Response.Status.FORBIDDEN.getStatusCode(), httpResponse.statusCode());
    }

    private void webApplicationExceptionThrownFrom(Consumer<RuntimeException> caller) {
        RuntimeException exception = new WebApplicationException(new OutboundResponseBuilder().status(Response.Status.FORBIDDEN).build());

        caller.accept(exception);

        HttpResponse<String> httpResponse = get("/test");

        assertEquals(Response.Status.FORBIDDEN.getStatusCode(), httpResponse.statusCode());
    }

    @ExceptionThrownFrom
    private void messageBodyWriter_writeTo(RuntimeException exception) {
        response.entity(new GenericEntity<>(2.5, Double.class), new Annotation[0]).returnFrom(router);
        when(providers.getMessageBodyWriter(eq(Double.class), eq(Double.class), eq(new Annotation[0]), eq(MediaType.TEXT_PLAIN_TYPE)))
                .thenReturn(new MessageBodyWriter<Double>() {
                    @Override
                    public boolean isWriteable(Class<?> aClass, Type type, Annotation[] annotations, MediaType mediaType) {
                        return false;
                    }

                    @Override
                    public void writeTo(Double aDouble, Class<?> aClass, Type type, Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, Object> multivaluedMap, OutputStream outputStream) throws IOException, WebApplicationException {
                        throw exception;
                    }
                });
    }


    class OutboundResponseBuilder {
        Response.Status status = Response.Status.OK;
        MultivaluedMap<String, Object> headers = new MultivaluedHashMap<>();
        GenericEntity<Object> entity = new GenericEntity<>("entity", String.class);
        Annotation[] annotations = new Annotation[0];
        MediaType mediaType = MediaType.TEXT_PLAIN_TYPE;

        public OutboundResponseBuilder status(Response.Status status) {
            this.status = status;
            return this;
        }

        public OutboundResponseBuilder headers(String name, Object... values) {
            headers.addAll(name, values);
            return this;
        }

        public OutboundResponseBuilder entity(GenericEntity<Object> entity, Annotation[] annotations) {
            this.entity = entity;
            this.annotations = annotations;
            return this;
        }

        public void returnFrom(ResourceRouter router) {
            build(response -> when(router.dispatch(any(), eq(resourceContext))).thenReturn(response));
        }

        public void throwFrom(ResourceRouter router) {
            build(response -> {
                WebApplicationException exception = new WebApplicationException(response);
                when(router.dispatch(any(), eq(resourceContext))).thenThrow(exception);
            });
        }

        public void build(Consumer<OutboundResponse> consumer) {
            OutboundResponse response = build();
            consumer.accept(response);
        }

        OutboundResponse build() {
            OutboundResponse response = Mockito.mock(OutboundResponse.class);
            when(response.getStatus()).thenReturn(status.getStatusCode());
            when(response.getStatusInfo()).thenReturn(status);
            when(response.getHeaders()).thenReturn(headers);
            when(response.getGenericEntity()).thenReturn(entity);
            when(response.getMediaType()).thenReturn(mediaType);
            when(response.getAnnotations()).thenReturn(annotations);
            setupMessageBodyWriter();
            return response;
        }

        private void setupMessageBodyWriter() {
            when(providers.getMessageBodyWriter(eq(String.class), eq(String.class), same(annotations), eq(mediaType)))
                    .thenReturn(new MessageBodyWriter<>() {
                        @Override
                        public boolean isWriteable(Class<?> aClass, Type type, Annotation[] annotations, MediaType mediaType) {
                            return false;
                        }

                        @Override
                        public void writeTo(String s, Class<?> aClass, Type type, Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, Object> multivaluedMap, OutputStream outputStream) throws IOException, WebApplicationException {
                            PrintWriter writer = new PrintWriter(outputStream);
                            writer.write(s);
                            writer.flush();
                        }
                    });
        }
    }
}
