package org.cyb.rest;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.core.GenericEntity;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

public interface ResourceRouter {
    OutboundResponse dispatch(HttpServletRequest request, ResourceContext resourceContext);

    interface RootResource extends Resource {
        UriTemplate getUriTemplate();
    }

    interface ResourceMethod {
        GenericEntity<?> call(ResourceContext resourceContext, UriInfoBuilder builder);
    }

    interface Resource {
        Optional<ResourceMethod> matches(String path, String method, String[] mediaTypes, UriInfoBuilder builder);
    }
}

class DefaultResourceRouter implements ResourceRouter {

    private Runtime runtime;
    private List<RootResource> rootResources;

    public DefaultResourceRouter(Runtime runtime, List<RootResource> rootResources) {
        this.runtime = runtime;
        this.rootResources = rootResources;
    }

    @Override
    public OutboundResponse dispatch(HttpServletRequest request, ResourceContext resourceContext) {
        String path = request.getServletPath();

        UriInfoBuilder uri = runtime.createUriInfoBuilder(request);


        Optional<ResourceMethod> resourceMethod = rootResources.stream()
                .map(resource -> matched(path, resource))
                .filter(Result::isMatched)
                .sorted()
                .findFirst()
                .flatMap(result -> result.findResourceMethod(request, uri));

        if (rootResources.stream().map(resource -> matched(path, resource))
                .filter(Result::isMatched)
                .sorted()
                .findFirst()
                .isEmpty()) {
            return (OutboundResponse) Response.status(Response.Status.NOT_FOUND).build();
        }

        return (OutboundResponse) resourceMethod.map(m -> m.call(resourceContext, uri))
                .map(entity -> Response.ok(entity).build())
                .orElseGet(() -> Response.noContent().build());
    }

    private Result matched(String path, RootResource resource) {
        return new Result(resource.getUriTemplate().match(path),
                resource);
    }

    record Result(Optional<UriTemplate.MatchResult> matched, RootResource resource) implements Comparable<Result> {
        @Override
        public int compareTo(Result o) {
            return matched.get().compareTo(o.matched.get());
        }

        private boolean isMatched() {
            return matched.isPresent();
        }

        private Optional<ResourceMethod> findResourceMethod(HttpServletRequest request, UriInfoBuilder uri) {
            return matched.flatMap(result -> resource.matches(result.getRemaining(), request.getMethod(),
                    Collections.list(request.getHeaders(HttpHeaders.ACCEPT)).toArray(String[]::new),
                    uri));
        }
    }
}
