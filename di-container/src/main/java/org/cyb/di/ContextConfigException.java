package org.cyb.di;

import java.text.MessageFormat;
import java.util.List;

class ContextConfigException extends RuntimeException {
    static ContextConfigException illegalAnnotation(Class type, List annotations) {
        return new ContextConfigException(MessageFormat.format("Unqualified annotations: {0} of {1}", String.join(" , ", annotations.stream().map(Object::toString).toList()), type));
    }

    static ContextConfigException unknownScope(Class annotationType) {
        return new ContextConfigException(MessageFormat.format("Unknown scope: {0}", annotationType));
    }

    static ContextConfigException duplicated(Component component) {
        return new ContextConfigException(MessageFormat.format("Duplicated: {0}", component));
    }

    ContextConfigException(String message) {
        super(message);
    }
}
