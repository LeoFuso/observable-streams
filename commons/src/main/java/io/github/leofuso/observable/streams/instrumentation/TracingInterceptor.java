package io.github.leofuso.observable.streams.instrumentation;

import java.lang.reflect.*;
import java.util.*;

import org.apache.kafka.streams.processor.api.*;

import io.github.leofuso.observable.streams.processor.*;
import io.micrometer.tracing.*;
import io.micrometer.tracing.propagation.*;

public class TracingInterceptor<KIn, VIn, KOut, VOut> implements ProxyConfiguration.Interceptor {

    private final Tracer tracer;
    private final Propagator propagator;

    public TracingInterceptor(final Tracer tracer, final Propagator propagator) {
        this.tracer = Objects.requireNonNull(tracer, "Tracer [tracer] is required.");
        this.propagator = Objects.requireNonNull(propagator, "Propagator [propagator] is required.");
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object intercept(final Object instance, final Method invoked, final Method original, final Object[] arguments) throws Throwable {
        final Processor<KIn, VIn, KOut, VOut> delegate = (Processor<KIn, VIn, KOut, VOut>) original.invoke(invoked);
        final TracingProcessorSupplier<KIn, VIn, KOut, VOut> tracingSupplier = new TracingProcessorSupplier<>(tracer, propagator, delegate);
        return tracingSupplier.get();
    }
}
