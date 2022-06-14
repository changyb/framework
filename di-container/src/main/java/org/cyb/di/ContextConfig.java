package org.cyb.di;

import jakarta.inject.Provider;
import jakarta.inject.Qualifier;
import jakarta.inject.Scope;
import jakarta.inject.Singleton;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.text.MessageFormat;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.util.Arrays.stream;
import static java.util.stream.Collectors.*;
import static org.cyb.di.ContextConfigException.illegalAnnotation;

public class ContextConfig {
    private Map<Component, ComponentProvider<?>> components = new HashMap<>();
    private Map<Class<?>, ScopeProvider> scopes = new HashMap<>();

    public ContextConfig() {
        scope(Singleton.class, SingletonProvider::new);
    }

    public <Type> void instance(Class<Type> type, Type instance) {
        bind(new Component(type, null), context -> instance);
    }
    public <Type> void instance(Class<Type> type, Type instance, Annotation... annotations) {
        bindInstance(type, instance, annotations);
    }

    public <Type, Implementation extends Type>  void component(Class<Type> type, Class<Implementation> implementation, Annotation... annotations) {
        bindComponent(type, implementation, annotations);
    }

    private void bindComponent(Class<?> type, Class<?> implementation, Annotation... annotations) {
        Bindings bindings = Bindings.component(implementation, annotations);
        bind(type, bindings.qualifiers(), provider(implementation, bindings.scope()));
    }

    private <Type> ComponentProvider<?> provider(Class<Type> implementation, Optional<Annotation> scope) {
        ComponentProvider<?> injectionProvider = new InjectProvider<>(implementation);
        return scope.<ComponentProvider<?>>map(s -> scoped(s, injectionProvider)).orElse(injectionProvider);
    }

    private ComponentProvider<?> scoped(Annotation scope, ComponentProvider<?> provider) {
        if (!scopes.containsKey(scope.annotationType()))
            throw ContextConfigException.unknownScope(scope.annotationType());
        return scopes.get(scope.annotationType()).create(provider);
    }

    private void bind(Component component, ComponentProvider<?> provider) {
        if (components.containsKey(component)) throw ContextConfigException.duplicated(component);
        components.put(component, provider);
    }

    private void bindInstance(Class<?> type, Object instance, Annotation[] annotations) {
        bind(type, Bindings.instance(type, annotations).qualifiers(), context -> instance);
    }

    public void from(Config config) {
        new DSL(config).bind();
    }

    public <T> void bind(Class<T> type, T instance) {
        components.put(new Component(type, null), (ComponentProvider<T>) context -> instance);
    }

    public <T> void bind(Class<T> type, T instance, Annotation... annotations) {
        if (stream(annotations).map(Annotation::annotationType)
                .anyMatch(t -> !t.isAnnotationPresent(Qualifier.class) && !t.isAnnotationPresent(Scope.class))) {
            throw new IllegalComponentException();
        }

        for (Annotation qualifier : annotations) {
            components.put(new Component(type, qualifier), context -> instance);
        }
    }

    public <T, U extends T>
    void bind(Class<T> type, Class<U> implementation) {
        bind(type, implementation, implementation.getAnnotations());
    }

    public <T, U extends T>
    void bind(Class<T> type, Class<U> implementation, Annotation... annotations) {
        Map<Class<?>, List<Annotation>> annotationGroups = stream(annotations).collect(groupingBy(this::typeOf, toList()));

        if (annotationGroups.containsKey(Illgeal.class)) {
            throw new IllegalComponentException();
        }

        bind(type, annotationGroups.getOrDefault(Qualifier.class, List.of()),
                createScopedProvider(implementation, annotationGroups.getOrDefault(Scope.class, List.of()),
                        new InjectProvider<U>(implementation)));
    }

    private <T> ComponentProvider<?> createScopedProvider(Class<T> implementation, List<Annotation> scopes, ComponentProvider<?> injectionProvider) {
        if (scopes.size() > 1) {
            throw new IllegalComponentException();
        }
        Optional<Annotation> scope = scopes.stream().findFirst()
                .or(() -> scopeFrom(implementation));
        return scope.<ComponentProvider<?>>map(s -> getScopeProvider(s, injectionProvider))
                .orElse(injectionProvider);
    }

    private <T> void bind(Class<T> type, List<Annotation> qualifiers, ComponentProvider<?> provider) {
        if (qualifiers.isEmpty()) {
            components.put(new Component(type, null), provider);
        }
        for (Annotation qualifier : qualifiers) {
            components.put(new Component(type, qualifier), provider);
        }
    }

    private <T> Optional<Annotation> scopeFrom(Class<T> implementation) {
        return stream(implementation.getAnnotations()).filter(a -> a.annotationType().isAnnotationPresent(Scope.class)).findFirst();
    }

    private Class<?> typeOf(Annotation annotation) {
        Class<? extends Annotation> type = annotation.annotationType();
        return Stream.of(Qualifier.class, Scope.class).filter(type::isAnnotationPresent).findFirst().orElse(Illgeal.class);
    }

