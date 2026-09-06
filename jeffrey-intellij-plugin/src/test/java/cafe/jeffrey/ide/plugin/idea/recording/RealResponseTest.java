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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The contract, pinned to a response Microscope actually produced.
 *
 * <p>Captured from a live run: {@code jeffrey-20260904-180108.jfr}, 8.1 MB, imported and analysed
 * through {@code POST /recordings/from-path} then {@code /analyze}, read back from
 * {@code GET /recordings/by-path}. Every other test here builds its own JSON, which proves the parser
 * reads what this plugin writes; this one proves it reads what the other side writes.
 *
 * <p>Re-capture it when the endpoint changes shape. A green suite against invented JSON and a panel
 * showing blanks against the real thing is the failure this is here to prevent.
 */
public class RealResponseTest {

    private static final String CAPTURED = """
            {
              "state": "READY",
              "recordingId": "01a0769f-816e-73d1-a2c5-5f0e17a68175",
              "profileId": "01a0769f-a1bb-744f-962d-88b314030196",
              "filename": "jeffrey-20260904-180108.jfr",
              "sizeInBytes": 8450244,
              "summary": {
                "profileName": "jeffrey-20260904-180108.jfr",
                "profilingStartedAt": 1788537668917,
                "profilingFinishedAt": 1788537674457,
                "durationInMillis": 5539,
                "sampleCount": 44099,
                "eventTypeCount": 106,
                "capturedSamples": 353,
                "lostSamples": 222,
                "analysisComputed": false,
                "findings": []
              }
            }
            """;

    @Test
    public void readsALiveMicroscopeResponse() {
        RecordingState state = MicroscopeJson.parseState(CAPTURED, "fallback.jfr", 0);

        assertEquals(RecordingState.Status.READY, state.status());
        assertEquals("01a0769f-a1bb-744f-962d-88b314030196", state.profileId());
        assertEquals("jeffrey-20260904-180108.jfr", state.filename());
        assertEquals(8_450_244L, state.sizeInBytes());
        assertEquals(5_539L, state.summary().durationInMillis());
        assertEquals(44_099L, state.summary().sampleCount());
        assertEquals(106, state.summary().eventTypeCount());
    }

    /** What the panel prints for that recording, as the developer would read it. */
    @Test
    public void printsThatResponseTheWayThePanelWill() {
        RecordingState.ProfileSummary summary =
                MicroscopeJson.parseState(CAPTURED, "fallback.jfr", 0).summary();

        assertEquals("8.1 MB", Formats.bytes(8_450_244L));
        assertEquals("5.5 s", Formats.duration(summary.durationInMillis()));
        assertEquals("44.1 K", Formats.count(summary.sampleCount()));
        assertEquals("106", String.valueOf(summary.eventTypeCount()));
        assertEquals("38.6%", Formats.lossRatio(summary.lossRatio()));
    }

    /**
     * Auto-analysis is not computed at import time, so a freshly analysed profile has no findings and
     * has to say so. Showing an empty findings list as "nothing wrong" would be a lie about a
     * recording that in fact dropped 38% of its samples.
     */
    @Test
    public void saysAnalysisIsNotComputedRatherThanShowingNothingWrong() {
        RecordingState.ProfileSummary summary =
                MicroscopeJson.parseState(CAPTURED, "fallback.jfr", 0).summary();

        assertFalse(summary.analysisComputed());
        assertTrue(summary.findings().isEmpty());
    }
}
