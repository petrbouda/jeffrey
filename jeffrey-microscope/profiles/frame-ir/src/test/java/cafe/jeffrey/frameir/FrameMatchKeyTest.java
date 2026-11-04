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

package cafe.jeffrey.frameir;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class FrameMatchKeyTest {

    /** The SQLite driver's library as two runs of jeffrey-testapp-server unpacked it. */
    private static final String FIRST_RUN = "/tmp/sqlite-3.53.4.0-53d288d8-0e69-4a19-940f-2f0eabc33180-libsqlitejdbc.so";
    private static final String SECOND_RUN = "/tmp/sqlite-3.53.4.0-40f3afff-c82e-434d-806d-fc77c2aa2e39-libsqlitejdbc.so";

    @Test
    void oneLibraryUnpackedByTwoRunsHasOneKey() {
        assertEquals("/tmp/sqlite-3.53.4.0-*-libsqlitejdbc.so", FrameMatchKey.of(FIRST_RUN));
        assertEquals(FrameMatchKey.of(FIRST_RUN), FrameMatchKey.of(SECOND_RUN));
    }

    @Test
    void anotherVersionOfTheLibraryIsAnotherKey() {
        assertNotEquals(FrameMatchKey.of(FIRST_RUN),
                FrameMatchKey.of("/tmp/sqlite-3.54.0.0-53d288d8-0e69-4a19-940f-2f0eabc33180-libsqlitejdbc.so"));
    }

    @Test
    void anythingButALibrarysPathIsMatchedByItsName() {
        String javaFrame = "com.example.Service#handle-53d288d8-0e69-4a19-940f-2f0eabc33180";
        assertEquals(javaFrame, FrameMatchKey.of(javaFrame));
        assertEquals("/usr/lib/x86_64-linux-gnu/libc.so.6", FrameMatchKey.of("/usr/lib/x86_64-linux-gnu/libc.so.6"));
    }
}