    private @interface Illgeal {
    }


    private ComponentProvider<?> getScopeProvider(Annotation scope, ComponentProvider<?> provider) {
        if (!scopes.containsKey(scope.annotationType())) {
            throw new IllegalComponentException();
        }
        return scopes.get(scope.annotationType()).create(provider);
    }

    public <ScopeType extends Annotation> void scope(Class<ScopeType> scope, ScopeProvider provider) {
        scopes.put(scope, provider);
    }


    public Context getContext() {
        components.keySet().forEach(component -> checkDependencies(component, new Stack<>()));

        return new Context() {
            @Override
            public <ComponentType> Optional<ComponentType> get(ComponentRef<ComponentType> ref) {
                if (ref.isContainer()) {
                    if (ref.getContainer() != Provider.class) {
                        return Optional.empty();
                    }
                    return (Optional<ComponentType>) Optional.ofNullable(getProvider(ref))
                            .map(provider -> (Provider<Object>) () -> provider.get(this));
                }
                return Optional.ofNullable(getProvider(ref))
                        .map(provider -> (ComponentType)provider.get(this));
            }
        };
    }

    private <ComponentType> ComponentProvider<?> getProvider(ComponentRef<ComponentType> ref) {
        return components.get(ref.component());
    }

    private void checkDependencies(Component component, Stack<Component> visiting) {
        for (ComponentRef dependency : components.get(component).getDependencies()) {
            if (!components.containsKey(dependency.component())) {
                throw new DependencyNotFoundException(dependency.component(), component);
            }

            if (!dependency.isContainer()) {
                if (visiting.contains(dependency.component())) {
                    throw new CyclicDependenciesFound(visiting);
                }

                visiting.push(dependency.component());
                checkDependencies(dependency.component(), visiting);
                visiting.pop();
            }
        }
    }

    static class Bindings {
        public static Bindings component(Class component, Annotation... annotations) {
            return new Bindings(component, annotations, Qualifier.class, Scope.class);
        }

        public static Bindings instance(Class instance, Annotation... annotations) {
            return new Bindings(instance, annotations, Qualifier.class);
        }

        Class type;
        Map<Class<?>, List<Annotation>> group;

        public Bindings(Class type, Annotation[] annotations, Class... allowed) {
            this.type = type;
            this.group = parse(type, annotations, allowed);
        }

        private static Map<Class<?>, List<Annotation>> parse(Class<?> type, Annotation[] annotations, Class<? extends Annotation>... allowed) {
            Map<Class<?>, List<Annotation>> annotationGroups = stream(annotations).collect(groupingBy(allow(allowed), toList()));
            if (annotationGroups.containsKey(Illegal.class))
                throw illegalAnnotation(type, annotationGroups.get(Illegal.class));
            return annotationGroups;
        }

        private static Function<Annotation, Class<?>> allow(Class<? extends Annotation>... annotations) {
            return annotation -> Stream.of(annotations).filter(annotation.annotationType()::isAnnotationPresent)
                    .findFirst().orElse(Illegal.class);
        }
        private @interface Illegal {

        }
        Optional<Annotation> scope() {
            List<Annotation> scopes = group.getOrDefault(Scope.class, from(type, Scope.class));
            if (scopes.size() > 1) throw illegalAnnotation(type, scopes);
            return scopes.stream().findFirst();
        }

        List<Annotation> qualifiers() {
            return group.getOrDefault(Qualifier.class, List.of());
        }

        private static List<Annotation> from(Class<?> implementation, Class<? extends Annotation> annotation) {
            return stream(implementation.getAnnotations()).filter(a -> a.annotationType().isAnnotationPresent(annotation)).toList();
        }
    }

    class DSL {
        private Config config;

        public DSL(Config config) {
            this.config = config;
        }

        void bind() {
            for (Declaration declaration : declarations())
                declaration.value().ifPresentOrElse(declaration::bindInstance, declaration::bindComponent);
        }

        private List<Declaration> declarations() {
            return stream(config.getClass().getDeclaredFields()).filter(f -> !f.isSynthetic()).map(Declaration::new).toList();
        }

        class Declaration {
            private Field field;

            Declaration(Field field) {
                this.field = field;
            }

            void bindInstance(Object instance) {
                ContextConfig.this.bindInstance(type(), instance, annotations());
            }

            void bindComponent() {
                ContextConfig.this.bindComponent(type(), field.getType(), annotations());
            }

            private Optional value() {
                try {
                    return Optional.ofNullable(field.get(config));
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }

            private Class type() {
                Config.Export export = field.getAnnotation(Config.Export.class);
                return export != null ? export.value() : field.getType();
            }

            private Annotation[] annotations() {
                return stream(field.getAnnotations()).filter(a -> a.annotationType() != Config.Export.class).toArray(Annotation[]::new);
            }
        }
    }

}
