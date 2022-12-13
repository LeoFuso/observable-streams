package io.github.leofuso.observable.streams.instrumentation;

import io.micrometer.tracing.*;

import io.micrometer.tracing.propagation.*;

import net.bytebuddy.*;
import net.bytebuddy.description.method.*;
import net.bytebuddy.description.modifier.*;
import net.bytebuddy.dynamic.*;
import net.bytebuddy.dynamic.loading.*;
import net.bytebuddy.implementation.*;
import net.bytebuddy.pool.*;
import net.bytebuddy.pool.TypePool.*;

import java.util.function.*;

import static net.bytebuddy.matcher.ElementMatchers.*;

public class InterceptorSetup implements Runnable {

    @Override
    public void run() {

        final ClassFileLocator classFileLocator = ClassFileLocator.ForClassLoader.ofSystemLoader();
        final TypePool typePool = TypePool.Default.ofSystemLoader();

        final Resolution tracingSupplier = typePool.describe("io.github.leofuso.observable.streams.processor.TracingProcessorSupplier");
        final Resolution processorSupplier = typePool.describe("org.apache.kafka.streams.processor.api.ProcessorSupplier");
        final Resolution processor = typePool.describe("org.apache.kafka.streams.processor.api.Processor");

        new ByteBuddy()
                .rebase(processorSupplier.resolve(), classFileLocator)
                .method(
                        named("get")
                                .and(returns(processor.resolve()))
                                .and(takesNoArguments())
                                .and(MethodDescription::isMethod)
                                .and(not(isDeclaredBy(tracingSupplier.resolve()))) // This probably isn't needed.
                )
                .intercept(MethodDelegation.to(ProxyConfiguration.InterceptorDispatcher.class))
                .defineField(ProxyConfiguration.INTERCEPTOR_FIELD_NAME, ProxyConfiguration.Interceptor.class, Visibility.PRIVATE)
                .make()
                .load(ClassLoader.getSystemClassLoader(), ClassLoadingStrategy.Default.INJECTION);
    }

    private Function<ByteBuddy, DynamicType.Builder<?>> apply(final Tracer tracer, final Propagator propagator) {
        final TracingInterceptor<?, ?, ?, ?> interceptor = new TracingInterceptor<>(tracer, propagator);
        return byteBuddy -> {
            throw new UnsupportedOperationException();
        };
    }
}
