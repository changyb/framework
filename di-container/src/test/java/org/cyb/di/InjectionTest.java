package org.cyb.di;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Provider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.ParameterizedType;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class InjectionTest {
    private Dependency dependency = mock(Dependency.class);

    private Provider<Dependency> dependencyProvider = mock(Provider.class);

    private Context context = mock(Context.class);

    private ParameterizedType dependencyProviderType;


    @BeforeEach
    public void setUp() throws NoSuchFieldException {
        dependencyProviderType = (ParameterizedType)InjectionTest.class.getDeclaredField("dependencyProvider").getGenericType();
        when(context.get(eq(ComponentRef.of(Dependency.class)))).thenReturn(Optional.of(dependency));
        when(context.get(eq(ComponentRef.of(dependencyProviderType)))).thenReturn(Optional.of(dependencyProvider));
    }

    @Nested
    public class ConstructorInjection {

        @Nested
        class Injection {
            static class DefaultConstructor {
            }

            @Test
            public void should_call_default_constructor_if_no_inject_constructor() {
                DefaultConstructor instance = new InjectProvider<>(DefaultConstructor.class).get(context);
                assertNotNull(instance);
            }

            static class InjectConstructor {
                Dependency dependency;

                @Inject
                public InjectConstructor(Dependency dependency) {
                    this.dependency = dependency;
                }
            }

            @Test
            public void should_inject_dependency_via_inject_constructor() {
                InjectConstructor component = new InjectProvider<>(InjectConstructor.class).get(context);

                assertSame(dependency, component.dependency);
            }

            @Test
            public void should_include_dependency_from_inject_constructor() {
                InjectProvider<InjectConstructor> provider = new InjectProvider<>(InjectConstructor.class);
                assertArrayEquals(new ComponentRef[]{ComponentRef.of(Dependency.class)}, provider.getDependencies().toArray(ComponentRef[]::new));
            }

            @Test
            public void should_include_provider_type_from_inject_constructor() {
                InjectProvider<ProviderInjectConstructor> provider = new InjectProvider<>(ProviderInjectConstructor.class);
                assertArrayEquals(new ComponentRef[]{ComponentRef.of(dependencyProviderType)}, provider.getDependencies().toArray(ComponentRef[]::new));
            }

            static class ProviderInjectConstructor {
                Provider<Dependency> dependency;

                @Inject
                public ProviderInjectConstructor(Provider<Dependency> dependency) {
                    this.dependency = dependency;
                }
            }

            @Test
            public void should_inject_provider_via_inject_constructor() {
                ProviderInjectConstructor instance = new InjectProvider<>(ProviderInjectConstructor.class).get(context);
                assertSame(instance.dependency, dependencyProvider);
            }

            static class MultiQualifierInjectConstructor {
                @Inject
                public MultiQualifierInjectConstructor(@Named("ChosenOne") @SkyWalker Dependency dependency) {

                }
            }

            @Test
            public void should_throw_exception_if_multi_qualifiers_given() {
                assertThrows(IllegalComponentException.class, () -> new InjectProvider<>(MultiQualifierInjectConstructor.class));
            }
        }

        @Nested
        class IllegelInjectConstructor {
            abstract class AbstractTestComponent implements TestComponent {
                @Inject
                public AbstractTestComponent() {
                }
            }

            @Test
            public void should_throw_exception_if_component_is_abstract() {
                assertThrows(IllegalComponentException.class,
                        () -> new InjectProvider<>(AbstractTestComponent.class));
            }

            @Test
            public void should_throw_exception_if_component_is_interface() {
                assertThrows(IllegalComponentException.class,
                        () -> new InjectProvider<>(AbstractTestComponent.class));
            }

            @Test
            public void should_throw_exception_if_multi_inject_constructors_provided() {
                assertThrows(IllegalComponentException.class, () -> {
                    new InjectProvider<>(TestComponentWithMultiInjectConstructors.class);
                });
            }

            @Test
            public void should_throw_exception_if_no_inject_constructor_nor_default_constructor_provided() {
                assertThrows(IllegalComponentException.class, () -> {
                    new InjectProvider<>(TestComponentWithNoInjectConstructorNorDefaultConstructor.class);
                });
            }
        }

        @Nested
        class WithQualifier {

            @BeforeEach
            public void before() {
                Mockito.reset(context);
                when(context.get(eq(ComponentRef.of(Dependency.class, new NamedLiteral("ChosenOne"))))).thenReturn(Optional.of(dependency));
            }

            static class InjectConstructor {
                Dependency dependency;
                @Inject
                public InjectConstructor(@Named("ChosenOne")Dependency dependency) {
                    this.dependency = dependency;
                }
            }

            @Test
            public void should_inject_dependency_with_qualifier_via_constructor() {
                InjectProvider<InjectConstructor> provider = new InjectProvider<>(InjectConstructor.class);
                InjectConstructor component = provider.get(context);
                assertSame(dependency, component.dependency);
            }

            @Test
            public void should_include_qualifier_with_dependency() {
                InjectProvider<InjectConstructor> provider = new InjectProvider<>(InjectConstructor.class);
                assertArrayEquals(new ComponentRef<?>[]{
                    ComponentRef.of(Dependency.class, new NamedLiteral("ChosenOne"))
                }, provider.getDependencies().toArray(new ComponentRef[0]));
            }
        }
    }

    @Nested
    public class FiledInjection {

        @Nested
        class Injection {
            static class ComponentWithFieldInjection {
                @Inject
                Dependency dependency;
            }

            static class SubclassWithFieldInjection extends ComponentWithFieldInjection {
            }

            @Test
            public void should_inject_dependency_via_field() {
                ComponentWithFieldInjection component = new InjectProvider<>(ComponentWithFieldInjection.class).get(context);
                assertSame(dependency, component.dependency);
            }

            @Test
            public void should_inject_dependency_via_superclass_inject_field() {
                SubclassWithFieldInjection component = new InjectProvider<>(SubclassWithFieldInjection.class).get(context);
                assertSame(dependency, component.dependency);

            }

            @Test
            public void should_include_dependency_from_field_dependency() {
                InjectProvider<ComponentWithFieldInjection> provider = new InjectProvider<>(ComponentWithFieldInjection.class);
                assertArrayEquals(new ComponentRef[]{ComponentRef.of(Dependency.class)}, provider.getDependencies().toArray(ComponentRef[]::new));
            }

            @Test
            public void should_include_provider_type_from_inject_field() {
                InjectProvider<ProviderInjectField> provider = new InjectProvider<>(ProviderInjectField.class);
                assertArrayEquals(new ComponentRef[] {ComponentRef.of(dependencyProviderType)}, provider.getDependencies().toArray(ComponentRef[]::new));
            }

            static class ProviderInjectField {
                @Inject
                Provider<Dependency> dependency;
            }

            @Test
            public void should_inject_provider_via_inject_field() {
                ProviderInjectField instance = new InjectProvider<>(ProviderInjectField.class).get(context);
                assertSame(dependencyProvider, instance.dependency);
            }

        }

        @Nested
        class IllegalInjectFields {
            static class FinalInjectField {
                @Inject
                final Dependency dependency = null;
            }

            @Test
            public void should_throw_exception_if_inject_field_is_final() {
                assertThrows(IllegalComponentException.class,
                        () -> new InjectProvider<>(FinalInjectField.class));
            }
        }

        @Nested
        class WithQualifier {

            @BeforeEach
            public void before() {
                Mockito.reset(context);
                when(context.get(eq(ComponentRef.of(Dependency.class, new NamedLiteral("ChosenOne"))))).thenReturn(Optional.of(dependency));
            }

            static class InjectField {
                @Inject
                @Named("ChosenOne")
                Dependency dependency;
            }

            @Test
            public void should_inject_dependency_with_qualifier_via_field() {
                InjectProvider<InjectField> provider = new InjectProvider<>(InjectField.class);
                InjectField component = provider.get(context);
                assertSame(dependency, component.dependency);
            }

            @Test
            public void should_include_qualifier_with_dependency() {
                InjectProvider<InjectField> provider = new InjectProvider<>(InjectField.class);
                assertArrayEquals(new ComponentRef<?>[]{
                        ComponentRef.of(Dependency.class, new NamedLiteral("ChosenOne"))
                }, provider.getDependencies().toArray(new ComponentRef[0]));

            }

            static class MultiQualifierInjectField {
                @Inject
                @Named("ChosenOne")
                @SkyWalker
                Dependency dependency;
            }

            @Test
            public void should_throw_exception_if_multi_qualifiers_given() {
                assertThrows(IllegalComponentException.class, () -> new InjectProvider<>(MultiQualifierInjectField.class));
            }
        }
    }

    @Nested
    public class MethodInjection {

        @Nested
        class Injection {
            static class InjectMethodWithNoDependency {
                boolean called;

                @Inject
                void install() {
                    this.called = true;
                }
            }

            @Test
            public void should_call_inject_method_even_if_no_dependency_declared() {
                InjectMethodWithNoDependency component = new InjectProvider<>(InjectMethodWithNoDependency.class).get(context);
                assertTrue(component.called);
            }

            static class InjectMethodWithDependency {
                Dependency dependency;

                @Inject
                void install(Dependency dependency) {
                    this.dependency = dependency;
                }
            }

            @Test
            public void should_inject_dependency_via_inject_method() {
                new InjectProvider<>(InjectMethodWithDependency.class).get(context);
                InjectMethodWithDependency component = new InjectProvider<>(InjectMethodWithDependency.class).get(context);
                assertSame(dependency, component.dependency);
            }

            static class SuperClassWithInjectMethod {
                int superCalled = 0;

                @Inject
                void install() {
                    superCalled++;
                }
            }

            static class SubclassWithInjectMethod extends SuperClassWithInjectMethod {
                int subCalled = 0;

                @Inject
                void installAnother() {
                    subCalled = superCalled + 1;
                }
            }

            @Test
            public void should_inject_dependencies_via_inject_method_from_superclass() {
                SubclassWithInjectMethod component = new InjectProvider<>(SubclassWithInjectMethod.class).get(context);
                assertEquals(1, component.superCalled);
                assertEquals(2, component.subCalled);
            }

            static class SubclassOverrideSuperClassWithInject extends SuperClassWithInjectMethod {
                @Inject
                @Override
                void install() {
                    super.install();
                }
            }

            @Test
            public void should_only_call_once_if_subclass_override_inject_method_with_inject() {
                SubclassOverrideSuperClassWithInject component = new InjectProvider<>(SubclassOverrideSuperClassWithInject.class).get(context);

                assertEquals(1, component.superCalled);
            }

            static class SubclassOverrideSuperClassWithNoInject extends SuperClassWithInjectMethod {
                void install() {
                    super.install();
                }
            }

            @Test
            public void should_not_call_inject_method_if_override_with_no_inject() {
                SubclassOverrideSuperClassWithNoInject component = new InjectProvider<>(SubclassOverrideSuperClassWithNoInject.class).get(context);

                assertEquals(0, component.superCalled);
            }

            @Test
            public void should_include_dependencies_from_inject_method() {
                InjectProvider<InjectMethodWithDependency> provider = new InjectProvider<>(InjectMethodWithDependency.class);
                assertArrayEquals(new ComponentRef[]{ComponentRef.of(Dependency.class)}, provider.getDependencies().toArray(ComponentRef[]::new));
            }

            @Test
            public void should_include_provider_type_from_inject_method() {
                InjectProvider<ProviderInjectMethod> provider = new InjectProvider<>(ProviderInjectMethod.class);
                assertArrayEquals(new ComponentRef[] {ComponentRef.of(dependencyProviderType)}, provider.getDependencies().toArray(ComponentRef[]::new));
            }

            static class ProviderInjectMethod {
                Provider<Dependency> dependency;

                @Inject
                void install(Provider<Dependency> dependency) {
                    this.dependency = dependency;
                }
            }

            @Test
            public void should_inject_provider_via_inject_method() {
                ProviderInjectMethod instance = new InjectProvider<>(ProviderInjectMethod.class).get(context);
                assertSame(dependencyProvider, instance.dependency);
            }
        }

        @Nested
        class IllegalInjectMethods {
            static class InjectMethodWithTypeParameter {
                @Inject
                <T> void install() {
                }
            }

            @Test
            public void should_throw_exception_if_inject_method_has_type_parameter() {
                assertThrows(IllegalComponentException.class, () -> new InjectProvider<>(InjectMethodWithTypeParameter.class));
            }
        }

        @Nested
        class WithQualifier {

            @BeforeEach
            public void before() {
                Mockito.reset(context);
                when(context.get(eq(ComponentRef.of(Dependency.class, new NamedLiteral("ChosenOne"))))).thenReturn(Optional.of(dependency));
            }

            static class InjectMethod {
                Dependency dependency;
                @Inject
                void install(@Named("ChosenOne")Dependency dependency) {
                    this.dependency = dependency;
                }
            }

            @Test
            public void should_inject_dependency_with_qualifier_via_method() {
                InjectProvider<InjectMethod> provider = new InjectProvider<>(InjectMethod.class);
                InjectMethod component = provider.get(context);
                assertSame(dependency, component.dependency);
            }

            @Test
            public void should_include_dependency_with_qualifier() {
                InjectProvider<InjectMethod> provider = new InjectProvider<>(InjectMethod.class);
                assertArrayEquals(new ComponentRef<?>[]{
                        ComponentRef.of(Dependency.class, new NamedLiteral("ChosenOne"))
                }, provider.getDependencies().toArray(new ComponentRef[0]));

            }

            static class MultiQualifierInjectMethod {
                @Inject
                void install(@Named("ChosenOne") @SkyWalker Dependency dependency) {
                }
            }

            @Test
            public void should_throw_exception_if_multi_qualifiers_given() {
                assertThrows(IllegalComponentException.class, () -> new InjectProvider<>(MultiQualifierInjectMethod.class));
            }
        }
    }
}
