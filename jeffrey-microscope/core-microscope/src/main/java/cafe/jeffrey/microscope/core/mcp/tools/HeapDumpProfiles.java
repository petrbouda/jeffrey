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

package cafe.jeffrey.microscope.core.mcp.tools;

import cafe.jeffrey.microscope.model.ProfileKind;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.manager.heapdump.HeapDumpManager;

/**
 * The refusal every heap_ tool gives a profile that is not a heap dump. A JFR recording never holds
 * one: a heap dump downloaded beside it is another item of the same bundle, with a profile of its own.
 */
public final class HeapDumpProfiles {

    private static final String NOT_A_HEAP_DUMP =
            "Profile %s is not a heap dump, and the heap_ tools read only heap dump profiles. A heap dump "
                    + "downloaded with a recording is another item of its bundle: remote_download lists the "
                    + "items with their recordingIds, and recordings_analyzeRecording builds its profile. "
                    + "For a JFR recording use the jfr_, flamegraph_ and traces_ tools instead.";

    private static final String DUMP_MISSING =
            "Heap dump profile %s has lost its copy of the dump. recordings_analyzeRecording on its "
                    + "recording builds the profile again from the stored dump.";

    private HeapDumpProfiles() {
    }

    /**
     * The profile's heap dump.
     *
     * @throws IllegalArgumentException when the profile is not a heap dump, or its dump is gone
     */
    public static HeapDumpManager require(ProfileManager profileManager) {
        String profileId = profileManager.info().id();
        if (profileManager.info().eventSource().kind() != ProfileKind.HEAP_DUMP) {
            throw new IllegalArgumentException(NOT_A_HEAP_DUMP.formatted(profileId));
        }
        HeapDumpManager heapDumpManager = profileManager.heapDumpManager();
        if (!heapDumpManager.heapDumpExists()) {
            throw new IllegalArgumentException(DUMP_MISSING.formatted(profileId));
        }
        return heapDumpManager;
    }

    /** Whether the profile is a heap dump with its dump in place, for a caller answering with a status. */
    static boolean holdsHeapDump(ProfileManager profileManager) {
        return profileManager.info().eventSource().kind() == ProfileKind.HEAP_DUMP
                && profileManager.heapDumpManager().heapDumpExists();
    }
}
