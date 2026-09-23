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

package cafe.jeffrey.profile.thread;

import org.junit.jupiter.api.Test;
import cafe.jeffrey.shared.common.Json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ThreadPeriodSerdeTest {

    /**
     * The timeline is cached as JSON in the profile database and read back on every later request,
     * so a band has to survive a round-trip. A derived accessor leaking into the output would be
     * written but not accepted back, and the timeline would fail to load from cache.
     */
    @Test
    void survivesTheCacheRoundTrip() {
        ThreadPeriod band = new ThreadPeriod(1_000, 500, 7);

        String json = Json.toString(band);

        assertFalse(json.contains("endOffset"), "Only the band's own components belong on the wire: " + json);

        ThreadPeriod read = Json.read(json, ThreadPeriod.class);
        assertEquals(band, read);
    }
}
