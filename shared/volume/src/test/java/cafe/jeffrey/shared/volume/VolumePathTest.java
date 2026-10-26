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

package cafe.jeffrey.shared.volume;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A volume path names nothing outside the volume, whatever it is built from — which is what lets
 * every backend map it onto its own namespace without checking it again.
 */
class VolumePathTest {

    @Nested
    class Building {

        @Test
        void theRootHasNoElements() {
            assertEquals(List.of(), VolumePath.root().segments());
            assertEquals("", VolumePath.root().toString());
            assertEquals("", VolumePath.root().name());
        }

        @Test
        void resolvingAddsOneElement() {
            VolumePath session = VolumePath.root().resolve("ws").resolve("pod-a").resolve("s-1");

            assertEquals(List.of("ws", "pod-a", "s-1"), session.segments());
            assertEquals("ws/pod-a/s-1", session.toString());
            assertEquals("s-1", session.name());
            assertEquals(VolumePath.root().resolve("ws").resolve("pod-a"), session.parent());
        }

        @Test
        void theRootHasNoParent() {
            assertThrows(IllegalStateException.class, () -> VolumePath.root().parent());
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"", " ", ".", "..", "a/b", "../a", "a\0b", "a\\b", "a\nb", "a\rb", "a\tb"})
        void refusesAnElementThatIsNotOnePlainName(String name) {
            assertThrows(IllegalArgumentException.class, () -> VolumePath.root().resolve(name));
        }

        @Test
        void refusesTheSameElementsWhenGivenAllAtOnce() {
            assertThrows(IllegalArgumentException.class, () -> new VolumePath(List.of("ws", "..", "etc")));
        }
    }

    /** Ids arrive over gRPC and from agents; one must never name a directory it does not belong to. */
    @Nested
    class AChildNamedByAnId {

        @Test
        void isThatChild() {
            assertEquals(Optional.of(VolumePath.root().resolve("s-1")), VolumePath.root().visibleChild("s-1"));
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"", "..", "../s-1", "a/b", ".retained", ".heartbeat"})
        void isNothingForAnIdThatWalksOutOrNamesBookkeeping(String id) {
            assertTrue(VolumePath.root().visibleChild(id).isEmpty());
        }
    }

    @Test
    void aLeadingDotMarksTheVolumesOwnBookkeeping() {
        assertTrue(VolumePath.root().resolve(".session-info.json").isHidden());
        assertFalse(VolumePath.root().resolve("profile-1.jfr").isHidden());
    }
}
