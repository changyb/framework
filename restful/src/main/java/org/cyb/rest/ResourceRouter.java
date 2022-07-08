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

        Optional<Result> matched = rootResources.stream().map(resource -> new Result(resource.getUriTemplate().match(path),
                        resource)).filter(result -> result.matched.isPresent())
                .sorted()
                .findFirst();

        Optional<ResourceMethod> resourceMethod = matched.flatMap(result -> result.resource.matches(result.matched.get().getRemaining(), request.getMethod(),
                Collections.list(request.getHeaders(HttpHeaders.ACCEPT)).toArray(String[]::new),
                uri));

        GenericEntity entity = resourceMethod.map(m -> m.call(resourceContext, uri)).get();
        return (OutboundResponse) Response.ok(entity).build();
    }

    record Result(Optional<UriTemplate.MatchResult> matched, RootResource resource) implements Comparable<Result> {
        @Override
        public int compareTo(Result o) {
            return matched.get().compareTo(o.matched.get());
        }
    }
}
