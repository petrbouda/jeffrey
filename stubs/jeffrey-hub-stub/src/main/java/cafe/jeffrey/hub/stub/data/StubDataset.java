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
            List<Project> projects) {
    }

    public record Project(
            String id,
            String originId,
            String name,
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

    /** Recording/session lifecycle state. */
    public enum RecState {
        ACTIVE, FINISHED, UNKNOWN
    }

    /** Instance lifecycle state. */
    public enum InstState {
        PENDING, ACTIVE, FINISHED, EXPIRED
    }

    /**
     * File classification. Only {@code recording} reaches the wire: the hub says whether a file
     * is a recording chunk and nothing finer, and Microscope classifies the name itself — so the
     * names the factory gives these files ({@code gc.jvm-log}, {@code heapdump.hprof}) are what
     * decide how the client reads them.
     */
    public enum FileKind {
        JFR(true),
        HEAP_DUMP(false),
        GC_LOG(false),
        HS_ERR_LOG(false),
        APP_LOG(false),
        OTHER(false);

        private final boolean recording;

        FileKind(boolean recording) {
            this.recording = recording;
        }

        public boolean recording() {
            return recording;
        }
    }
}
