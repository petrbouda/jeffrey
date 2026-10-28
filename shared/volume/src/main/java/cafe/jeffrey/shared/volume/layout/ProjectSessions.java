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

import cafe.jeffrey.shared.volume.ReadFanOut;
import cafe.jeffrey.shared.volume.VolumeEntry;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * A project's sessions as they are at the moment of reading: each one's directory, whether it is
 * its instance's newest and, when read, its listing. The one place every reader of a volume — the
 * hub, a Microscope reading the share directly — derives that from, so they cannot come to
 * different answers.
 *
 * <p>Nothing is tracked about a session beyond its order: a newer session in its instance means it
 * has ended, and only the newest session's newest recording may still be on its way onto the volume.
 *
 * <p>On a file share every question asked of the volume is a round-trip. An instance's sessions
 * are one listing of its directory, their names saying when each started; a session is read — its
 * directory listed, its files described off that listing ({@link ProjectTree#files(VolumeSession)})
 * — only where its files are needed.
 */
public final class ProjectSessions {

    private static final Comparator<VolumeSession> NEWEST_FIRST =
            Comparator.comparing(VolumeSession::createdAt).reversed();

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

    /** Every session of the project, newest first, each read: every instance's listing, then every session's. */
    public List<VolumeSession> all() {
        List<List<SessionDirectory>> byInstance =
                fanOut.map(tree.instances(), instance -> tree.sessionsOldestFirst(instance.path()));
        List<SessionHeader> headers = byInstance.stream()
                .flatMap(oldestFirst -> oldestFirst.stream()
                        .map(session -> new SessionHeader(session, session.equals(oldestFirst.getLast()))))
                .toList();
        return fanOut.map(headers, this::read).stream()
                .sorted(NEWEST_FIRST)
                .toList();
    }

    /** The newest {@code limit} sessions of every instance, each instance's newest first, each read. */
    public List<VolumeSession> newestOfEachInstance(int limit) {
        return fanOut.map(tree.instances(), instance -> {
                    List<SessionHeader> newestFirst = newestFirst(tree.sessionsOldestFirst(instance.path()));
                    return newestFirst.subList(0, Math.min(limit, newestFirst.size())).stream()
                            .map(this::read)
                            .toList();
                })
                .stream()
                .flatMap(List::stream)
                .toList();
    }

    /**
     * One session, read, or empty when the project holds none by that id. Whether it is its
     * instance's newest is read off the instance's listing.
     */
    public Optional<VolumeSession> session(String sessionId) {
        return tree.session(sessionId)
                .map(session -> read(new SessionHeader(session, tree.isNewest(session))));
    }

    /**
     * Every instance with its count and its newest session, read: one listing of each instance's
     * directory, whose names say which session is the newest and how many there are, then that
     * session's listing.
     */
    public List<InstanceSummary> instances() {
        return fanOut.map(tree.instances(), instance -> {
            List<SessionDirectory> oldestFirst = tree.sessionsOldestFirst(instance.path());
            Optional<VolumeSession> newest = oldestFirst.isEmpty()
                    ? Optional.empty()
                    : Optional.of(read(new SessionHeader(oldestFirst.getLast(), true)));
            return new InstanceSummary(instance, oldestFirst.size(), newest);
        });
    }

    /**
     * One instance with a page of its sessions, newest first, or empty when the project holds no
     * instance by that id: one look at the instance and one listing of it, whose names order the
     * page. No session is read — what a session holds is read when it is opened.
     */
    public Optional<InstanceSessionList> instance(String instanceId, SessionPage page) {
        return tree.instance(instanceId).map(instance -> {
            List<SessionHeader> newestFirst = newestFirst(tree.sessionsOldestFirst(instance.path()));
            int start = startOf(newestFirst, page);
            int end = (int) Math.min((long) start + page.limit(), newestFirst.size());
            return new InstanceSessionList(instance, newestFirst.subList(start, end), end < newestFirst.size());
        });
    }

    /** An instance's sessions newest first, the first of them its newest. */
    private static List<SessionHeader> newestFirst(List<SessionDirectory> oldestFirst) {
        List<SessionDirectory> reversed = oldestFirst.reversed();
        return reversed.stream()
                .map(session -> new SessionHeader(session, session.equals(reversed.getFirst())))
                .toList();
    }

    /** Where a page starts: after the session its cursor names, or past the end for one not there. */
    private static int startOf(List<SessionHeader> newestFirst, SessionPage page) {
        List<String> ids = newestFirst.stream().map(SessionHeader::id).toList();
        return page.before()
                .map(before -> {
                    int at = ids.indexOf(before);
                    return at < 0 ? ids.size() : at + 1;
                })
                .orElse(0);
    }

    /** A session off one listing of its directory. */
    private VolumeSession read(SessionHeader session) {
        return new VolumeSession(session.directory(), session.newest(), tree.listing(session.directory()));
    }
}
