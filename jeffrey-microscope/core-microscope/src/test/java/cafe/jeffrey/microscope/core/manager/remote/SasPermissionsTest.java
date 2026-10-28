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

package cafe.jeffrey.microscope.core.manager.remote;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SasPermissionsTest {

    @Test
    void readsTheRightsAndNothingElseOfTheToken() {
        SasPermissions permissions = SasPermissions.of("?sv=2026-06-06&ss=f&srt=sco&sp=rl&sig=SECRET");

        assertEquals("lr", permissions.toString());
        assertFalse(permissions.grantsChanges());
        assertFalse(permissions.grantsEveryChange());
    }

    /** A writable source reads, lists and deletes: it never creates or writes. */
    @Test
    void aTokenForAWritableSourceNeedsReadListAndDelete() {
        SasPermissions permissions = SasPermissions.of("sv=2026-06-06&sp=rld&sig=SECRET");

        assertTrue(permissions.grantsChanges());
        assertTrue(permissions.grantsEveryChange());
        assertFalse(SasPermissions.of("sv=2026-06-06&sp=rlcw&sig=SECRET").grantsEveryChange());
    }

    @Test
    void aTokenNamingNoRightsGrantsNone() {
        SasPermissions permissions = SasPermissions.of("sv=2026-06-06&sig=SECRET");

        assertEquals("", permissions.toString());
        assertFalse(permissions.grantsChanges());
    }
}
