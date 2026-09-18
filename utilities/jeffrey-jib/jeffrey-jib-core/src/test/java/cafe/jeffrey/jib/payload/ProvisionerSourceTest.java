/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
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
