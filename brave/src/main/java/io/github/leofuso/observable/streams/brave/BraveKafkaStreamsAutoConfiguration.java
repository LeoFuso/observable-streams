package io.github.leofuso.observable.streams.brave;

import org.springframework.beans.*;
import org.springframework.beans.factory.*;
import org.springframework.beans.factory.config.*;
import org.springframework.boot.actuate.autoconfigure.tracing.*;
import org.springframework.boot.autoconfigure.*;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.context.annotation.*;
import org.springframework.kafka.annotation.*;
import org.springframework.kafka.config.*;
import org.springframework.lang.*;

import org.apache.kafka.streams.*;

import io.github.leofuso.observable.streams.instrumentation.*;
import io.github.leofuso.observable.streams.instrumentation.ProxyConfiguration.*;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.*;

import net.bytebuddy.*;
import net.bytebuddy.description.method.*;
import net.bytebuddy.description.modifier.*;
import net.bytebuddy.dynamic.*;
import net.bytebuddy.dynamic.loading.*;
import net.bytebuddy.implementation.*;
import net.bytebuddy.pool.*;

import brave.*;
import brave.kafka.streams.*;

import static net.bytebuddy.matcher.ElementMatchers.*;

@AutoConfiguration(before = { KafkaStreamsDefaultConfiguration.class })
@ConditionalOnEnabledTracing
@ConditionalOnBean(Tracing.class)
@ConditionalOnClass({ KafkaStreams.class, StreamsBuilderFactoryBean.class })
public class BraveKafkaStreamsAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    KafkaStreamsTracingInterceptorPostProcessor byteBuddyPostProcessor() {
        return new KafkaStreamsTracingInterceptorPostProcessor();
    }

    @Bean
    @ConditionalOnMissingBean(name = { "tracingInterceptor" })
    Interceptor tracingInterceptor(final Tracer tracer, final Propagator propagator) {
        return new TracingInterceptor<>(tracer, propagator);
    }

    @Bean
    @ConditionalOnMissingBean
    KafkaStreamsTracing kafkaStreamsTracing(final Tracing tracing) {
        return KafkaStreamsTracing.create(tracing);
    }

//    @Bean
//    @ConditionalOnMissingBean(name = { "tracingClientConfigurer" })
//    StreamsBuilderFactoryBeanConfigurer tracingClientConfigurer(final ObjectProvider<KafkaStreamsTracing> provider) {
//        return factoryBean -> provider.ifAvailable(client -> factoryBean.setClientSupplier(client.kafkaClientSupplier()));
//    }
}


class KafkaStreamsTracingInterceptorPostProcessor implements BeanPostProcessor {

    @Override
    @SuppressWarnings("resource")
    public Object postProcessAfterInitialization(@NonNull final Object bean, @NonNull final String beanName) throws BeansException {
        if (bean instanceof TracingInterceptor<?, ?, ?, ?> interceptor && "tracingInterceptor".equals(beanName)) {

            final ClassFileLocator classFileLocator = ClassFileLocator.ForClassLoader.ofSystemLoader();
            final TypePool typePool = TypePool.Default.ofSystemLoader();

            final TypePool.Resolution
                    tracingSupplier = typePool.describe("io.github.leofuso.observable.streams.processor.TracingProcessorSupplier");
            final TypePool.Resolution processorSupplier = typePool.describe("org.apache.kafka.streams.processor.api.ProcessorSupplier");
            final TypePool.Resolution processor = typePool.describe("org.apache.kafka.streams.processor.api.Processor");

            new ByteBuddy()
                    .rebase(processorSupplier.resolve(), classFileLocator)
                    .method(
                            named("get")
                                    .and(returns(processor.resolve()))
                                    .and(takesNoArguments())
                                    .and(MethodDescription::isMethod)
                                    .and(not(isDeclaredBy(tracingSupplier.resolve()))) // This probably isn't needed.
                    )
                    .intercept(MethodDelegation.to(interceptor))
                    .defineField(ProxyConfiguration.INTERCEPTOR_FIELD_NAME, ProxyConfiguration.Interceptor.class, Visibility.PRIVATE)
                    .make()
                    .load(ClassLoader.getSystemClassLoader(), ClassLoadingStrategy.Default.INJECTION);

        }
        return bean;
    }
}
