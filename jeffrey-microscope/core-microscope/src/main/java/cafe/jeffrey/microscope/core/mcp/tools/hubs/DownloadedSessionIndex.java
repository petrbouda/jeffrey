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

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Which hub sessions are already in this Jeffrey, so a listing can say so and a download can decline
 * to repeat itself.
 * <p>
 * This is what keeps a repeated "what recorded in the last hour" cheap. Every recording downloaded
 * from a hub carries {@code origin.*} tags naming where it came from, and the last of them,
 * {@code origin.recordingId}, is the upstream <em>session</em> id. Reconstructing the whole
 * {@link HubSessionRef} from those four tags is what makes the hub's non-unique session id harmless:
 * the same session id under a different project is a different key, and does not match.
 * <p>
 * Built forward and in bulk — one listing plus one tag query — because nothing looks recordings up
 * by tag value, and adding such a query for this would be a schema change to save a pass over a list
 * that is small by construction.
 * <p>
 * Build it once per listing call and throw it away. It must never be cached across calls: a download
 * that happens between two listings has to show up in the second one.
 */
public final class DownloadedSessionIndex {

    private static final String TAG_HUB_ID = "origin.hubId";
    private static final String TAG_WORKSPACE_ID = "origin.workspaceId";
    private static final String TAG_PROJECT_ID = "origin.projectId";
    private static final String TAG_SESSION_ID = "origin.recordingId";

    private final Map<HubSessionRef, LocalCopy> byRef;

    private DownloadedSessionIndex(Map<HubSessionRef, LocalCopy> byRef) {
        this.byRef = byRef;
    }

    /**
     * The local copy of a hub session: always a recording, and a profile once it has been analysed.
     */
    public record LocalCopy(String recordingId, String profileId) {

        public boolean analysed() {
            return profileId != null;
        }
    }

    public static DownloadedSessionIndex build(RecordingsCoreManager recordings) {
        List<Recording> local = recordings.listRecordings();
        if (local.isEmpty()) {
            return new DownloadedSessionIndex(Map.of());
        }

        Map<String, List<RecordingTag>> tags =
                recordings.tagsForRecordings(local.stream().map(Recording::id).toList());

        Map<HubSessionRef, Recording> newest = new HashMap<>();
        for (Recording recording : local) {
            originRef(tags.get(recording.id()))
                    .ifPresent(ref -> newest.merge(ref, recording, DownloadedSessionIndex::preferred));
        }

        Map<HubSessionRef, LocalCopy> byRef = new HashMap<>();
        newest.forEach((ref, recording) -> byRef.put(ref, new LocalCopy(
                recording.id(),
                recording.hasProfile() ? recording.profileId() : null)));
        return new DownloadedSessionIndex(byRef);
    }

    public Optional<LocalCopy> find(HubSessionRef ref) {
        return Optional.ofNullable(byRef.get(ref));
    }

    /**
     * The coordinate a recording was downloaded from, or empty for one that came from anywhere else
     * — an upload, or a path import. A recording missing any of the four tags is not a hub download
     * we can point back at, so it is skipped rather than half-matched.
     */
    private static Optional<HubSessionRef> originRef(List<RecordingTag> tags) {
        if (tags == null || tags.isEmpty()) {
            return Optional.empty();
        }

        Map<String, String> byKey = new HashMap<>();
        for (RecordingTag tag : tags) {
            byKey.put(tag.key(), tag.value());
        }

        String hubId = byKey.get(TAG_HUB_ID);
        String workspaceId = byKey.get(TAG_WORKSPACE_ID);
        String projectId = byKey.get(TAG_PROJECT_ID);
        String sessionId = byKey.get(TAG_SESSION_ID);
        if (hubId == null || workspaceId == null || projectId == null || sessionId == null) {
            return Optional.empty();
        }

        try {
            return Optional.of(new HubSessionRef(hubId, workspaceId, projectId, sessionId));
        } catch (IllegalArgumentException e) {
            // A tag written by an older version, or by hand. Not worth failing a listing over.
            return Optional.empty();
        }
    }

    /**
     * Which of two downloads of the same session to point the reader at: the analysed one, because
     * it is the one they can use immediately, and otherwise the newer.
     */
    private static Recording preferred(Recording left, Recording right) {
        if (left.hasProfile() != right.hasProfile()) {
            return left.hasProfile() ? left : right;
        }
        return Comparator.comparing(
                        Recording::createdAt,
                        Comparator.nullsFirst(Comparator.naturalOrder()))
                .compare(left, right) >= 0 ? left : right;
    }
}
