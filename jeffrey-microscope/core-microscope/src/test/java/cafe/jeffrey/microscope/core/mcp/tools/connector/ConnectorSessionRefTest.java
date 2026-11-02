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

package cafe.jeffrey.microscope.core.mcp.tools.connector;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectorSessionRefTest {

    private static final ConnectorSessionRef REF =
            new ConnectorSessionRef("ws-1", "proj-1", "session-1");

    @Nested
    class Encoding {

        @Test
        void roundTripsEveryComponent() {
            assertEquals(REF, ConnectorSessionRef.decode(REF.encode()));
        }

        @Test
        void producesOnlyCharactersSafeInATableCellAndInJson() {
            // A pipe would split the Markdown column the ref is printed in; a quote or backslash
            // would have to survive JSON escaping on the way back.
            assertTrue(REF.encode().matches("^r1[A-Za-z0-9_-]+$"), REF.encode());
        }

        @Test
        void isStableAcrossCalls() {
            assertEquals(REF.encode(), REF.encode());
        }

        @Test
        void keepsASeparatorInsideASessionId() {
            // The session id is the one component minted outside Jeffrey, so it is decoded from the
            // tail and a separator in it has to survive.
            ConnectorSessionRef piped = new ConnectorSessionRef("ws-1", "proj-1", "odd|session|id");

            assertEquals(piped, ConnectorSessionRef.decode(piped.encode()));
        }

        @Test
        void toleratesSurroundingWhitespaceWhenReadingBack() {
            assertEquals(REF, ConnectorSessionRef.decode("  " + REF.encode() + "\n"));
        }
    }

    @Nested
    class Decoding {

        @Test
        void rejectsARefWithoutThePrefix() {
            assertThrows(IllegalArgumentException.class, () -> ConnectorSessionRef.decode("session-1"));
        }

        @Test
        void rejectsUndecodableContent() {
            assertThrows(IllegalArgumentException.class, () -> ConnectorSessionRef.decode("r1not base64!"));
        }

        @Test
        void rejectsARefWithTooFewComponents() {
            String tooFew = "r1" + java.util.Base64.getUrlEncoder().withoutPadding()
                    .encodeToString("only-one".getBytes(java.nio.charset.StandardCharsets.UTF_8));

            assertThrows(IllegalArgumentException.class, () -> ConnectorSessionRef.decode(tooFew));
        }

        @Test
        void rejectsARefWithABlankComponent() {
            String blankProject = "r1" + java.util.Base64.getUrlEncoder().withoutPadding()
                    .encodeToString("ws||session".getBytes(java.nio.charset.StandardCharsets.UTF_8));

            assertThrows(IllegalArgumentException.class, () -> ConnectorSessionRef.decode(blankProject));
        }

        @Test
        void rejectsNothingAtAll() {
            assertThrows(IllegalArgumentException.class, () -> ConnectorSessionRef.decode(null));
            assertThrows(IllegalArgumentException.class, () -> ConnectorSessionRef.decode("  "));
        }

        @Test
        void namesTheToolThatProducesAValidRef() {
            // The message is the model's only route back to a working call, so it has to say where
            // a good ref comes from rather than only that this one is bad.
            IllegalArgumentException e = assertThrows(
                    IllegalArgumentException.class, () -> ConnectorSessionRef.decode("nonsense"));

            assertTrue(e.getMessage().contains("connector_sessions"), e.getMessage());
        }
    }

    @Nested
    class Validation {

        @Test
        void rejectsAMissingComponent() {
            assertThrows(IllegalArgumentException.class,
                    () -> new ConnectorSessionRef(null, "proj-1", "session-1"));
            assertThrows(IllegalArgumentException.class,
                    () -> new ConnectorSessionRef("ws-1", "proj-1", " "));
        }

        @Test
        void rejectsASeparatorInAServerMintedComponent() {
            // Caught where such an id would be introduced, rather than mis-splitting on the way back.
            assertThrows(IllegalArgumentException.class,
                    () -> new ConnectorSessionRef("ws|1", "proj-1", "session-1"));
            assertThrows(IllegalArgumentException.class,
                    () -> new ConnectorSessionRef("ws-1", "proj|1", "session-1"));
        }
    }
}
