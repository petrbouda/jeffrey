

package io.jafar.parser.api.types;

import io.jafar.parser.api.Control;

@FunctionalInterface
public interface JFRHandler<T> {
    static class Impl<T> {
        private final Class<T> clazz;
        private final JFRHandler<T> handler;

        public Impl(Class<T> clazz, JFRHandler<T> handler) {
            this.clazz = clazz;
            this.handler = handler;
        }

        public void handle(Object event, Control ctl) {
            handler.handle(clazz.cast(event), ctl);
        }
    }

    void handle(T event, Control ctl);
}
