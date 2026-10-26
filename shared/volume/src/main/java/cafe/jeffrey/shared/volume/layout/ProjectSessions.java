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
import cafe.jeffrey.shared.common.JeffreyLayout;
import cafe.jeffrey.shared.volume.ReadFanOut;
import cafe.jeffrey.shared.volume.VolumeEntry;
import cafe.jeffrey.shared.volume.liveness.SessionLifecycle;
import cafe.jeffrey.shared.volume.liveness.SessionLiveness;

import java.time.Instant;
import java.util.ArrayList;
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

    /** A page's sessions newest first by their markers, a tie broken by name, as an instance lists them. */
    private static final Comparator<SessionDirectory> NEWEST_SESSION_FIRST =
            Comparator.comparing(SessionDirectory::createdAt).thenComparing(SessionDirectory::id).reversed();

    /** Session directories newest first by when the share says each was created, a tie broken by name. */
    private static final Comparator<VolumeEntry> NEWEST_LISTED_FIRST = Comparator
            .comparing((VolumeEntry entry) -> entry.createdAt().orElseThrow())
            .thenComparing(VolumeEntry::name)
            .reversed();

    private final ProjectTree tree;
    private final SessionLiveness liveness;
    private final ReadFanOut fanOut;

    /** Reads one thing after another: the hub's, on its own mount. */
    public ProjectSessions(ProjectTree tree, SessionLiveness liveness) {
        this(tree, liveness, ReadFanOut.sequential());
    }

    /** @param fanOut how the independent reads of one call are run — several at a time on a share */
    public ProjectSessions(ProjectTree tree, SessionLiveness liveness, ReadFanOut fanOut) {
        this.tree = tree;
        this.liveness = liveness;
        this.fanOut = fanOut;
    }

    /** Every session of the project, newest first: each instance's markers, then every session, fanned out. */
    public List<VolumeSession> all() {
        List<List<SessionDirectory>> byInstance =
                fanOut.map(tree.instances(fanOut), instance -> tree.sessionsOldestFirst(instance.path()));
        record Placed(SessionDirectory session, List<SessionDirectory> siblingsOldestFirst) {
        }
        List<Placed> placed = byInstance.stream()
                .flatMap(oldestFirst -> oldestFirst.stream().map(session -> new Placed(session, oldestFirst)))
                .toList();
        return fanOut.map(placed, one -> read(one.session(), one.siblingsOldestFirst())).stream()
                .sorted(NEWEST_FIRST)
                .toList();
    }

    /** One instance's sessions, newest first. */
    public List<VolumeSession> of(InstanceDirectory instance) {
        List<SessionDirectory> oldestFirst = tree.sessionsOldestFirst(instance.path(), fanOut);
        return fanOut.map(oldestFirst, session -> read(session, oldestFirst)).reversed();
    }

    /**
     * One session, or empty when the project holds none by that id. Only this session's liveness
     * files are read; its siblings are needed for their start times alone, only when its liveness
     * files do not settle it — a clean finish never reads them — and then only the ones that may
     * have started after it ({@link ProjectTree#nextStart(SessionDirectory)}).
     */
    public Optional<VolumeSession> session(String sessionId) {
        return tree.session(sessionId)
                .map(session -> read(session, () -> tree.nextStart(session)))
                .filter(ProjectSessions::stillListed);
    }

    /**
     * Whether the session's own listing still shows its marker: a session directory always holds
     * one, so a listing without it means the session went away after its marker was read — or that
     * the marker was remembered from before.
     */
    private static boolean stillListed(VolumeSession session) {
        return session.listing().holdsFile(JeffreyLayout.SESSION_INFO_FILE, () -> true);
    }

    /**
     * Every instance with its count and its newest session. Only the newest session's liveness
     * files are read; the others' markers give the count.
     */
    public List<InstanceSummary> instances() {
        return fanOut.map(tree.instances(fanOut), instance -> {
            List<SessionDirectory> oldestFirst = tree.sessionsOldestFirst(instance.path());
            Optional<VolumeSession> newest = oldestFirst.isEmpty()
                    ? Optional.empty()
                    : Optional.of(read(oldestFirst.getLast(), oldestFirst));
            return new InstanceSummary(instance, oldestFirst.size(), newest);
        });
    }

    /** One instance with every session of it, or empty when the project holds no instance by that id. */
    public Optional<InstanceSessionList> instance(String instanceId) {
        return instance(instanceId, SessionPage.ALL);
    }

    /**
     * One instance with a page of its sessions, newest first, or empty when the project holds no
     * instance by that id. Its listing orders the page: on a share that lists when each directory
     * was created, only the page's sessions are read — their markers, listings and liveness — and
     * the count is the instance's session directories. One that lists no times (an NFS share)
     * reads every session's marker for the order, as a read of all of them does.
     */
    public Optional<InstanceSessionList> instance(String instanceId, SessionPage page) {
        return tree.instance(instanceId).flatMap(instance -> tree.instanceListing(instance)
                .map(listed -> page.isAll() || !listsTimes(listed)
                        ? pageOfEvery(instance, listed, page)
                        : pageByListedTime(instance, listed, page)));
    }

    private static boolean listsTimes(List<VolumeEntry> listed) {
        return ProjectTree.visibleDirectories(listed).stream().allMatch(entry -> entry.createdAt().isPresent());
    }

    /** Every session's marker read and ordered, the page taken from that order. */
    private InstanceSessionList pageOfEvery(InstanceDirectory instance, List<VolumeEntry> listed, SessionPage page) {
        List<SessionDirectory> oldestFirst = tree.sessionsOldestFirst(listed, fanOut);
        List<SessionDirectory> newestFirst = oldestFirst.reversed();
        int start = startOf(newestFirst.stream().map(SessionDirectory::id).toList(), page);
        int end = (int) Math.min((long) start + page.limit(), newestFirst.size());
        List<VolumeSession> sessions = fanOut.map(newestFirst.subList(start, end), session -> read(session, oldestFirst));
        Optional<VolumeSession> newest = start == 0 && !sessions.isEmpty()
                ? Optional.of(sessions.getFirst())
                : newestFirst.stream().findFirst().map(session -> read(session, oldestFirst));
        return new InstanceSessionList(
                new InstanceSummary(instance, oldestFirst.size(), newest), sessions, end < newestFirst.size());
    }

    /** The page read off the listing's order, newest directory first; only its sessions are read. */
    private InstanceSessionList pageByListedTime(InstanceDirectory instance, List<VolumeEntry> listed, SessionPage page) {
        List<VolumeEntry> newestFirst = ProjectTree.visibleDirectories(listed).stream()
                .sorted(NEWEST_LISTED_FIRST)
                .toList();
        int next = startOf(newestFirst.stream().map(VolumeEntry::name).toList(), page);
        List<SessionDirectory> taken = new ArrayList<>();
        // A directory whose marker is not written yet is no session, so the page reads on past it
        while (next < newestFirst.size() && taken.size() < page.limit()) {
            List<VolumeEntry> candidates =
                    newestFirst.subList(next, Math.min(next + page.limit() - taken.size(), newestFirst.size()));
            fanOut.map(candidates, entry -> tree.sessionAt(entry.path())).forEach(found -> found.ifPresent(taken::add));
            next += candidates.size();
        }
        List<VolumeSession> sessions = fanOut.map(
                taken.stream().sorted(NEWEST_SESSION_FIRST).toList(),
                session -> read(session, () -> tree.nextStart(session, listed)));
        Optional<VolumeSession> newest = page.before().isEmpty() && !sessions.isEmpty()
                ? Optional.of(sessions.getFirst())
                : newestOf(newestFirst, listed);
        // Its session directories, a directory whose marker is not written yet counted with them
        int sessionCount = newest.isPresent() ? newestFirst.size() : 0;
        return new InstanceSessionList(
                new InstanceSummary(instance, sessionCount, newest),
                sessions,
                next < newestFirst.size());
    }

    /** The newest session among listed directories, newest directory first; empty while none is described. */
    private Optional<VolumeSession> newestOf(List<VolumeEntry> newestFirst, List<VolumeEntry> listed) {
        return newestFirst.stream()
                .map(entry -> tree.sessionAt(entry.path()))
                .flatMap(Optional::stream)
                .findFirst()
                .map(session -> read(session, () -> tree.nextStart(session, listed)));
    }

    /** Where a page starts: after the session its cursor names, or past the end for one not there. */
    private static int startOf(List<String> idsNewestFirst, SessionPage page) {
        return page.before()
                .map(before -> {
                    int at = idsNewestFirst.indexOf(before);
                    return at < 0 ? idsNewestFirst.size() : at + 1;
                })
                .orElse(0);
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
