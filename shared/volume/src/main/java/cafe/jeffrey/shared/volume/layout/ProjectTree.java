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
import cafe.jeffrey.shared.common.model.repository.RemoteProject;
import cafe.jeffrey.shared.common.model.repository.RemoteProjectInstance;
import cafe.jeffrey.shared.common.model.repository.RemoteProjectInstanceSession;
import cafe.jeffrey.shared.volume.ReadFanOut;
import cafe.jeffrey.shared.volume.VolumeAccess;
import cafe.jeffrey.shared.volume.VolumeEntry;
import cafe.jeffrey.shared.volume.VolumePath;
import cafe.jeffrey.shared.volume.recording.RecordingFormat;
import cafe.jeffrey.shared.volume.recording.TimestampResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UncheckedIOException;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * A project's directory as it lies on the volume, which is all anyone knows of it: instance
 * directories with their {@code .instance-info.json}, session directories under them with their
 * {@code .session-info.json}, and the files a profiler left behind. Nothing is cached; every call
 * reads the volume again.
 *
 * <p>Ids come from the volume: an instance's and a session's are their directory names. A file's
 * id, timestamp and whether it is a recording come from {@link RecordingFormat#of(String)}.
 */
public final class ProjectTree {

    private static final Logger LOG = LoggerFactory.getLogger(ProjectTree.class);

    /**
     * How a session directory is listed: newest first by filename. Presentation only — which chunk
     * the profiler still holds open is derived by each reader's session model, never from this order.
     */
    private static final Comparator<VolumeEntry> NEWEST_BY_NAME =
            Comparator.comparing(VolumeEntry::name).reversed();

    /** The order an instance's sessions started in: by their markers, a tie broken by name. */
    private static final Comparator<SessionDirectory> OLDEST_FIRST =
            Comparator.comparing(SessionDirectory::createdAt).thenComparing(SessionDirectory::id);

    /**
     * How much earlier than a session's directory a sibling's must have been created before it is
     * not read as a possibly newer session. The two times come from the volume's own clock, but a
     * filesystem that keeps no birth time answers its last change instead, so the order is trusted
     * only well clear of a tie.
     */
    private static final Duration DIRECTORY_ORDER_MARGIN = Duration.ofHours(1);

    private final VolumeAccess volume;
    private final VolumePath path;
    private final RemoteProject marker;

    private ProjectTree(VolumeAccess volume, VolumePath path, RemoteProject marker) {
        this.volume = volume;
        this.path = path;
        this.marker = marker;
    }

    /**
     * The project in this directory, or empty when it holds no readable marker naming a project
     * id — the one test of "is this a project", shared by every listing and count.
     */
    public static Optional<ProjectTree> at(VolumeAccess volume, VolumePath directory) {
        return VolumeMarkers.read(volume, directory, JeffreyLayout.PROJECT_INFO_FILE, RemoteProject.class)
                .filter(marker -> marker.projectId() != null && !marker.projectId().isBlank())
                .map(marker -> new ProjectTree(volume, directory, marker));
    }

    /** The projects in a workspace's directory, each a child directory {@link #at} recognises. */
    public static List<ProjectTree> allIn(VolumeAccess volume, VolumePath workspaceDirectory) {
        return VolumeMarkers.childDirectories(volume, workspaceDirectory).stream()
                .map(directory -> at(volume, directory))
                .flatMap(Optional::stream)
                .toList();
    }

    /** Where the project's instances and sessions lie. */
    public VolumePath path() {
        return path;
    }

    /** What the project's {@code .project-info.json} declares. */
    public RemoteProject marker() {
        return marker;
    }

    // ========== Instances ==========

    public List<InstanceDirectory> instances() {
        return instances(ReadFanOut.sequential());
    }

    /** The instances, their markers read through {@code fanOut}. */
    List<InstanceDirectory> instances(ReadFanOut fanOut) {
        return fanOut.map(VolumeMarkers.childDirectories(volume, path), this::instanceAt).stream()
                .flatMap(Optional::stream)
                .toList();
    }

    /** Read straight off its marker: a read answers whether the directory is there, so it is not looked at first. */
    public Optional<InstanceDirectory> instance(String instanceId) {
        return path.visibleChild(instanceId).flatMap(this::instanceAt);
    }

    /**
     * The directory an instance id names, whether or not its marker reads — for removing one the
     * provisioner created and never described.
     */
    public Optional<VolumePath> instanceDirectory(String instanceId) {
        return childDirectory(path, instanceId);
    }

    /**
     * Whether any directory is left in an instance, marker or not: one the provisioner has created
     * but not yet described is a session about to start, and its profiler may already be writing.
     */
    boolean holdsAnyDirectory(VolumePath instanceDir) {
        return !VolumeMarkers.childDirectories(volume, instanceDir).isEmpty();
    }

    private Optional<InstanceDirectory> instanceAt(VolumePath dir) {
        return VolumeMarkers.read(volume, dir, JeffreyLayout.INSTANCE_INFO_FILE, RemoteProjectInstance.class)
                .map(marker -> new InstanceDirectory(dir, Instant.ofEpochMilli(marker.createdAt())));
    }

    // ========== Sessions ==========

    /**
     * A session lies in one of the instance directories, under its own id. Each instance directory
     * is asked for the session's marker directly, and only the one that holds it has its own marker
     * read — not every instance's marker first.
     */
    public Optional<SessionDirectory> session(String sessionId) {
        for (VolumePath instanceDir : VolumeMarkers.childDirectories(volume, path)) {
            Optional<SessionDirectory> session = instanceDir.visibleChild(sessionId)
                    .flatMap(this::sessionAt)
                    .filter(found -> instanceAt(instanceDir).isPresent());
            if (session.isPresent()) {
                return session;
            }
        }
        return Optional.empty();
    }

    /** The session in this directory, or empty when its marker does not read. */
    public Optional<SessionDirectory> sessionAt(VolumePath dir) {
        return VolumeMarkers.read(volume, dir, JeffreyLayout.SESSION_INFO_FILE, RemoteProjectInstanceSession.class)
                .map(marker -> new SessionDirectory(dir, Instant.ofEpochMilli(marker.createdAt()), zoneOf(marker, dir)));
    }

    /** An instance's sessions in the order they started — the order a session's end is read from. */
    public List<SessionDirectory> sessionsOldestFirst(VolumePath instanceDir) {
        return sessionsOldestFirst(instanceDir, ReadFanOut.sequential());
    }

    /** The same, the session markers read through {@code fanOut}. */
    List<SessionDirectory> sessionsOldestFirst(VolumePath instanceDir, ReadFanOut fanOut) {
        return sessionsOldestFirst(VolumeMarkers.childDirectoryEntries(volume, instanceDir), fanOut);
    }

    /** The sessions among an instance directory's listed entries, in the order they started. */
    List<SessionDirectory> sessionsOldestFirst(List<VolumeEntry> instanceEntries, ReadFanOut fanOut) {
        List<VolumePath> directories = visibleDirectories(instanceEntries).stream()
                .map(VolumeEntry::path)
                .sorted(Comparator.comparing(VolumePath::name))
                .toList();
        return fanOut.map(directories, this::sessionAt).stream()
                .flatMap(Optional::stream)
                .sorted(OLDEST_FIRST)
                .toList();
    }

    /**
     * The instance's directory as listed, or empty when the listing no longer shows its marker: a
     * marker remembered from before the instance was removed is not taken for the instance. One that
     * cannot be listed reads as holding nothing, as {@link VolumeMarkers#childDirectories} reads it.
     */
    Optional<List<VolumeEntry>> instanceListing(InstanceDirectory instance) {
        List<VolumeEntry> entries;
        try {
            entries = volume.list(instance.path());
        } catch (UncheckedIOException e) {
            LOG.warn("Cannot list directory: path={}", instance.path(), e);
            return Optional.of(List.of());
        }
        boolean described = entries.stream()
                .anyMatch(entry -> entry.isFile() && entry.name().equals(JeffreyLayout.INSTANCE_INFO_FILE));
        return described ? Optional.of(entries) : Optional.empty();
    }

    /** The visible directories among listed entries; dot-directories are the volume's own bookkeeping. */
    static List<VolumeEntry> visibleDirectories(List<VolumeEntry> entries) {
        return entries.stream()
                .filter(VolumeEntry::isDirectory)
                .filter(entry -> !entry.path().isHidden())
                .toList();
    }

    /**
     * When the session after this one in its instance started, or empty for the newest — what
     * {@link #nextStart(List, SessionDirectory)} answers off every sibling, for one session alone.
     * The instance's listing carries when each directory was created, so only the siblings that may
     * have started after this one have their markers read: for the newest session, usually none.
     * A sibling whose listing carries no time (an NFS share lists none) is always read.
     */
    Optional<Instant> nextStart(SessionDirectory session) {
        return nextStart(session, VolumeMarkers.childDirectoryEntries(volume, session.path().parent()));
    }

    /** The same, off a listing of the session's instance already in hand. */
    Optional<Instant> nextStart(SessionDirectory session, List<VolumeEntry> instanceEntries) {
        List<VolumeEntry> siblings = visibleDirectories(instanceEntries);
        Optional<Instant> ownCreated = siblings.stream()
                .filter(entry -> entry.path().equals(session.path()))
                .findFirst()
                .flatMap(VolumeEntry::createdAt);
        return siblings.stream()
                .filter(entry -> !entry.path().equals(session.path()))
                .filter(entry -> mayHaveStartedAfter(entry, ownCreated))
                .map(entry -> sessionAt(entry.path()))
                .flatMap(Optional::stream)
                .filter(sibling -> OLDEST_FIRST.compare(sibling, session) > 0)
                .min(OLDEST_FIRST)
                .map(SessionDirectory::createdAt);
    }

    private static boolean mayHaveStartedAfter(VolumeEntry sibling, Optional<Instant> ownCreated) {
        if (ownCreated.isEmpty() || sibling.createdAt().isEmpty()) {
            return true;
        }
        return !sibling.createdAt().get().isBefore(ownCreated.get().minus(DIRECTORY_ORDER_MARGIN));
    }

    /**
     * When the session after this one in its instance started, or empty for the newest: a newer
     * session in the same instance is what ended this one.
     */
    static Optional<Instant> nextStart(List<SessionDirectory> oldestFirst, SessionDirectory session) {
        for (int i = 0; i < oldestFirst.size() - 1; i++) {
            if (oldestFirst.get(i).path().equals(session.path())) {
                return Optional.of(oldestFirst.get(i + 1).createdAt());
            }
        }
        return Optional.empty();
    }

    boolean isRetained(SessionDirectory session) {
        return VolumeMarkers.hasFlag(volume, session.path(), SessionDirectory.RETAINED_FLAG);
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

    /**
     * The zone a session's recording names are local to: UTC for a marker written before the zone
     * was recorded, which is what those names were always read as, and for one that names no zone
     * this JVM knows.
     */
    private static ZoneId zoneOf(RemoteProjectInstanceSession marker, VolumePath dir) {
        if (marker.zoneId() == null || marker.zoneId().isBlank()) {
            return ZoneOffset.UTC;
        }
        try {
            return ZoneId.of(marker.zoneId());
        } catch (DateTimeException e) {
            LOG.debug("Session marker names an unknown time zone, reading its recordings in UTC: "
                    + "session_path={} zone_id={}", dir, marker.zoneId());
            return ZoneOffset.UTC;
        }
    }

    // ========== Files ==========

    /**
     * The visible files of a session just read, off the listing it was read from.
     *
     * @throws UncheckedIOException when its directory could not be listed
     */
    public List<SessionFile> files(VolumeSession session) {
        return files(session.listing().entries(), session.zone());
    }

    /**
     * The visible files of a session directory, newest first by name, each with its id, its
     * timestamp read in {@code zone} and whether it is a recording; none when the directory is gone.
     *
     * @throws UncheckedIOException when the directory is there and cannot be listed
     */
    public List<SessionFile> files(VolumePath sessionDir, ZoneId zone) {
        return files(volume.list(sessionDir), zone);
    }

    private List<SessionFile> files(List<VolumeEntry> entries, ZoneId zone) {
        return entries.stream()
                .filter(entry -> !entry.path().isHidden())
                .filter(VolumeEntry::isFile)
                .sorted(NEWEST_BY_NAME)
                .map(entry -> describe(entry, zone))
                .flatMap(Optional::stream)
                .toList();
    }

    /**
     * The file as a session lists it, or empty when it can no longer be described. A file whose
     * name says nothing about when it was opened, on a volume whose listing carries no timestamps,
     * costs one more look at that file alone.
     */
    private Optional<SessionFile> describe(VolumeEntry entry, ZoneId zone) {
        Optional<RecordingFormat> format = RecordingFormat.of(entry.name());
        return format.map(RecordingFormat::timestampResolver)
                .orElse(TimestampResolver.VOLUME)
                .resolve(entry, zone)
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

    /**
     * The directory {@code name} directly under {@code parent}, or empty for a name that is not one
     * plain, visible path element — ids arrive from outside and must not walk out of the project.
     */
    private Optional<VolumePath> childDirectory(VolumePath parent, String name) {
        return parent.visibleChild(name).filter(child -> VolumeMarkers.isDirectory(volume, child));
    }
}
