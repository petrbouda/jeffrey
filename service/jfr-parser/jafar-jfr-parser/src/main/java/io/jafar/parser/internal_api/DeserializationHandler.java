

package io.jafar.parser.internal_api;

import io.jafar.parser.internal_api.RecordingStream;

@FunctionalInterface
public interface DeserializationHandler<T> {
    T handle(RecordingStream stream);
}
