package io.github.leofuso.observable.streams.processor;

import java.util.*;

import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.processor.api.*;
import org.apache.kafka.streams.processor.internals.*;

import io.github.leofuso.observable.streams.propagator.*;
import io.micrometer.tracing.*;
import io.micrometer.tracing.propagation.*;
import io.micrometer.tracing.propagation.Propagator.*;

public class TracingProcessorSupplier<KIn, VIn, KOut, VOut> implements ProcessorSupplier<KIn, VIn, KOut, VOut> {

    private final Tracer tracer;
    private final Propagator propagator;

    private final Getter<Record<KIn, VIn>> getter;
    private final Setter<Record<KIn, VIn>> setter;

    private final Processor<KIn, VIn, KOut, VOut> delegate;

    public TracingProcessorSupplier(final Tracer tracer, final Propagator propagator, final Processor<KIn, VIn, KOut, VOut> delegate) {
        this.tracer = Objects.requireNonNull(tracer, "Tracer [tracer] is required.");
        this.propagator = Objects.requireNonNull(propagator, "Propagator [propagator] is required.");
        this.delegate = Objects.requireNonNull(delegate, "Processor [delegate] is required.");
        getter = new HeaderGetter<>();
        setter = new HeaderSetter<>();
    }

    @Override
    public Processor<KIn, VIn, KOut, VOut> get() {
        return new TracingProcessor();
    }

    private String getSpanNameFromNamedNode(final ProcessorContext<KOut, VOut> context) {
        if (context instanceof ProcessorContextImpl named) {
            try {
                return named.currentNode()
                        .name();
            } catch (final UnsupportedOperationException ignored) {
                /* Ignored */
            }
        }
        return "unnamed-processor";
    }


    private class TracingProcessor extends ContextualProcessor<KIn, VIn, KOut, VOut> {

        @Override
        public void init(final ProcessorContext<KOut, VOut> context) {
            super.init(context);
            delegate.init(context);
        }

        @Override
        public void process(final Record<KIn, VIn> record) {

            final ProcessorContext<KOut, VOut> context = context();
            final Span.Builder spanBuilder = propagator.extract(record, getter);

            final String name = getSpanNameFromNamedNode(context);
            spanBuilder.name(name);

            spanBuilder.tag("kafka.streams.application.id", context.applicationId());
            spanBuilder.tag("kafka.streams.task.id", String.valueOf(context.taskId()));
            context.recordMetadata()
                    .ifPresent(metadata -> {
                        spanBuilder.tag("kafka.streams.offset", String.valueOf(metadata.offset()));
                        spanBuilder.tag("kafka.streams.topic", "%s-%s".formatted(metadata.topic(), metadata.partition()));
                    });

            final Span span = spanBuilder.start();

            Throwable error = null;
            try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
                delegate.process(record);
            } catch (final Throwable throwable) {
                error = throwable;
                throw throwable;
            } finally {
                final TraceContext traceContext = span.context();
                propagator.inject(traceContext, record, setter);
                if (error != null) {
                    span.error(error);
                }
                span.end();
            }
        }

        @Override
        public void close() {
            delegate.close();
            super.close();
        }
    }
}
