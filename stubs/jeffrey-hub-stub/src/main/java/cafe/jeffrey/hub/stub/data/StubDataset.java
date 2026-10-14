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
import java.util.Comparator;
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

    private static final Comparator<Session> NEWEST_FIRST =
            Comparator.comparing(Session::createdAt).reversed();

    public Optional<Workspace> workspace(String workspaceId) {
        return workspaces.stream()
                .filter(workspace -> workspace.id().equals(workspaceId))
                .findFirst();
    }

    /**
     * A project within its workspace. Like the real hub, the stub resolves nothing below a
     * workspace without it: a request that leaves out the workspace finds no project.
     */
    public Optional<Project> project(String workspaceId, String projectId) {
        return workspace(workspaceId).stream()
                .flatMap(workspace -> workspace.projects().stream())
                .filter(project -> project.id().equals(projectId))
                .findFirst();
    }

    /**
     * @return the project's sessions newest first, the order the hub lists them in
     */
    public List<Session> sessionsForProject(String workspaceId, String projectId) {
        return project(workspaceId, projectId)
                .map(project -> project.instances().stream()
                        .flatMap(instance -> instance.sessions().stream())
                        .sorted(NEWEST_FIRST)
                        .toList())
                .orElse(List.of());
    }

    public Optional<Session> session(String workspaceId, String projectId, String sessionId) {
        return sessionsForProject(workspaceId, projectId).stream()
                .filter(session -> session.id().equals(sessionId))
                .findFirst();
    }

    /**
     * @param id the workspace's directory name on the hub's volume, which is also its reference id
     */
    public record Workspace(
            String id,
            String name,
            Instant createdAt,
            List<Project> projects) {
    }

    public record Project(
            String id,
            String name,
            Instant createdAt,
            String workspaceId,
            RecState status,
            List<Instance> instances) {

        public int sessionCount() {
            return instances.stream().mapToInt(instance -> instance.sessions().size()).sum();
        }

        public long sizeBytes() {
            return instances.stream()
                    .flatMap(instance -> instance.sessions().stream())
                    .mapToLong(Session::sizeBytes)
                    .sum();
        }
    }

    public record Instance(
            String id,
            String name,
            InstState status,
            Instant createdAt,
            Instant finishedAt,
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

        public long sizeBytes() {
            return files.stream().mapToLong(File::size).sum();
        }
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
        PENDING, ACTIVE, FINISHED
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
