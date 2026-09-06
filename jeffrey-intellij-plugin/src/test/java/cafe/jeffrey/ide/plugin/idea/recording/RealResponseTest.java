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
 * The contract, pinned to responses Microscope actually produced.
 *
 * <p>Captured from a live run: a JFR and a heap dump imported through
 * {@code POST /recordings/from-path}, analysed, and read back from
 * {@code GET /recordings/by-path}. Every other test here builds its own JSON, which proves the parser
 * reads what this plugin writes; this one proves it reads what the other side writes.
 *
 * <p>Re-capture when the endpoint changes shape. A green suite against invented JSON and a panel
 * showing blanks against the real thing is the failure this exists to prevent.
 */
public class RealResponseTest {

    private static final String CAPTURED_RECORDING = """
{
              "state": "READY",
              "recordingId": "01a07712-495b-73db-8665-dc3e1f98f6ad",
              "profileId": "01a07712-49a3-74a3-814f-4ffebef9289e",
              "filename": "jeffrey-20260904-180108.jfr",
              "sizeInBytes": 8450244,
              "summary": {
                "kind": "RECORDING",
                "profileName": "jeffrey-20260904-180108.jfr",
                "profilingStartedAt": 1788537668917,
                "profilingFinishedAt": 1788537674457,
                "recording": {
                  "durationInMillis": 5539,
                  "sampleCount": 44099,
                  "eventTypeCount": 106,
                  "capturedSamples": 353,
                  "lostSamples": 222
                },
                "heap": null,
                "analysisComputed": false,
                "findings": [],
                "disabledFeatures": [
                  "ASYNC_PROFILER_SPANS",
                  "CONTAINER_DASHBOARD",
                  "GRPC_CLIENT_DASHBOARD",
                  "GRPC_SERVER_DASHBOARD",
                  "HEAP_DUMP",
                  "HTTP_CLIENT_DASHBOARD",
                  "HTTP_SERVER_DASHBOARD",
                  "JDBC_STATEMENTS_DASHBOARD",
                  "METHOD_TRACING_DASHBOARD",
                  "PERF_COUNTERS_DASHBOARD",
                  "TRACES"
                ]
              }
            }
            """;

    private static final String CAPTURED_HEAP_DUMP = """
{
              "state": "READY",
              "recordingId": "01a07712-550b-7a3b-8405-ea6599f371bf",
              "profileId": "01a07712-5552-7f0d-a592-8fd8bd34279f",
              "filename": "microscope.hprof",
              "sizeInBytes": 65267674,
              "summary": {
                "kind": "HEAP_DUMP",
                "profileName": "microscope.hprof",
                "profilingStartedAt": 1788704085330,
                "profilingFinishedAt": 1788704085330,
                "recording": null,
                "heap": {
                  "totalBytes": 34536952,
                  "totalInstances": 737553,
                  "classCount": 15474,
                  "gcRootCount": 4700,
                  "cacheReady": true
                },
                "analysisComputed": false,
                "findings": [],
                "disabledFeatures": [
                  "ASYNC_PROFILER_SPANS",
                  "CONTAINER_DASHBOARD",
                  "GRPC_CLIENT_DASHBOARD",
                  "GRPC_SERVER_DASHBOARD",
                  "HTTP_CLIENT_DASHBOARD",
                  "HTTP_SERVER_DASHBOARD",
                  "JDBC_POOL_DASHBOARD",
                  "JDBC_STATEMENTS_DASHBOARD",
                  "METHOD_TRACING_DASHBOARD",
                  "PERF_COUNTERS_DASHBOARD",
                  "TRACES"
                ]
              }
            }
            """;

    @Test
    public void readsALiveRecordingResponse() {
        RecordingState state = MicroscopeJson.parseState(CAPTURED_RECORDING, "fallback.jfr", 0);
        RecordingState.ProfileSummary summary = state.summary();

        assertEquals(RecordingState.Status.READY, state.status());
        assertEquals(RecordingState.Kind.RECORDING, summary.kind());
        assertNull("a recording carries no heap figures", summary.heap());
        assertEquals(5_539L, summary.recording().durationInMillis());
        assertEquals(44_099L, summary.recording().sampleCount());
        assertEquals(106, summary.recording().eventTypeCount());
    }

    /** What the panel prints for that recording, as the developer would read it. */
    @Test
    public void printsTheRecordingTheWayThePanelWill() {
        var figures = MicroscopeJson.parseState(CAPTURED_RECORDING, "f.jfr", 0).summary().recording();

        assertEquals("8.1 MB", Formats.bytes(8_450_244L));
        assertEquals("5.5 s", Formats.duration(figures.durationInMillis()));
        assertEquals("44.1 K", Formats.count(figures.sampleCount()));
        assertEquals("38.6%", Formats.lossRatio(figures.lossRatio()));
    }

    @Test
    public void readsALiveHeapDumpResponse() {
        RecordingState state = MicroscopeJson.parseState(CAPTURED_HEAP_DUMP, "fallback.hprof", 0);
        RecordingState.ProfileSummary summary = state.summary();

        assertEquals(RecordingState.Kind.HEAP_DUMP, summary.kind());
        assertTrue(summary.isHeapDump());
        assertNull("a heap dump has no recording window", summary.recording());
        assertTrue(summary.heap().cacheReady());
        assertEquals(34_536_952L, summary.heap().totalBytes());
        assertEquals(737_553L, summary.heap().totalInstances());
        assertEquals(15_474, summary.heap().classCount());
        assertEquals(4_700, summary.heap().gcRootCount());
    }

    @Test
    public void printsTheHeapDumpTheWayThePanelWill() {
        var heap = MicroscopeJson.parseState(CAPTURED_HEAP_DUMP, "f.hprof", 0).summary().heap();

        assertEquals("32.9 MB", Formats.bytes(heap.totalBytes()));
        assertEquals("737.6 K", Formats.count(heap.totalInstances()));
        assertEquals("15.5 K", Formats.count(heap.classCount()));
    }

    /** And it gets the heap tile grid, not the recording one. */
    @Test
    public void offersHeapViewsForAHeapDump() {
        var summary = MicroscopeJson.parseState(CAPTURED_HEAP_DUMP, "f.hprof", 0).summary();

        assertEquals(ProfileView.HEAP, summary.views());
        assertEquals(
                ProfileView.RECORDING,
                MicroscopeJson.parseState(CAPTURED_RECORDING, "f.jfr", 0).summary().views());
    }

    /**
     * Auto-analysis is not computed at import time, so a freshly analysed profile has to say so.
     * Showing an empty findings list as "nothing wrong" would be a lie about a recording that in fact
     * dropped 38% of its samples.
     */
    @Test
    public void saysAnalysisIsNotComputedRatherThanShowingNothingWrong() {
        var summary = MicroscopeJson.parseState(CAPTURED_RECORDING, "f.jfr", 0).summary();

        assertFalse(summary.analysisComputed());
        assertTrue(summary.findings().isEmpty());
    }
}
