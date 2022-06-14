package org.cyb.rest;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.ext.Providers;
import org.cyb.di.Context;

public interface Runtime {
    Providers getProviders();

    ResourceContext createResourceContext(HttpServletRequest request, HttpServletResponse response);

    Context getApplicationContext();

    ResourceRouter getResourceRouter();
}
