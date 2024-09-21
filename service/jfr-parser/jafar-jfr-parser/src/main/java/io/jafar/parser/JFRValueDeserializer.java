

package io.jafar.parser;

import io.jafar.parser.internal_api.DeserializationHandler;
import io.jafar.parser.internal_api.RecordingStream;

import java.lang.invoke.MethodHandle;

public final class JFRValueDeserializer<T> implements DeserializationHandler<T> {
    private final Class<T> clazz;
    private MethodHandle handler = null;

    private JFRValueDeserializer(Class<T> clazz) {
        this.clazz = clazz;
    }

    public static <T> JFRValueDeserializer<T> create(Class<T> clazz) {
        return new JFRValueDeserializer<>(clazz);
    }

    public void setHandler(MethodHandle handler) {
        this.handler = handler;
    }

    public Class<T> getClazz() {
        return clazz;
    }

    public JFRValueDeserializer<T> duplicate() {
        return new JFRValueDeserializer<>(clazz);
    }

    @SuppressWarnings("unchecked")
    @Override
    public T handle(RecordingStream stream) {
        try {
            T value = handler != null ? (T) handler.invoke(stream) : null;
            return value;
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }
}
