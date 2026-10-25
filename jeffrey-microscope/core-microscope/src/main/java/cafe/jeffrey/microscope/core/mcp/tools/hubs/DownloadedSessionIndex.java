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

package cafe.jeffrey.microscope.core.mcp.tools.hubs;

import cafe.jeffrey.microscope.persistence.api.RecordingTag;
import cafe.jeffrey.microscope.core.manager.recordings.RecordingsManager;
import cafe.jeffrey.recordings.core.OriginContext;
import cafe.jeffrey.microscope.model.ProfileKind;
import cafe.jeffrey.storage.recording.api.file.Recording;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
 * A download is a bundle: the session's recording item, plus a recording for each heap dump it brought,
 * every one carrying the same tags. Only the recording item is the session's local copy — a heap dump
 * analysed on its own must never be reported as the session's analysis.
 * <p>
 * A copy of a session that was still recording holds the chunks it had at the time. It is listed as
 * here, but only a {@link LocalCopy#complete() complete} one — taken once the session had finished —
 * stands in for the session, so a download is declined only for that one.
 * <p>
 * Built forward and in bulk — one listing plus one tag query — because nothing looks recordings up
 * by tag value, and adding such a query for this would be a schema change to save a pass over a list
 * that is small by construction.
 * <p>
 * Build it once per listing call and throw it away. It must never be cached across calls: a download
 * that happens between two listings has to show up in the second one.
 */
public final class DownloadedSessionIndex {


    /** The kinds a session's recording item is: what a download is analysed as, never a heap dump. */
    private static final Set<ProfileKind> SESSION_KINDS = Set.of(ProfileKind.JFR, ProfileKind.STACK_SAMPLES);

    private final Map<HubSessionRef, LocalCopy> byRef;

    private DownloadedSessionIndex(Map<HubSessionRef, LocalCopy> byRef) {
        this.byRef = byRef;
    }

    /**
     * The local copy of a hub session: always a recording, and a profile once it has been analysed.
     *
     * @param bundleId the bundle the recording came in, holding the session's other items; {@code null}
     *                 for a copy made before downloads were bundled
     * @param complete whether the copy was taken once the session had finished, and so holds all of it
     */
    public record LocalCopy(String bundleId, String recordingId, String profileId, boolean complete) {

        public boolean analysed() {
            return profileId != null;
        }
    }

    public static DownloadedSessionIndex build(RecordingsManager recordings) {
        List<Recording> local = recordings.listRecordings().stream()
                .filter(recording -> SESSION_KINDS.contains(recording.eventSource().kind()))
                .toList();
        if (local.isEmpty()) {
            return new DownloadedSessionIndex(Map.of());
        }

        Map<String, List<RecordingTag>> tags =
                recordings.tagsForRecordings(local.stream().map(Recording::id).toList());

        Map<HubSessionRef, Recording> newest = new HashMap<>();
        Map<String, Boolean> readyProfiles = new HashMap<>();
        for (Recording recording : local) {
            originRef(tags.get(recording.id())).ifPresent(ref -> {
                if (recording.hasProfile()) {
                    readyProfiles.computeIfAbsent(recording.profileId(), profileId -> recordings.profile(profileId)
                            .map(profile -> profile.info().enabled())
                            .orElse(false));
                }
                newest.merge(ref, recording, (left, right) -> preferred(left, right, readyProfiles, tags));
            });
        }

        Map<HubSessionRef, LocalCopy> byRef = new HashMap<>();
        newest.forEach((ref, recording) -> byRef.put(ref, new LocalCopy(
                recording.bundleId(),
                recording.id(),
                Boolean.TRUE.equals(readyProfiles.get(recording.profileId())) ? recording.profileId() : null,
                isComplete(tags.get(recording.id())))));
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

        // A window of a session is a recording of its own, not the session's local copy: telling a
        // reader it is "already here" would hand them an hour of a day they asked for whole.
        if (byKey.containsKey(OriginContext.TAG_WINDOW)) {
            return Optional.empty();
        }

        String hubId = byKey.get(OriginContext.TAG_HUB_ID);
        String workspaceId = byKey.get(OriginContext.TAG_WORKSPACE_ID);
        String projectId = byKey.get(OriginContext.TAG_PROJECT_ID);
        String sessionId = byKey.get(OriginContext.TAG_RECORDING_ID);
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

    /** Whether a copy carries the tag a whole download of a finished session is given. */
    private static boolean isComplete(List<RecordingTag> tags) {
        if (tags == null) {
            return false;
        }
        return tags.stream().anyMatch(tag -> OriginContext.TAG_COMPLETE.equals(tag.key())
                && Boolean.parseBoolean(tag.value()));
    }

    /**
     * Which of two downloads of the same session to point the reader at: the complete one, because
     * only it stands in for the session — preferring a partial copy would have every download of
     * the session fetch it again — then the analysed one, because it is the one they can use
     * immediately, and otherwise the newer.
     */
    private static Recording preferred(
            Recording left, Recording right, Map<String, Boolean> readyProfiles, Map<String, List<RecordingTag>> tags) {

        boolean leftComplete = isComplete(tags.get(left.id()));
        boolean rightComplete = isComplete(tags.get(right.id()));
        if (leftComplete != rightComplete) {
            return leftComplete ? left : right;
        }
        boolean leftReady = Boolean.TRUE.equals(readyProfiles.get(left.profileId()));
        boolean rightReady = Boolean.TRUE.equals(readyProfiles.get(right.profileId()));
        if (leftReady != rightReady) {
            return leftReady ? left : right;
        }
        return Comparator.comparing(
                        Recording::createdAt,
                        Comparator.nullsFirst(Comparator.naturalOrder()))
                .compare(left, right) >= 0 ? left : right;
    }
}
