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

package cafe.jeffrey.jfr.events.trace;

import cafe.jeffrey.jfr.events.trace.spi.SpanContextStorage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpanContextStoragesTest {

    @Test
    @DisplayName("the candidate with the highest priority wins, whatever the discovery order")
    void highestPriorityWins() {
        SpanContextStorage low = storage(0);
        SpanContextStorage high = storage(100);

        assertSame(high, SpanContextStorages.choose(List.of(low, high)));
        assertSame(high, SpanContextStorages.choose(List.of(high, low)));
    }

    @Test
    @DisplayName("with nothing to choose from, the error names both artifacts")
    void noneNamesBothArtifacts() {
        IllegalStateException error =
                assertThrows(IllegalStateException.class, () -> SpanContextStorages.choose(List.of()));

        assertTrue(error.getMessage().contains("jeffrey-tracing-scoped-value "));
        assertTrue(error.getMessage().contains("jeffrey-tracing-thread-local"));
    }

    @Test
    @DisplayName("this module's own class path carries no storage, so loading fails the same way")
    void loadWithoutImplementationFails() {
        assertThrows(IllegalStateException.class, SpanContextStorages::load);
    }

    private static SpanContextStorage storage(int priority) {
        return new SpanContextStorage() {
            @Override
            public SpanContext current() {
                return null;
            }

            @Override
            public <R, X extends Throwable> R callWith(SpanContext context, SpanBody<? extends R, X> body) throws X {
                return body.call();
            }

            @Override
            public int priority() {
                return priority;
            }
        };
    }
}
