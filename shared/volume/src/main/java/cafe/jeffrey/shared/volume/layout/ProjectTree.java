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

import cafe.jeffrey.shared.common.IDGenerator;
import cafe.jeffrey.shared.volume.VolumeAccess;
import cafe.jeffrey.shared.volume.VolumeEntry;
import cafe.jeffrey.shared.volume.VolumePath;
import cafe.jeffrey.shared.volume.recording.RecordingFormat;
import cafe.jeffrey.shared.volume.recording.TimestampResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * A project's directory as it lies on the volume, which is all anyone knows of it: instance
 * directories, session directories under them, and the files a profiler left behind. Nothing is
 * cached and nothing is described by a marker file: every call lists the volume again, and every
 * id is a directory name.
 *
 * <p>A project's id is its directory name — the project name the provisioner was configured with —
 * as are an instance's and a session's. A session's name, a time-ordered id, says when it started.
 * A file's id, timestamp and whether it is a recording come from {@link RecordingFormat#of(String)}.
 */
public final class ProjectTree {

    private static final Logger LOG = LoggerFactory.getLogger(ProjectTree.class);

    /**
     * How a session directory is listed: newest first by filename. Presentation only — which chunk
     * the profiler still holds open is derived by each reader's session model, never from this order.
     */
    private static final Comparator<VolumeEntry> NEWEST_BY_NAME =
            Comparator.comparing(VolumeEntry::name).reversed();

    /** The order an instance's sessions started in, newest first: by their starts, a tie broken by name. */
    private static final Comparator<SessionDirectory> NEWEST_FIRST =
            Comparator.comparing(SessionDirectory::createdAt).thenComparing(SessionDirectory::id).reversed();

    private final VolumeAccess volume;
    private final VolumePath path;
    private final Optional<Instant> createdAt;

    private ProjectTree(VolumeAccess volume, VolumePath path, Optional<Instant> createdAt) {
        this.volume = volume;
        this.path = path;
        this.createdAt = createdAt;
    }

    /**
     * The project in this directory, or empty when no directory is there: one look at it, nothing
     * below it — the one test of "is this a project".
     */
    public static Optional<ProjectTree> at(VolumeAccess volume, VolumePath directory) {
        return VolumeDirectories.directory(volume, directory).map(entry -> new ProjectTree(volume, directory, entry.createdAt()));
    }

    /**
     * The project at this directory, taken as given — for the reads of a request that resolved the
     * project already ({@link #at}), so they do not look at it again. A directory that has gone
     * since lists nothing.
     */
    public static ProjectTree of(VolumeAccess volume, VolumePath directory) {
        return new ProjectTree(volume, directory, Optional.empty());
    }

    /** The projects in a workspace's directory, each a visible child directory: one listing. */
    public static List<ProjectTree> allIn(VolumeAccess volume, VolumePath workspaceDirectory) {
        return VolumeDirectories.childDirectoryEntries(volume, workspaceDirectory).stream()
                .map(entry -> new ProjectTree(volume, entry.path(), entry.createdAt()))
                .toList();
    }

    /** The project's id and name: its directory name. */
    public String id() {
        return path.name();
    }

    /** Where the project's instances and sessions lie. */
    public VolumePath path() {
        return path;
    }

    /** When the project's directory was created, as the volume lists it; empty on a share that lists no times. */
    public Optional<Instant> createdAt() {
        return createdAt;
    }

    // ========== Instances ==========

    /** The project's instances, each a visible child directory: one listing. */
    public List<InstanceDirectory> instances() {
        return VolumeDirectories.childDirectoryEntries(volume, path).stream()
                .map(entry -> new InstanceDirectory(entry.path(), entry.createdAt()))
                .toList();
    }

    /** One instance, or empty when the project holds no directory by that id: one look at it. */
    public Optional<InstanceDirectory> instance(String instanceId) {
        return path.visibleChild(instanceId)
                .flatMap(dir -> VolumeDirectories.directory(volume, dir)
                        .map(entry -> new InstanceDirectory(dir, entry.createdAt())));
    }

    /**
     * Whether any directory is left in an instance: one the provisioner has created but whose
     * profiler has not written yet is a session about to start.
     */
    boolean holdsAnyDirectory(VolumePath instanceDir) {
        return !VolumeDirectories.childDirectories(volume, instanceDir).isEmpty();
    }

    // ========== Sessions ==========

    /**
     * An instance's sessions newest first, off one listing of its directory: each a visible
     * directory named with the time-ordered id the provisioner gives a session (a UUIDv7), whose
     * name says when it started. The first is the instance's newest — no newer session has started
     * there — and the one rule for which session that is.
     */
    public List<SessionHeader> sessionsNewestFirst(InstanceDirectory instance) {
        List<SessionDirectory> newestFirst = VolumeDirectories.childDirectories(volume, instance.path()).stream()
                .flatMap(dir -> IDGenerator.timestampOf(dir.name())
                        .map(start -> new SessionDirectory(dir, start))
                        .stream())
                .sorted(NEWEST_FIRST)
                .toList();
        return newestFirst.stream()
                .map(session -> new SessionHeader(session, session.equals(newestFirst.getFirst())))
                .toList();
    }

