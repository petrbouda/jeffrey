/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cafe.jeffrey.jfr.events.trace.threadlocal;

import cafe.jeffrey.jfr.events.trace.SpanBody;
import cafe.jeffrey.jfr.events.trace.SpanContext;
import cafe.jeffrey.jfr.events.trace.spi.SpanContextStorage;

/**
 * Keeps the span in progress in a {@link ThreadLocal}, for JVMs older than 25. Each binding is set
 * around its body and the previous value restored in {@code finally}, so a binding never outlives
 * its span and a pooled thread is handed back with whatever it had before.
 */
public final class ThreadLocalSpanContextStorage implements SpanContextStorage {

    /**
     * Below {@code ScopedValueSpanContextStorage}: this one is used only where that cannot load.
     */
    private static final int PRIORITY = 0;

    private static final ThreadLocal<SpanContext> CURRENT = new ThreadLocal<>();

    @Override
    public SpanContext current() {
        return CURRENT.get();
    }

    @Override
    public <R, X extends Throwable> R callWith(SpanContext context, SpanBody<? extends R, X> body) throws X {
        SpanContext previous = CURRENT.get();
        CURRENT.set(context);
        try {
            return body.call();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }

    @Override
    public int priority() {
        return PRIORITY;
    }
}
