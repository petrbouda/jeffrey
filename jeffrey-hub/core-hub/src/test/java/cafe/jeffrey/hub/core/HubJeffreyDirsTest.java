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

package cafe.jeffrey.hub.core;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HubJeffreyDirsTest {

    /** A relative home with dots would otherwise make every directory resolved under it a stranger to it. */
    @Test
    void homeIsAbsoluteAndNormalized() {
        HubJeffreyDirs dirs = new HubJeffreyDirs(Path.of("./hub/../hub"));

        assertTrue(dirs.homeDir().isAbsolute());
        assertEquals(dirs.homeDir(), dirs.homeDir().normalize());
        assertEquals(dirs.workspaces().resolve("ws").normalize().getParent(), dirs.workspaces());
    }
}
