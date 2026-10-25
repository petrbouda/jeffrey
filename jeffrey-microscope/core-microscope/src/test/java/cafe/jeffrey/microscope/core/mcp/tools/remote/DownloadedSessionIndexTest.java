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

package cafe.jeffrey.microscope.core.mcp.tools.remote;

import cafe.jeffrey.microscope.persistence.api.RecordingTag;
import cafe.jeffrey.microscope.core.manager.recordings.RecordingsManager;
import cafe.jeffrey.recordings.core.OriginContext;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.microscope.model.ProfileInfo;
import cafe.jeffrey.storage.recording.api.file.Recording;
import cafe.jeffrey.microscope.model.RecordingEventSource;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DownloadedSessionIndexTest {

    private static final Instant CREATED_AT = Instant.parse("2026-03-01T12:00:00Z");
    private static final String ADDRESS = "hub.example.com:443";
    private static final RemoteSessionRef REF =
            new RemoteSessionRef("ws-1", "proj-1", "session-1");

    private final RecordingsManager recordings = mock(RecordingsManager.class);

    private static Recording recording(String id, String profileId, Instant createdAt) {
        return new Recording(
                id, id, null, RecordingEventSource.JDK, createdAt, CREATED_AT, CREATED_AT,
                profileId != null, profileId, profileId, List.of());
    }

    private static List<RecordingTag> originTags(RemoteSessionRef ref) {
        return List.of(
                new RecordingTag(OriginContext.TAG_REMOTE_ADDRESS, ADDRESS),
                new RecordingTag("origin.workspaceId", ref.workspaceId()),
                new RecordingTag("origin.projectId", ref.projectId()),
                new RecordingTag("origin.recordingId", ref.sessionId()));
    }

    /** The tags of a whole copy taken once the session had finished. */
    private static List<RecordingTag> completeTags(RemoteSessionRef ref) {
        List<RecordingTag> tags = new ArrayList<>(originTags(ref));
        tags.add(new RecordingTag(OriginContext.TAG_COMPLETE, "true"));
        return tags;
    }

    private DownloadedSessionIndex indexOf(List<Recording> local, Map<String, List<RecordingTag>> tags) {
        when(recordings.listRecordings()).thenReturn(local);
        if (!local.isEmpty()) {
            when(recordings.tagsForRecordings(any())).thenReturn(tags);
        }
        return DownloadedSessionIndex.build(recordings, Optional.of(ADDRESS));
    }

    private void profileReady(String id, boolean enabled) {
        ProfileManager manager = mock(ProfileManager.class);
        ProfileInfo info = mock(ProfileInfo.class);
        when(recordings.profile(id)).thenReturn(Optional.of(manager));
        when(manager.info()).thenReturn(info);
        when(info.enabled()).thenReturn(enabled);
    }

    @Test
    void disabledProfileIsOnlyADownloadedRecording() {
        profileReady("profile-1", false);
        DownloadedSessionIndex index = indexOf(
                List.of(recording("rec-1", "profile-1", CREATED_AT)),
                Map.of("rec-1", originTags(REF)));

        assertFalse(index.find(REF).orElseThrow().analysed());
    }

    @Test
    void missingProfileIsOnlyADownloadedRecording() {
        DownloadedSessionIndex index = indexOf(
                List.of(recording("rec-1", "profile-1", CREATED_AT)),
                Map.of("rec-1", originTags(REF)));

        assertFalse(index.find(REF).orElseThrow().analysed());
    }

    @Test
    void readyCopyWinsOverNewerDisabledProfile() {
        profileReady("profile-ready", true);
        profileReady("profile-building", false);
        DownloadedSessionIndex index = indexOf(
                List.of(recording("rec-ready", "profile-ready", CREATED_AT),
                        recording("rec-building", "profile-building", CREATED_AT.plusSeconds(60))),
                Map.of("rec-ready", originTags(REF), "rec-building", originTags(REF)));

        assertEquals("rec-ready", index.find(REF).orElseThrow().recordingId());
    }

    /**
     * Every item of a downloaded bundle carries the session's origin tags. Only the session's own
     * recording is its local copy: a heap dump item with a profile ready is still not the session.
     */
    @Test
    void aHeapItemWithAReadyProfileIsNeverTheSessionsLocalCopy() {
        profileReady("profile-heap", true);
        Recording heapItem = new Recording(
                "rec-heap", "app.hprof", null, RecordingEventSource.HEAP_DUMP, CREATED_AT.plusSeconds(60),
                null, null, true, "profile-heap", "app.hprof", List.of(), "bundle-1");
        Recording session = new Recording(
                "rec-jfr", "session", null, RecordingEventSource.JDK, CREATED_AT, CREATED_AT, CREATED_AT,
                false, null, null, List.of(), "bundle-1");
        DownloadedSessionIndex index = indexOf(
                List.of(heapItem, session),
                Map.of("rec-heap", originTags(REF), "rec-jfr", originTags(REF)));

        DownloadedSessionIndex.LocalCopy copy = index.find(REF).orElseThrow();

        assertEquals("rec-jfr", copy.recordingId());
        assertEquals("bundle-1", copy.bundleId());
        assertFalse(copy.analysed());
    }

    @Nested
    class Matching {

        @Test
        void findsARecordingByItsFullOriginCoordinate() {
            DownloadedSessionIndex index = indexOf(
                    List.of(recording("rec-1", null, CREATED_AT)),
                    Map.of("rec-1", originTags(REF)));

            Optional<DownloadedSessionIndex.LocalCopy> found = index.find(REF);

            assertTrue(found.isPresent());
            assertEquals("rec-1", found.get().recordingId());
            assertFalse(found.get().analysed());
        }

        @Test
        void reportsTheProfileWhenTheDownloadHasBeenAnalysed() {
            profileReady("profile-1", true);
            DownloadedSessionIndex index = indexOf(
                    List.of(recording("rec-1", "profile-1", CREATED_AT)),
                    Map.of("rec-1", originTags(REF)));

            DownloadedSessionIndex.LocalCopy copy = index.find(REF).orElseThrow();

            assertTrue(copy.analysed());
            assertEquals("profile-1", copy.profileId());
        }

        @Test
        void doesNotMatchTheSameSessionIdUnderADifferentProject() {
            // A remote keys a session by (repository, session), so a session id alone is not unique.
            // Carrying the whole coordinate is what stops a look-alike matching.
            DownloadedSessionIndex index = indexOf(
                    List.of(recording("rec-1", null, CREATED_AT)),
                    Map.of("rec-1", originTags(REF)));

            RemoteSessionRef elsewhere =
                    new RemoteSessionRef(REF.workspaceId(), "other-project", REF.sessionId());

            assertTrue(index.find(elsewhere).isEmpty());
        }

        /** A copy taken from a remote connected earlier, at another address, is not this remote's. */
        @Test
        void doesNotMatchACopyFromAnotherRemote() {
            when(recordings.listRecordings()).thenReturn(List.of(recording("rec-1", null, CREATED_AT)));
            when(recordings.tagsForRecordings(any())).thenReturn(Map.of("rec-1", originTags(REF)));

            DownloadedSessionIndex index =
                    DownloadedSessionIndex.build(recordings, Optional.of("other.example.com:443"));

            assertTrue(index.find(REF).isEmpty());
        }

        @Test
        void matchesNothingWithoutARemote() {
            DownloadedSessionIndex index = DownloadedSessionIndex.build(recordings, Optional.empty());

            assertTrue(index.find(REF).isEmpty());
            verify(recordings, times(0)).listRecordings();
        }

        @Test
        void ignoresARecordingThatDidNotComeFromTheRemote() {
            // An upload or a path import carries no origin tags at all.
            DownloadedSessionIndex index = indexOf(
                    List.of(recording("rec-1", "profile-1", CREATED_AT)),
                    Map.of("rec-1", List.of()));

            assertTrue(index.find(REF).isEmpty());
        }

        @Test
        void ignoresARecordingWithOnlySomeOfTheOriginTags() {
            DownloadedSessionIndex index = indexOf(
                    List.of(recording("rec-1", null, CREATED_AT)),
                    Map.of("rec-1", List.of(
                            new RecordingTag(OriginContext.TAG_REMOTE_ADDRESS, ADDRESS),
                            new RecordingTag("origin.recordingId", REF.sessionId()))));

            assertTrue(index.find(REF).isEmpty());
        }

        /**
         * A window of the session is tagged with every origin coordinate the whole session carries,
         * plus the window. Answering "already downloaded" from it would hand a reader an hour when
         * they asked for the day.
         */
        @Test
        void ignoresAWindowOfTheSession() {
            List<RecordingTag> tags = new ArrayList<>(originTags(REF));
            tags.add(new RecordingTag(OriginContext.TAG_WINDOW, "1000-2000"));
            DownloadedSessionIndex index = indexOf(
                    List.of(recording("rec-1", "profile-1", CREATED_AT)),
                    Map.of("rec-1", tags));

            assertTrue(index.find(REF).isEmpty());
        }
    }

    @Nested
    class Bulk {

        @Test
        void readsTagsInOneQuery() {
            indexOf(
                    List.of(recording("rec-1", null, CREATED_AT), recording("rec-2", null, CREATED_AT)),
                    Map.of("rec-1", originTags(REF)));

            verify(recordings, times(1)).tagsForRecordings(any());
        }

        @Test
        void asksForNoTagsWhenTheStoreIsEmpty() {
            when(recordings.listRecordings()).thenReturn(List.of());

            DownloadedSessionIndex index = DownloadedSessionIndex.build(recordings, Optional.of(ADDRESS));

            assertTrue(index.find(REF).isEmpty());
            verify(recordings, times(0)).tagsForRecordings(any());
        }

        @Test
        void prefersTheAnalysedCopyWhenASessionWasDownloadedTwice() {
            profileReady("profile-1", true);
            DownloadedSessionIndex index = indexOf(
                    List.of(
                            recording("rec-new", null, CREATED_AT.plusSeconds(60)),
                            recording("rec-analysed", "profile-1", CREATED_AT)),
                    Map.of("rec-new", originTags(REF), "rec-analysed", originTags(REF)));

            DownloadedSessionIndex.LocalCopy copy = index.find(REF).orElseThrow();

            assertEquals("rec-analysed", copy.recordingId());
        }

        @Test
        void fallsBackToTheNewestWhenNeitherCopyIsAnalysed() {
            DownloadedSessionIndex index = indexOf(
                    List.of(
                            recording("rec-old", null, CREATED_AT),
                            recording("rec-new", null, CREATED_AT.plusSeconds(60))),
                    Map.of("rec-old", originTags(REF), "rec-new", originTags(REF)));

            assertEquals("rec-new", index.find(REF).orElseThrow().recordingId());
        }
    }

    /**
     * A copy taken while the session was still recording stops where the session stood then: it is
     * here, but only a copy of the finished session stands in for it.
     */
    @Nested
    class Completeness {

        @Test
        void aCopyOfAFinishedSessionIsComplete() {
            DownloadedSessionIndex index = indexOf(
                    List.of(recording("rec-1", null, CREATED_AT)),
                    Map.of("rec-1", completeTags(REF)));

            assertTrue(index.find(REF).orElseThrow().complete());
        }

        @Test
        void aCopyOfARunningSessionIsHereButNotComplete() {
            DownloadedSessionIndex index = indexOf(
                    List.of(recording("rec-1", null, CREATED_AT)),
                    Map.of("rec-1", originTags(REF)));

            assertFalse(index.find(REF).orElseThrow().complete());
        }

        /**
         * Preferring the analysed partial copy would have every later download of the session
         * fetch it again, since the copy it is answered with is never complete.
         */
        @Test
        void theCompleteCopyWinsOverAnAnalysedPartialOne() {
            profileReady("profile-1", true);
            DownloadedSessionIndex index = indexOf(
                    List.of(
                            recording("rec-partial", "profile-1", CREATED_AT),
                            recording("rec-complete", null, CREATED_AT.plusSeconds(3600))),
                    Map.of("rec-partial", originTags(REF), "rec-complete", completeTags(REF)));

            DownloadedSessionIndex.LocalCopy copy = index.find(REF).orElseThrow();

            assertEquals("rec-complete", copy.recordingId());
            assertTrue(copy.complete());
        }
    }
}
