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

package cafe.jeffrey.jib.payload;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProvisionerSourceTest {

    @Test
    void aMissingKindIsABrokenPayloadNotADefault() {
        // The kind is stated by the payload jar that was published; there is nothing to fall
        // back to, and treating a blank as native would hide a mis-built payload.
        assertThrows(IllegalArgumentException.class, () -> ProvisionerSource.parse(null));
        assertThrows(IllegalArgumentException.class, () -> ProvisionerSource.parse(""));
        assertThrows(IllegalArgumentException.class, () -> ProvisionerSource.parse("   "));
    }

    @Test
    void parsingIgnoresCaseAndSurroundingSpace() {
        assertSame(ProvisionerSource.JAR, ProvisionerSource.parse("jar"));
        assertSame(ProvisionerSource.JAR, ProvisionerSource.parse("JAR"));
        assertSame(ProvisionerSource.JAR, ProvisionerSource.parse(" Jar "));
    }

    @Test
    void anUnknownValueIsRejectedRatherThanDefaulted() {
        // Quietly treating an unknown kind as native would surface only as a container that
        // fails to profile, long after the build passed.
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class, () -> ProvisionerSource.parse("graalvm"));

        assertTrue(ex.getMessage().contains("native"), ex.getMessage());
        assertTrue(ex.getMessage().contains("jar"), ex.getMessage());
    }

    @Test
    void kindIsWhatTheEntrypointReads() {
        assertEquals("native", ProvisionerSource.NATIVE.kind());
        assertEquals("jar", ProvisionerSource.JAR.kind());
    }
}
