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

package cafe.jeffrey.jfr.events.trace.scoped;

import cafe.jeffrey.jfr.events.trace.SpanBody;
import cafe.jeffrey.jfr.events.trace.SpanContext;
import cafe.jeffrey.jfr.events.trace.spi.SpanContextStorage;

/**
 * Keeps the span in progress in a {@link ScopedValue}. The binding is bounded by the body by
 * construction, and threads forked through structured concurrency inherit it.
 */
public final class ScopedValueSpanContextStorage implements SpanContextStorage {

    /**
     * Above {@code ThreadLocalSpanContextStorage}: with both on the class path, a JVM that can load
     * this one uses it.
     */
    private static final int PRIORITY = 100;

    private static final ScopedValue<SpanContext> CURRENT = ScopedValue.newInstance();

    @Override
    public SpanContext current() {
        // Not orElse(null): ScopedValue.orElse rejects a null fallback.
        return CURRENT.isBound() ? CURRENT.get() : null;
    }

    @Override
    public <R, X extends Throwable> R callWith(SpanContext context, SpanBody<? extends R, X> body) throws X {
        return ScopedValue.where(CURRENT, context).call(body::call);
    }

    @Override
    public int priority() {
        return PRIORITY;
    }
}
