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

package cafe.jeffrey.hub.stub.data;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Immutable, framework-free in-memory dataset served by the stub.
 *
 * <p>The shape mirrors the real jeffrey-hub domain: a server holds workspaces,
 * each workspace holds projects and a workspace-event log, each project holds
 * instances, each instance holds recording sessions, and each session holds files.
 * The gRPC services map these records onto the {@code cafe.jeffrey.hub.api.v1}
 * protobuf messages — no gRPC types appear here.
 */
public record StubDataset(List<Workspace> workspaces) {

    public Optional<Workspace> workspace(String workspaceId) {
        return workspaces.stream()
                .filter(workspace -> workspace.id().equals(workspaceId))
                .findFirst();
    }

    public List<Project> projects(String workspaceId, boolean includeDeleted) {
        return workspace(workspaceId)
                .map(Workspace::projects)
                .orElse(List.of())
                .stream()
                .filter(project -> includeDeleted || project.deletedAt() == null)
                .toList();
    }

    public Optional<Project> project(String projectId) {
        return workspaces.stream()
                .flatMap(workspace -> workspace.projects().stream())
                .filter(project -> project.id().equals(projectId))
                .findFirst();
    }

    public Optional<Instance> instance(String instanceId) {
        return workspaces.stream()
                .flatMap(workspace -> workspace.projects().stream())
                .flatMap(project -> project.instances().stream())
                .filter(instance -> instance.id().equals(instanceId))
                .findFirst();
    }

    public List<Session> sessionsForProject(String projectId) {
        return project(projectId)
                .map(project -> project.instances().stream()
                        .flatMap(instance -> instance.sessions().stream())
                        .toList())
                .orElse(List.of());
    }

    public Optional<Session> session(String sessionId) {
        return workspaces.stream()
                .flatMap(workspace -> workspace.projects().stream())
                .flatMap(project -> project.instances().stream())
                .flatMap(instance -> instance.sessions().stream())
                .filter(session -> session.id().equals(sessionId))
                .findFirst();
    }

    public record Workspace(
            String id,
            String name,
            String referenceId,
            Instant createdAt,
            List<Project> projects,
            List<Event> events) {
    }

    public record Project(
            String id,
            String originId,
            String name,
            String label,
            String namespace,
            Instant createdAt,
            String workspaceId,
            RecState status,
            Instant deletedAt,
            List<Instance> instances) {

        public int sessionCount() {
            return instances.stream().mapToInt(instance -> instance.sessions().size()).sum();
        }
    }

    public record Instance(
            String id,
            String name,
            InstState status,
            Instant createdAt,
            Instant finishedAt,
            Instant expiringAt,
            Instant expiredAt,
            String activeSessionId,
            List<Session> sessions) {
    }

    public record Session(
            String id,
            String repositoryId,
            String name,
            String instanceId,
            Instant createdAt,
            Instant finishedAt,
            boolean active,
            RecState status,
            List<File> files) {
    }

    public record File(
            String id,
            String name,
            Instant createdAt,
            long size,
            FileKind kind,
            RecState status) {
    }

    public record Event(
            long eventId,
            String originEventId,
            String projectId,
            String workspaceRefId,
            String eventType,
            String content,
            Instant originCreatedAt,
            Instant createdAt,
            String createdBy) {
    }

    /** Recording/session lifecycle state. */
    public enum RecState {
        ACTIVE, FINISHED, UNKNOWN
    }

    /** Instance lifecycle state. */
    public enum InstState {
        PENDING, ACTIVE, FINISHED, EXPIRED
    }

    /**
     * File classification. {@code fileType} is the wire string the hub sends — it MUST be a
     * {@code SupportedRecordingFile} enum name, because the client resolves it with
     * {@code SupportedRecordingFile.valueOf(...)} (an unknown name yields a null fileType and
     * a downstream NPE). {@code recording} distinguishes JFR recordings from artifacts.
     */
    public enum FileKind {
        JFR("JFR", true),
        HEAP_DUMP("HEAP_DUMP", false),
        GC_LOG("JVM_LOG", false),
        HS_ERR_LOG("HS_JVM_ERROR_LOG", false),
        APP_LOG("APP_LOG", false),
        OTHER("UNKNOWN", false);

        private final String fileType;
        private final boolean recording;

        FileKind(String fileType, boolean recording) {
            this.fileType = fileType;
            this.recording = recording;
        }

        public String fileType() {
            return fileType;
        }

        public boolean recording() {
            return recording;
        }
    }
}
