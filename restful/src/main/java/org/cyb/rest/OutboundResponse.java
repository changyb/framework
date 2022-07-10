package org.cyb.rest;

import jakarta.ws.rs.core.GenericEntity;
import jakarta.ws.rs.core.Response;

import java.lang.annotation.Annotation;

public abstract class OutboundResponse extends Response {

    abstract GenericEntity getGenericEntity();

    abstract Annotation[] getAnnotations();
}
