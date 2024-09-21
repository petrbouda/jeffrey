

package io.jafar.parser.api;

import io.jafar.parser.api.JafarParser;

public interface HandlerRegistration<T> {
    void destroy(JafarParser cookie);
}
