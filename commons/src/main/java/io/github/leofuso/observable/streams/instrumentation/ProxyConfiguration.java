package io.github.leofuso.observable.streams.instrumentation;

import java.lang.reflect.*;

import net.bytebuddy.implementation.bind.annotation.*;

/**
 * A proxy configuration facilitates the definition of an interceptor object that decides on the behavior of a proxy. This interface is
 * meant for internal only.
 *<p>
 * Heavily inspired by the <a href="https://github.com/hibernate/hibernate-orm/blob/main/hibernate-core/src/main/java/org/hibernate/proxy/ProxyConfiguration.java">Hibernate implementation</a>.
 */
public interface ProxyConfiguration {

    /**
     * The canonical field name for an interceptor object stored in a proxied object.
     */
    String INTERCEPTOR_FIELD_NAME = "$$_observable_streams_interceptor";

    /**
     * Defines an interceptor object that specifies the behavior of the proxy object.
     *
     * @param interceptor The interceptor object.
     */
    void $$_observable_streams_set_interceptor(Interceptor interceptor);

    /**
     * An interceptor object that is responsible for invoking a proxy's method.
     */
    interface Interceptor {

        /**
         * Intercepts a method call to a proxy.
         *
         * @param instance  The proxied instance.
         * @param invoked   The invoked method.
         * @param original  The original method, target of the invocation.
         * @param arguments The intercepted method arguments.
         * @return The method's return value.
         *
         * @throws Throwable If the intercepted method raises an exception.
         */
        @RuntimeType
        Object intercept(
                @This Object instance,
                @Origin Method invoked,
                @SuperMethod Method original,
                @AllArguments Object[] arguments
        ) throws Throwable;
    }


    /**
     * A static interceptor that guards method calls before the interceptor is set.
     */
    class InterceptorDispatcher {

        /**
         * Intercepts a method call to a proxy.
         *
         * @param instance    The proxied instance.
         * @param invoked     The invoked method.
         * @param original    The original method, target of the invocation.
         * @param arguments   The method arguments.
         * @param stubValue   The intercepted method's default value.
         * @param interceptor The proxy object's interceptor instance.
         * @return The intercepted method's return value.
         *
         * @throws Throwable If the intercepted method raises an exception.
         */
        @RuntimeType
        public static Object intercept(
                @This final Object instance,
                @Origin Method invoked,
                @SuperMethod Method original,
                @AllArguments final Object[] arguments,
                @FieldValue(INTERCEPTOR_FIELD_NAME) Interceptor interceptor
        ) throws Throwable {
            if (interceptor == null) {
                return original.invoke(instance, arguments);
            } else {
                return interceptor.intercept(instance, invoked, original, arguments);
            }
        }
    }
}
