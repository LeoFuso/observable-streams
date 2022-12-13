package io.github.leofuso.observable.streams.propagator;

import java.nio.charset.*;
import java.util.*;

import org.apache.kafka.streams.processor.api.Record;

import io.micrometer.tracing.propagation.Propagator.*;

public class HeaderSetter<K, V> implements Setter<Record<K, V>> {

    @Override
    public void set(final Record<K, V> carrier, final String key, final String value) {
        Optional.ofNullable(carrier)
                .map(Record::headers)
                .map(headers -> headers.add(key, value.getBytes(StandardCharsets.UTF_8)));
    }
}
