package org.cyb.rest;

import jakarta.ws.rs.core.GenericEntity;
import org.eclipse.jetty.http.HttpTester;

import java.lang.annotation.Annotation;

public abstract class OutboundResponse extends HttpTester.Response {

    abstract GenericEntity getGenericEntity();

    abstract Annotation[] getAnnotations();
}
