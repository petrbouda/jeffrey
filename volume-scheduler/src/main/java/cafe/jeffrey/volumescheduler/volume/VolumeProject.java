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

package cafe.jeffrey.volumescheduler.volume;

import cafe.jeffrey.shared.common.filesystem.FileSystemUtils;
import cafe.jeffrey.shared.volume.FilesystemVolume;
import cafe.jeffrey.shared.volume.layout.InstanceDirectory;
import cafe.jeffrey.shared.volume.layout.InstanceSummary;
import cafe.jeffrey.shared.volume.layout.ProjectSessions;
import cafe.jeffrey.shared.volume.layout.ProjectTree;
import cafe.jeffrey.shared.volume.layout.SessionDeletion;
import cafe.jeffrey.shared.volume.layout.SessionFile;
import cafe.jeffrey.shared.volume.layout.SessionHeader;
import cafe.jeffrey.shared.volume.layout.VolumeChanges;
import cafe.jeffrey.shared.volume.layout.VolumeSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * One project directory on the recording volume, which is all the volume scheduler knows of it.
 * What it holds is read through {@link ProjectTree}, the layout every reader of the volume shares;
 * a session is deleted through {@link VolumeChanges}, the rules every writer keeps; files and
 * empty instance directories are deleted here, on the filesystem the volume lies on. Nothing is
 * cached: every call reads the directories again.
 *
 * <p>Changes are addressed by id, never by a path the caller hands in, and each id is matched
 * against a fresh listing of the directory. A recording's id has no extension, so it names no file;
 * an id holding {@code ../} would walk out of the session directory; and the retention and the
 * compression job run side by side under locks of their own, so a {@code .jfr} a pass listed may be
 * a {@code .jfr.lz4} by the time it is deleted — a stale path would delete nothing and leave the
 * archive behind.</p>
 */
public final class VolumeProject {

    private static final Logger LOG = LoggerFactory.getLogger(VolumeProject.class);

    private final ProjectRef ref;
    private final ProjectTree tree;
    private final ProjectSessions sessions;
    private final FilesystemVolume volume;

    /**
     * @param tree   the project's directory, as the volume's layout reads it
     * @param volume the filesystem the tree lies on, where every change is written
     */
    public VolumeProject(ProjectRef ref, ProjectTree tree, FilesystemVolume volume) {
        this.ref = ref;
        this.tree = tree;
        this.sessions = new ProjectSessions(tree);
        this.volume = volume;
    }

    public ProjectRef ref() {
        return ref;
    }

    /** Every session of the project with its files, newest first. */
    public List<RecordingSession> sessions() {
        return sessions.all().stream()
                .map(this::withFiles)
                .toList();
    }

    /**
     * The newest {@code perInstance} sessions of every instance, with their files: only those
     * sessions are read.
     */
    public List<RecordingSession> newestSessions(int perInstance) {
        return sessions.newestOfEachInstance(perInstance).stream()
                .map(this::withFiles)
                .toList();
    }

    /** Every instance of the project with how many sessions it holds, at this moment. */
    public List<ProjectInstance> instances() {
        return sessions.instances().stream()
                .map(VolumeProject::instance)
                .toList();
    }

    /**
     * Deletes a session's directory with everything in it, and its instance's directory when no
     * other directory is left in it ({@link VolumeChanges#deleteSession}).
     */
    public SessionDeletion deleteSession(String sessionId) {
        SessionDeletion deletion = VolumeChanges.deleteSession(volume, tree, sessionId);
        if (deletion.deleted()) {
            LOG.info("Deleted session directory: {} session_id={} instance_deleted={}",
                    ref.logScope(), sessionId, deletion.instanceDeleted());
        } else {
            LOG.warn("Recording session not found, nothing to delete: {} session_id={}", ref.logScope(), sessionId);
        }
        return deletion;
    }

    /**
     * Deletes the files of these ids from a session, matched against a fresh listing of its
     * directory (see the class comment for why never by path).
     */
    public void deleteFiles(String sessionId, List<String> fileIds) {
        Optional<SessionHeader> session = tree.session(sessionId);
        if (session.isEmpty()) {
            return;
        }
        Path sessionPath = volume.resolve(session.get().path());

        Set<String> requestedIds = Set.copyOf(fileIds);
        List<Path> matched;
        try (Stream<Path> files = Files.list(sessionPath)) {
            matched = files
                    .filter(Files::isRegularFile)
                    .filter(file -> requestedIds.contains(fileId(file)))
                    .toList();
        } catch (IOException e) {
            LOG.warn("Cannot list a session directory to delete files from it: {} session_path={}",
                    ref.logScope(), sessionPath, e);
            return;
        }

        // Compared by id, not by count: during a compression a recording and its archive share an id
        Set<String> found = matched.stream()
                .map(VolumeProject::fileId)
                .collect(Collectors.toSet());
        List<String> missing = requestedIds.stream()
                .filter(id -> !found.contains(id))
                .toList();
        if (!missing.isEmpty()) {
            LOG.warn("Session holds no file with these ids, nothing deleted for them: {} session_id={} file_ids={}",
                    ref.logScope(), sessionId, missing);
        }

        for (Path file : matched) {
            FileSystemUtils.removeFile(file);
        }
        LOG.info("Deleted files of a session: {} session_id={} file_ids={}", ref.logScope(), sessionId, found);
    }

    /**
     * Deletes an instance's directory with whatever is left in it, unless it holds a session or
     * cannot be listed to tell. Looks at the instance once more first: a session the provisioner
     * laid down since it was listed keeps it, and so does a listing that fails, which would
     * otherwise read as no session at all.
     *
     * @return whether it was deleted
     */
    public boolean deleteInstance(String instanceId) {
        Optional<InstanceDirectory> instance = tree.instance(instanceId);
        if (instance.isEmpty()) {
            return false;
        }
        if (tree.holdsAnySession(instance.get())) {
            LOG.warn("Kept an instance directory that holds a session or cannot be listed: {} instance_id={}",
                    ref.logScope(), instanceId);
            return false;
        }
        volume.delete(instance.get().path());
        LOG.info("Deleted instance directory: {} instance_id={}", ref.logScope(), instanceId);
        return true;
    }

    private RecordingSession withFiles(VolumeSession session) {
        List<RecordingSessionFile> files = tree.files(session).stream()
                .map(this::file)
                .toList();
        return new RecordingSession(session.id(), session.instanceId(), session.createdAt(), session.newest(), files);
    }

    private RecordingSessionFile file(SessionFile file) {
        return new RecordingSessionFile(
                file.id(), file.name(), file.createdAt(), file.size(), file.recording(), volume.resolve(file.path()));
    }

    private static ProjectInstance instance(InstanceSummary summary) {
        return new ProjectInstance(summary.instance().id(), summary.instance().createdAt(), summary.sessionCount());
    }

    private static String fileId(Path file) {
        return SessionFile.idOf(file.getFileName().toString());
    }
}
