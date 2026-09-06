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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Reading Microscope's answer. The panel draws whatever comes back here, so a field this parser gets
 * wrong is a wrong number shown to the developer with no hint that it is wrong.
 */
public class MicroscopeJsonTest {

    private static final String FILENAME = "jeffrey-20260904-180108.jfr";
    private static final long SIZE = 8_450_244L;

    @Test
    public void readsAReadyProfileWithItsFigures() {
        String body = """
                {
                  "state": "READY",
                  "recordingId": "rec-1",
                  "profileId": "profile-1",
                  "filename": "jeffrey-20260904-180108.jfr",
                  "sizeInBytes": 8450244,
                  "summary": {
                    "profileName": "jeffrey-20260904-180108",
                    "durationInMillis": 42000,
                    "sampleCount": 1240000,
                    "eventTypeCount": 18,
                    "capturedSamples": 980000,
                    "lostSamples": 0,
                    "analysisComputed": true,
                    "findings": [
                      {"rule": "gc-pauses", "severity": "WARNING", "summary": "Long GC pauses"}
                    ]
                  }
                }
                """;

        RecordingState state = MicroscopeJson.parseState(body, FILENAME, SIZE);

        assertEquals(RecordingState.Status.READY, state.status());
        assertEquals("profile-1", state.profileId());
        assertEquals(42_000L, state.summary().durationInMillis());
        assertEquals(18, state.summary().eventTypeCount());
        assertTrue(state.summary().analysisComputed());
        assertEquals(1, state.summary().findings().size());
        assertTrue(state.summary().findings().getFirst().isWarning());
    }

    @Test
    public void readsAFileMicroscopeHasNeverSeen() {
        RecordingState state = MicroscopeJson.parseState(
                "{\"state\":\"NOT_IMPORTED\",\"filename\":\"run.jfr\",\"sizeInBytes\":10}", FILENAME, SIZE);

        assertEquals(RecordingState.Status.NOT_IMPORTED, state.status());
        assertNull(state.recordingId());
        assertNull(state.summary());
    }

    /** Missing fields fall back to what the caller already knows, rather than to nulls and zeroes. */
    @Test
    public void fallsBackToTheCallersOwnFileFacts() {
        RecordingState state = MicroscopeJson.parseState("{\"state\":\"IMPORTED\"}", FILENAME, SIZE);

        assertEquals(FILENAME, state.filename());
        assertEquals(SIZE, state.sizeInBytes());
    }

    /**
     * A Microscope newer than this plugin may answer with a state it has never heard of. That has to
     * read as "cannot describe this file", not as an exception that leaves the tab blank.
     */
    @Test
    public void treatsAnUnknownStateAsUnavailable() {
        assertEquals(
                RecordingState.Status.UNAVAILABLE,
                MicroscopeJson.parseState("{\"state\":\"QUARANTINED\"}", FILENAME, SIZE).status());
        assertEquals(
                RecordingState.Status.UNAVAILABLE,
                MicroscopeJson.parseState("{}", FILENAME, SIZE).status());
    }

    /** The list the panel dims tiles with; absent means nothing is known to be missing. */
    @Test
    public void readsTheDisabledFeatureList() {
        RecordingState state = MicroscopeJson.parseState(
                "{\"state\":\"READY\",\"summary\":{\"disabledFeatures\":[\"TRACES\",\"HEAP_DUMP\"]}}",
                FILENAME, SIZE);

        assertEquals(java.util.List.of("TRACES", "HEAP_DUMP"), state.summary().disabledFeatures());
        assertTrue(MicroscopeJson.parseState("{\"state\":\"READY\",\"summary\":{}}", FILENAME, SIZE)
                .summary().disabledFeatures().isEmpty());
    }

    @Test
    public void reportsAnalysisNotComputedRatherThanNoFindings() {
        RecordingState state = MicroscopeJson.parseState(
                "{\"state\":\"READY\",\"summary\":{\"analysisComputed\":false}}", FILENAME, SIZE);

        assertFalse(state.summary().analysisComputed());
        assertTrue(state.summary().findings().isEmpty());
    }

    @Test
    public void readsTheIdsTheImportAndAnalyzeCallsAnswerWith() {
        assertEquals("rec-1", MicroscopeJson.parseId("{\"recordingId\":\"rec-1\"}", "recordingId"));
        assertEquals("profile-1", MicroscopeJson.parseId("{\"profileId\":\"profile-1\"}", "profileId"));
        assertNull(MicroscopeJson.parseId("{}", "profileId"));
    }

    /**
     * Sampler health is reported as two counts, and the ratio the panel prints is derived. A profile
     * that reports neither has to come out as "not measured" rather than as a clean zero.
     */
    @Test
    public void derivesTheLossRatioFromTheCounts() {
        RecordingState clean = MicroscopeJson.parseState(
                "{\"state\":\"READY\",\"summary\":{\"capturedSamples\":100,\"lostSamples\":0}}",
                FILENAME, SIZE);
        RecordingState lossy = MicroscopeJson.parseState(
                "{\"state\":\"READY\",\"summary\":{\"capturedSamples\":75,\"lostSamples\":25}}",
                FILENAME, SIZE);
        RecordingState silent = MicroscopeJson.parseState(
                "{\"state\":\"READY\",\"summary\":{}}", FILENAME, SIZE);

        assertEquals(0.0, clean.summary().lossRatio(), 0.0001);
        assertEquals(0.25, lossy.summary().lossRatio(), 0.0001);
        assertEquals(-1.0, silent.summary().lossRatio(), 0.0001);
    }
}
