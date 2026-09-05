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

package cafe.jeffrey.microscope.core.mcp.tools.hubs;

import cafe.jeffrey.microscope.persistence.api.RecordingTag;
import cafe.jeffrey.recordings.core.manager.RecordingsCoreManager;
import cafe.jeffrey.shared.common.model.Recording;
import cafe.jeffrey.shared.common.model.RecordingEventSource;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
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
    private static final HubSessionRef REF =
            new HubSessionRef("cfg-production", "ws-1", "proj-1", "session-1");

    private final RecordingsCoreManager recordings = mock(RecordingsCoreManager.class);

    private static Recording recording(String id, String profileId, Instant createdAt) {
        return new Recording(
                id, id, null, null, RecordingEventSource.JDK, createdAt, CREATED_AT, CREATED_AT,
                profileId != null, profileId, profileId, List.of());
    }

    private static List<RecordingTag> originTags(HubSessionRef ref) {
        return List.of(
                new RecordingTag("origin.hubId", ref.hubId()),
                new RecordingTag("origin.workspaceId", ref.workspaceId()),
                new RecordingTag("origin.projectId", ref.projectId()),
                new RecordingTag("origin.recordingId", ref.sessionId()));
    }

    private DownloadedSessionIndex indexOf(List<Recording> local, Map<String, List<RecordingTag>> tags) {
        when(recordings.listRecordings()).thenReturn(local);
        if (!local.isEmpty()) {
            when(recordings.tagsForRecordings(any())).thenReturn(tags);
        }
        return DownloadedSessionIndex.build(recordings);
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
            DownloadedSessionIndex index = indexOf(
                    List.of(recording("rec-1", "profile-1", CREATED_AT)),
                    Map.of("rec-1", originTags(REF)));

            DownloadedSessionIndex.LocalCopy copy = index.find(REF).orElseThrow();

            assertTrue(copy.analysed());
            assertEquals("profile-1", copy.profileId());
        }

        @Test
        void doesNotMatchTheSameSessionIdUnderADifferentProject() {
            // The hub keys a session by (repository, session), so a session id alone is not unique.
            // Carrying the whole coordinate is what stops a look-alike matching.
            DownloadedSessionIndex index = indexOf(
                    List.of(recording("rec-1", null, CREATED_AT)),
                    Map.of("rec-1", originTags(REF)));

            HubSessionRef elsewhere =
                    new HubSessionRef(REF.hubId(), REF.workspaceId(), "other-project", REF.sessionId());

            assertTrue(index.find(elsewhere).isEmpty());
        }

        @Test
        void doesNotMatchTheSameSessionOnADifferentHub() {
            DownloadedSessionIndex index = indexOf(
                    List.of(recording("rec-1", null, CREATED_AT)),
                    Map.of("rec-1", originTags(REF)));

            HubSessionRef otherHub =
                    new HubSessionRef("cfg-staging", REF.workspaceId(), REF.projectId(), REF.sessionId());

            assertTrue(index.find(otherHub).isEmpty());
        }

        @Test
        void ignoresARecordingThatDidNotComeFromAHub() {
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
                            new RecordingTag("origin.hubId", REF.hubId()),
                            new RecordingTag("origin.recordingId", REF.sessionId()))));

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

            DownloadedSessionIndex index = DownloadedSessionIndex.build(recordings);

            assertTrue(index.find(REF).isEmpty());
            verify(recordings, times(0)).tagsForRecordings(any());
        }

        @Test
        void prefersTheAnalysedCopyWhenASessionWasDownloadedTwice() {
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
}
