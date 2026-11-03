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

package cafe.jeffrey.microscope.model.connector;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalDirectoryAddressTest {

    private static final Path VOLUME = Path.of("/tmp/jeffrey-volume");

    @Test
    void isStoredNormalized() {
        assertEquals(VOLUME, new LocalDirectoryAddress(Path.of("/tmp/./other/../jeffrey-volume"), false).root());
    }

    @Test
    void refusesARelativePath() {
        assertThrows(IllegalArgumentException.class, () -> new LocalDirectoryAddress(Path.of("jeffrey-volume"), false));
    }

    @Test
    void refusesNoPath() {
        assertThrows(IllegalArgumentException.class, () -> new LocalDirectoryAddress(null, false));
    }

    /** The identity names the directory alone: read-only or not, it is the same connector. */
    @Test
    void identityIsTheKindAndTheDirectory() {
        LocalDirectoryAddress writable = new LocalDirectoryAddress(VOLUME, false);
        LocalDirectoryAddress readOnly = new LocalDirectoryAddress(VOLUME, true);

        assertEquals("local:" + VOLUME, writable.identity());
        assertEquals(writable.identity(), readOnly.identity());
        assertNotEquals(writable.identity(),
                new AzureFilesAddress("https://acct.file.core.windows.net", "share", "", AzureFilesProtocol.SMB,
                        AzureFilesAuth.SAS, false).identity());
    }

    @Test
    void saysWhenItIsReadOnly() {
        assertTrue(new LocalDirectoryAddress(VOLUME, true).toString().endsWith("(read-only)"));
        assertFalse(new LocalDirectoryAddress(VOLUME, false).toString().contains("read-only"));
    }

    @Test
    void isOfTheLocalKind() {
        assertEquals(ConnectorKind.LOCAL, new LocalDirectoryAddress(VOLUME, false).kind());
        assertEquals(VOLUME.toString(), new LocalDirectoryAddress(VOLUME, false).location());
    }
}
