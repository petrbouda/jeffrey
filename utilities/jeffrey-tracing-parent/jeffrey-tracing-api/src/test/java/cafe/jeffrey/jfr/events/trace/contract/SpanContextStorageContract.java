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

package cafe.jeffrey.jfr.events.trace.contract;

import cafe.jeffrey.jfr.events.trace.SpanContext;
import cafe.jeffrey.jfr.events.trace.spi.SpanContextStorage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * What every {@link SpanContextStorage} must do, run by each implementation against itself: bind
 * for exactly the duration of the body, nest, and leave the thread as it found it on both exits.
 * <p>
 * Everything built on top — ids, span events, stamping — is the Tracer's and is tested once, in
 * {@code jeffrey-events}, against each storage in turn.
 */
public abstract class SpanContextStorageContract {

    private static final SpanContext OUTER = new SpanContext(1, 2, 0);
    private static final SpanContext INNER = new SpanContext(1, 3, 2);

    protected abstract SpanContextStorage storage();

    @Test
    @DisplayName("nothing is bound outside any body")
    void unboundOutsideBody() {
        assertNull(storage().current());
    }

    @Test
    @DisplayName("the context is bound while the body runs, and the body's result is returned")
    void bindsForTheBody() {
        String result = storage().callWith(OUTER, () -> {
            assertSame(OUTER, storage().current());
            return "done";
        });

        assertEquals("done", result);
        assertNull(storage().current());
    }

    @Test
    @DisplayName("a nested binding shadows the outer one and gives it back when it ends")
    void nests() {
        storage().callWith(OUTER, () -> {
            storage().callWith(INNER, () -> {
                assertSame(INNER, storage().current());
                return null;
            });
            assertSame(OUTER, storage().current());
            return null;
        });

        assertNull(storage().current());
    }

    @Test
    @DisplayName("a checked exception passes through unchanged and the binding is still undone")
    void restoresOnException() {
        IOException failure = new IOException("boom");

        storage().callWith(OUTER, () -> {
            IOException thrown = assertThrows(IOException.class, () -> storage().callWith(INNER, () -> {
                throw failure;
            }));
            assertSame(failure, thrown);
            assertSame(OUTER, storage().current());
            return null;
        });

        assertNull(storage().current());
    }

    @Test
    @DisplayName("a binding is confined to the thread that made it")
    void threadConfined() throws InterruptedException {
        AtomicReference<SpanContext> seen = new AtomicReference<>(OUTER);

        storage().callWith(OUTER, () -> {
            Thread other = new Thread(() -> seen.set(storage().current()));
            other.start();
            other.join();
            return null;
        });

        assertNull(seen.get());
    }
}
