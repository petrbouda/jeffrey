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

import cafe.jeffrey.shared.common.JeffreyLayout;
import cafe.jeffrey.shared.volume.ReadFanOut;
import cafe.jeffrey.shared.volume.VolumeEntry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * A project's sessions as they are at the moment of reading: each one's directory, its listing,
 * and whether it is its instance's newest. The one place every reader of a volume — the hub, a
 * Microscope reading the storage directly — derives that from, so they cannot come to different
 * answers.
 *
 * <p>Nothing is tracked about a session beyond its order: a newer session in its instance means it
 * has ended, and only the newest session's newest recording may still be on its way onto the volume.
 *
 * <p>On a file share every question asked of the volume is a round-trip, so a session costs its
 * marker and one listing of its directory; the files are described off that same listing
 * ({@link ProjectTree#files(VolumeSession)}).
 */
public final class ProjectSessions {

    private static final Comparator<VolumeSession> NEWEST_FIRST =
            Comparator.comparing(VolumeSession::createdAt).reversed();

    /** A page's sessions newest first by their starts, a tie broken by name, as an instance lists them. */
    private static final Comparator<SessionDirectory> NEWEST_SESSION_FIRST =
            Comparator.comparing(SessionDirectory::createdAt).thenComparing(SessionDirectory::id).reversed();

    /** Session directories newest first by the start their names carry, a tie broken by name. */
    private static final Comparator<VolumeEntry> NEWEST_NAMED_FIRST = ProjectTree.OLDEST_NAMED_FIRST.reversed();

    private final ProjectTree tree;
    private final ReadFanOut fanOut;

    /** Reads one thing after another: the hub's, on its own mount. */
    public ProjectSessions(ProjectTree tree) {
        this(tree, ReadFanOut.sequential());
    }

    /** @param fanOut how the independent reads of one call are run — several at a time on a share */
    public ProjectSessions(ProjectTree tree, ReadFanOut fanOut) {
        this.tree = tree;
        this.fanOut = fanOut;
    }

    /** Every session of the project, newest first: each instance's markers, then every session, fanned out. */
    public List<VolumeSession> all() {
        List<List<SessionDirectory>> byInstance =
                fanOut.map(tree.instances(fanOut), instance -> tree.sessionsOldestFirst(instance.path()));
        record Placed(SessionDirectory session, boolean newest) {
        }
        List<Placed> placed = byInstance.stream()
                .flatMap(oldestFirst -> oldestFirst.stream()
                        .map(session -> new Placed(session, session.equals(oldestFirst.getLast()))))
                .toList();
        return fanOut.map(placed, one -> read(one.session(), one.newest())).stream()
                .sorted(NEWEST_FIRST)
                .toList();
    }

    /** One instance's sessions, newest first. */
    public List<VolumeSession> of(InstanceDirectory instance) {
        List<SessionDirectory> oldestFirst = tree.sessionsOldestFirst(instance.path(), fanOut);
        return fanOut.map(oldestFirst, session -> read(session, session.equals(oldestFirst.getLast()))).reversed();
    }

    /**
     * One session, or empty when the project holds none by that id. Whether it is its instance's
     * newest is read off the instance's listing, whose names say which siblings started after it
     * ({@link ProjectTree#isNewest(SessionDirectory)}).
     */
    public Optional<VolumeSession> session(String sessionId) {
        return tree.session(sessionId)
                .map(session -> read(session, tree.isNewest(session)))
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
     * Every instance with its count and its newest session, each off one listing of its directory:
     * the names say which session is the newest, so only its marker is read — and the next one's
     * while a directory's marker is not written yet — and the count is the instance's session
     * directories, a directory whose marker is not written yet counted with them. A directory named
     * otherwise has only its marker to say when it started, so every marker is then read.
     */
    public List<InstanceSummary> instances() {
        return fanOut.map(tree.instances(fanOut), instance -> {
            List<VolumeEntry> listed = tree.sessionDirectoryEntries(instance.path());
            if (!ProjectTree.namesCarryStarts(listed)) {
                List<SessionDirectory> oldestFirst = tree.sessionsOldestFirst(listed, ReadFanOut.sequential());
                Optional<VolumeSession> newest = oldestFirst.isEmpty()
                        ? Optional.empty()
                        : Optional.of(read(oldestFirst.getLast(), true));
                return new InstanceSummary(instance, oldestFirst.size(), newest);
            }
            List<VolumeEntry> newestFirst = listed.stream().sorted(NEWEST_NAMED_FIRST).toList();
            Optional<VolumeSession> newest = firstSessionOf(newestFirst).map(session -> read(session, true));
            return new InstanceSummary(instance, newest.isPresent() ? newestFirst.size() : 0, newest);
        });
    }

    /** One instance with every session of it, or empty when the project holds no instance by that id. */
    public Optional<InstanceSessionList> instance(String instanceId) {
        return instance(instanceId, SessionPage.ALL);
    }

    /**
     * One instance with a page of its sessions, newest first, or empty when the project holds no
     * instance by that id. Its listing orders the page, each session directory being named for its
     * start: only the page's sessions are read — their markers and listings — and the count is the
     * instance's session directories, on any share, whatever times it lists. A directory named
     * otherwise has only its marker to say when it started, so every session's marker is then read
     * for the order, as a read of all of them does.
     */
    public Optional<InstanceSessionList> instance(String instanceId, SessionPage page) {
        return tree.instance(instanceId).flatMap(instance -> tree.instanceListing(instance)
                .map(listed -> page.isAll() || !ProjectTree.namesCarryStarts(listed)
                        ? pageOfEvery(instance, listed, page)
                        : pageByName(instance, listed, page)));
    }

    /** Every session's marker read and ordered, the page taken from that order. */
    private InstanceSessionList pageOfEvery(InstanceDirectory instance, List<VolumeEntry> listed, SessionPage page) {
        List<SessionDirectory> newestFirst = tree.sessionsOldestFirst(listed, fanOut).reversed();
        Optional<SessionDirectory> newestDirectory = newestFirst.stream().findFirst();
        int start = startOf(newestFirst.stream().map(SessionDirectory::id).toList(), page);
        int end = (int) Math.min((long) start + page.limit(), newestFirst.size());
        return assemble(instance, newestFirst.subList(start, end), newestDirectory, newestFirst.size(),
                end < newestFirst.size());
    }

    /** The page read off the names' order, newest directory first; only its sessions are read. */
    private InstanceSessionList pageByName(InstanceDirectory instance, List<VolumeEntry> listed, SessionPage page) {
        List<VolumeEntry> newestFirst = ProjectTree.visibleDirectories(listed).stream()
                .sorted(NEWEST_NAMED_FIRST)
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
        List<SessionDirectory> pageSessions = taken.stream().sorted(NEWEST_SESSION_FIRST).toList();
        Optional<SessionDirectory> newestDirectory = page.before().isEmpty() && !pageSessions.isEmpty()
                ? Optional.of(pageSessions.getFirst())
                : firstSessionOf(newestFirst);
        // Its session directories, a directory whose marker is not written yet counted with them
        int sessionCount = newestDirectory.isPresent() ? newestFirst.size() : 0;
        return assemble(instance, pageSessions, newestDirectory, sessionCount, next < newestFirst.size());
    }

    /** The newest session among listed directories, newest directory first; empty while none is described. */
    private Optional<SessionDirectory> firstSessionOf(List<VolumeEntry> newestFirst) {
        return newestFirst.stream()
                .map(entry -> tree.sessionAt(entry.path()))
                .flatMap(Optional::stream)
                .findFirst();
    }

    /** The page read, and the instance's newest session with it — taken from the page when it is on it. */
    private InstanceSessionList assemble(
            InstanceDirectory instance,
            List<SessionDirectory> pageSessions,
            Optional<SessionDirectory> newestDirectory,
            int sessionCount,
            boolean hasOlder) {

        List<VolumeSession> sessions = fanOut.map(pageSessions,
                session -> read(session, newestDirectory.filter(session::equals).isPresent()));
        Optional<VolumeSession> newest = newestDirectory.map(directory -> sessions.stream()
                .filter(session -> session.directory().equals(directory))
                .findFirst()
                .orElseGet(() -> read(directory, true)));
        return new InstanceSessionList(new InstanceSummary(instance, sessionCount, newest), sessions, hasOlder);
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

    /** A session off one listing of its directory. */
    private VolumeSession read(SessionDirectory session, boolean newest) {
        return new VolumeSession(session, newest, tree.listing(session));
    }
}
