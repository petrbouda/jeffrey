

package io.jafar.parser.internal_api.metadata;

@FunctionalInterface
public interface ExceptionalConsumer<T, E extends Exception> {
    void accept(T value) throws E;
}
