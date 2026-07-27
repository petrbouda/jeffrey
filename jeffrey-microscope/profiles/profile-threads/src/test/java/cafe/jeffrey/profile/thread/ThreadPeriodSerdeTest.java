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
