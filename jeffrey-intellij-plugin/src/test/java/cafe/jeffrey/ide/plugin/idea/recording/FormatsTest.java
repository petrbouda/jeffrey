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

package cafe.jeffrey.ide.plugin.idea.recording;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * The panel's four figures, as they are actually printed. Worth pinning because the panel shows them
 * beside Microscope's own pages: a recording that reads 8.1 MB in one and 8.06 MB in the other looks
 * like two different files.
 */
public class FormatsTest {

    @Test
    public void printsSizesTheWayMicroscopeDoes() {
        assertEquals("512 B", Formats.bytes(512));
        assertEquals("1.0 KB", Formats.bytes(1024));
        assertEquals("8.1 MB", Formats.bytes(8_450_244L));
        assertEquals("1.5 GB", Formats.bytes(1_610_612_736L));
    }

    @Test
    public void printsSampleCountsAtAGlance() {
        assertEquals("842", Formats.count(842));
        assertEquals("12.4 K", Formats.count(12_400));
        assertEquals("1.24 M", Formats.count(1_240_000));
        assertEquals("2.10 B", Formats.count(2_100_000_000L));
    }

    /** Sub-minute recordings are the common case, and a run of 42.3 s reading "0 m" would be useless. */
    @Test
    public void printsShortWindowsInSeconds() {
        assertEquals("42.3 s", Formats.duration(42_300));
        assertEquals("2 m 5 s", Formats.duration(125_000));
        assertEquals("1 h 5 m", Formats.duration(3_900_000));
    }

    @Test
    public void saysUnknownRatherThanZeroForAnUnmeasuredWindow() {
        assertEquals("unknown", Formats.duration(0));
        assertEquals("unknown", Formats.duration(-1));
    }

    /**
     * The distinction that matters most here. A recording made with the older sampler reports no loss
     * at all, and printing that as "0%" would claim the samples are known to be complete.
     */
    @Test
    public void separatesNoLossFromNoMeasurement() {
        assertEquals("not reported", Formats.lossRatio(-1));
        assertEquals("0%", Formats.lossRatio(0));
        assertEquals("<0.1%", Formats.lossRatio(0.0004));
        assertEquals("12.5%", Formats.lossRatio(0.125));
    }
}
