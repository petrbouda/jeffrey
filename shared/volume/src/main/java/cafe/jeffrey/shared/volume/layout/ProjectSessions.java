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

package cafe.jeffrey.shared.volume.layout;

import cafe.jeffrey.shared.common.HeartbeatConstants;
import cafe.jeffrey.shared.volume.liveness.SessionLifecycle;
import cafe.jeffrey.shared.volume.liveness.SessionLiveness;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * A project's sessions as they are at the moment of reading: each one's directory, whether it is
 * still recording, and whether it is pinned. The one place every reader of a volume — the hub, a
 * Microscope reading the storage directly — derives that from, so they cannot come to different
 * answers.
 *
 * <p>A session's lifecycle depends on the start of the next one in its instance (a newer session
 * means this one ended), so an instance's sessions are always read together.
 *
 * <p>On a file share every question asked of the volume is a round-trip, so a session costs its
 * marker, one listing of its directory, and its liveness files only when that listing shows them.
 * The files are described off the same listing ({@link ProjectTree#files(VolumeSession)}).
 */
public final class ProjectSessions {

    private static final Comparator<VolumeSession> NEWEST_FIRST =
            Comparator.comparing(VolumeSession::createdAt).reversed();

    private final ProjectTree tree;
    private final SessionLiveness liveness;

    public ProjectSessions(ProjectTree tree, SessionLiveness liveness) {
        this.tree = tree;
        this.liveness = liveness;
    }

    /** Every session of the project, newest first. */
    public List<VolumeSession> all() {
        return tree.instances().stream()
                .flatMap(instance -> of(instance).stream())
                .sorted(NEWEST_FIRST)
                .toList();
    }

    /** One instance's sessions, newest first. */
    public List<VolumeSession> of(InstanceDirectory instance) {
        List<SessionDirectory> oldestFirst = tree.sessionsOldestFirst(instance.path());
        return oldestFirst.stream()
                .map(session -> read(session, oldestFirst))
                .toList()
                .reversed();
    }

    /**
     * One session, or empty when the project holds none by that id. Only this session's liveness
     * files are read; its siblings are needed for their start times alone, only when its liveness
     * files do not settle it — a clean finish never reads them — and then only the ones that may
     * have started after it ({@link ProjectTree#nextStart(SessionDirectory)}).
     */
    public Optional<VolumeSession> session(String sessionId) {
        return tree.session(sessionId).map(session -> read(session, () -> tree.nextStart(session)));
    }

    /**
     * Every instance with its count and its newest session. Only the newest session's liveness
     * files are read; the others' markers give the count.
     */
    public List<InstanceSummary> instances() {
        return tree.instances().stream()
                .map(instance -> {
                    List<SessionDirectory> oldestFirst = tree.sessionsOldestFirst(instance.path());
                    Optional<VolumeSession> newest = oldestFirst.isEmpty()
                            ? Optional.empty()
                            : Optional.of(read(oldestFirst.getLast(), oldestFirst));
                    return new InstanceSummary(instance, oldestFirst.size(), newest);
                })
                .toList();
    }

    /** One instance with every session of it, or empty when the project holds no instance by that id. */
    public Optional<InstanceSessionList> instance(String instanceId) {
        return tree.instance(instanceId).map(instance -> {
            List<VolumeSession> sessions = of(instance);
            return new InstanceSessionList(
                    new InstanceSummary(instance, sessions.size(), sessions.stream().findFirst()), sessions);
        });
    }

    private VolumeSession read(SessionDirectory session, List<SessionDirectory> siblingsOldestFirst) {
        return read(session, () -> ProjectTree.nextStart(siblingsOldestFirst, session));
    }

    /**
     * A session off one listing of its directory: the listing says whether it is pinned and whether
     * anything ever wrote its liveness directory, so neither is asked of the volume again — the
     * liveness files are read only when that directory is there.
     */
    private VolumeSession read(SessionDirectory session, Supplier<Optional<Instant>> nextSessionStart) {
        SessionListing listing = tree.listing(session);
        // A failed listing cannot say the directory is missing, so the files are read as before
        boolean livenessWritten = listing.holdsDirectory(HeartbeatConstants.HEARTBEAT_DIR, () -> true);
        SessionLifecycle lifecycle = livenessWritten
                ? liveness.assess(session.path(), session.createdAt(), nextSessionStart)
                : liveness.assessUnwritten(session.path(), session.createdAt(), nextSessionStart);
        boolean retained = listing.holdsFile(SessionDirectory.RETAINED_FLAG, () -> tree.isRetained(session));
        return new VolumeSession(session, lifecycle, retained, listing);
    }
}