    /**
     * A session lies in one of the instance directories, under its own id: the project is listed
     * for its instances, and each instance in turn until one holds it — the listing that finds it
     * also says whether it is the newest. An id that is not a session's name asks the volume nothing.
     */
    public Optional<SessionHeader> session(String sessionId) {
        if (IDGenerator.timestampOf(sessionId).isEmpty()) {
            return Optional.empty();
        }
        for (InstanceDirectory instance : instances()) {
            Optional<SessionHeader> session = sessionsNewestFirst(instance).stream()
                    .filter(header -> header.id().equals(sessionId))
                    .findFirst();
            if (session.isPresent()) {
                return session;
            }
        }
        return Optional.empty();
    }

    /**
     * The one listing of a session directory every answer about it is read off. One that fails is
     * kept as the failure, so the session is still read, each answer asking the volume instead.
     */
    SessionListing listing(SessionDirectory session) {
        try {
            return new SessionListing.Listed(volume.list(session.path()));
        } catch (UncheckedIOException e) {
            LOG.warn("Cannot list a session directory, asking the volume for each answer instead: session_path={} "
                    + "reason={}", session.path(), e.getMessage());
            return new SessionListing.Unlisted(e);
        }
    }

    // ========== Files ==========

    /**
     * The visible files of a session just read, off the listing it was read from.
     *
     * @throws UncheckedIOException when its directory could not be listed
     */
    public List<SessionFile> files(VolumeSession session) {
        return files(session.listing().entries());
    }

    /**
     * The visible files of a session just read as its listing names them, none of them looked at:
     * enough to count them and add up their sizes.
     *
     * @throws UncheckedIOException when its directory could not be listed
     */
    public List<VolumeEntry> listedFiles(VolumeSession session) {
        return session.listing().entries().stream()
                .filter(VolumeEntry::isFile)
                .filter(entry -> !entry.path().isHidden())
                .toList();
    }

    /**
     * When each recording of a session just read started, oldest first, off the listing it was
     * read from and by the timestamps in their names: no file is looked at, so it asks the volume
     * nothing. Empty when the session's directory could not be listed.
     */
    public List<Instant> recordingStarts(VolumeSession session) {
        List<VolumeEntry> entries;
        try {
            entries = session.listing().entries();
        } catch (UncheckedIOException e) {
            return List.of();
        }
        return entries.stream()
                .filter(VolumeEntry::isFile)
                .filter(entry -> !entry.path().isHidden())
                .flatMap(entry -> RecordingFormat.of(entry.name())
                        .flatMap(format -> format.timestampResolver().resolve(entry))
                        .stream())
                .sorted()
                .toList();
    }

    /**
     * The visible files of a session directory, newest first by name, each with its id, its
     * timestamp and whether it is a recording; none when the directory is gone.
     *
     * @throws UncheckedIOException when the directory is there and cannot be listed
     */
    public List<SessionFile> files(VolumePath sessionDir) {
        return files(volume.list(sessionDir));
    }

    private List<SessionFile> files(List<VolumeEntry> entries) {
        return entries.stream()
                .filter(entry -> !entry.path().isHidden())
                .filter(VolumeEntry::isFile)
                .sorted(NEWEST_BY_NAME)
                .map(this::describe)
                .flatMap(Optional::stream)
                .toList();
    }

    /**
     * The file as a session lists it, or empty when it can no longer be described. A file whose
     * name says nothing about when it was opened, on a volume whose listing carries no timestamps,
     * costs one more look at that file alone.
     */
    private Optional<SessionFile> describe(VolumeEntry entry) {
        Optional<RecordingFormat> format = RecordingFormat.of(entry.name());
        return format.map(RecordingFormat::timestampResolver)
                .orElse(TimestampResolver.VOLUME)
                .resolve(entry)
                .or(() -> createdAtOnALook(entry.path()))
                .map(createdAt -> new SessionFile(
                        SessionFile.idOf(entry.name()), entry.name(), createdAt, entry.size(), format.isPresent(),
                        entry.path()));
    }

    /** When a file was created, from a look at it alone; empty, and the file left out, when it cannot say. */
    private Optional<Instant> createdAtOnALook(VolumePath file) {
        Optional<VolumeEntry> entry;
        try {
            entry = volume.stat(file);
        } catch (UncheckedIOException e) {
            LOG.debug("Leaving out a session file that cannot be looked at: path={} reason={}", file, e.getMessage());
            return Optional.empty();
        }
        if (entry.isEmpty()) {
            LOG.debug("Leaving out a session file that went away while it was listed: path={}", file);
            return Optional.empty();
        }
        if (entry.get().createdAt().isEmpty()) {
            LOG.warn("Leaving out a session file the volume gives no time for: path={}", file);
        }
        return entry.get().createdAt();
    }
}
