package io.github.leofuso.observable.streams.propagator;

import java.util.*;

import org.apache.kafka.common.header.*;
import org.apache.kafka.streams.processor.api.Record;

import io.micrometer.tracing.propagation.Propagator.*;

public class HeaderGetter<K, V> implements Getter<Record<K, V>> {

    @Override
    public String get(final Record<K, V> carrier, final String key) {
        return Optional.of(carrier)
                .map(Record::headers)
                .map(headers -> headers.lastHeader(key))
                .map(Header::value)
                .map(String::new)
                .orElse(null);
    }
}
