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

import cafe.jeffrey.hub.stub.data.StubDataset.File;
import cafe.jeffrey.hub.stub.data.StubDataset.FileKind;
import cafe.jeffrey.hub.stub.data.StubDataset.InstState;
import cafe.jeffrey.hub.stub.data.StubDataset.Instance;
import cafe.jeffrey.hub.stub.data.StubDataset.Project;
import cafe.jeffrey.hub.stub.data.StubDataset.RecState;
import cafe.jeffrey.hub.stub.data.StubDataset.Session;
import cafe.jeffrey.hub.stub.data.StubDataset.Workspace;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Builds a deterministic, hand-authored {@link StubDataset} from a {@link Clock}.
 *
 * <p>All timestamps are derived by subtracting fixed offsets from {@code clock.instant()},
 * so the dataset is reproducible for a fixed clock (used in tests) and "recent" for a
 * live clock (used at runtime). No randomness is involved.
 */
public class StubDataFactory {

    private static final long MEGABYTE = 1024L * 1024L;
    private static final String CREATED_BY_PROVISIONER = "jeffrey-provisioner";
    private static final String CREATED_BY_SYSTEM = "system";

    private final Clock clock;
    private final AtomicLong eventSequence = new AtomicLong(1);

    public StubDataFactory(Clock clock) {
        this.clock = clock;
    }

    public StubDataset create() {
        Instant now = clock.instant();
        return new StubDataset(List.of(
                productionWorkspace(now),
                stagingWorkspace(now),
                developmentWorkspace(now)));
    }

    private Workspace productionWorkspace(Instant now) {
        String workspaceId = "production";

        Project checkout = project(workspaceId, "proj-checkout", "checkout-service", now, List.of(
                finishedInstance("inst-checkout-blue", "checkout-service@blue", now, Duration.ofHours(6), 2),
                activeInstance("inst-checkout-green", "checkout-service@green", now, Duration.ofMinutes(40))));

        Project inventory = project(workspaceId, "proj-inventory", "inventory-service", now, List.of(
                finishedInstance("inst-inventory-1", "inventory-service@1", now, Duration.ofHours(30), 3),
                finishedInstance("inst-inventory-old", "inventory-service@old", now, Duration.ofDays(9), 1)));

        List<Project> projects = List.of(checkout, inventory);
        return new Workspace(workspaceId, "Production",
                minus(now, Duration.ofDays(45)), projects);
    }

    private Workspace stagingWorkspace(Instant now) {
        String workspaceId = "staging";

        Project gateway = project(workspaceId, "proj-gateway", "api-gateway", now, List.of(
                activeInstance("inst-gateway-1", "api-gateway@1", now, Duration.ofMinutes(12))));

        List<Project> projects = List.of(gateway);
        return new Workspace(workspaceId, "Staging",
                minus(now, Duration.ofDays(20)), projects);
    }

    private Workspace developmentWorkspace(Instant now) {
        String workspaceId = "development";

        Project sandbox = project(workspaceId, "proj-sandbox", "sandbox-app", now, List.of(
                finishedInstance("inst-sandbox-1", "sandbox-app@1", now, Duration.ofHours(2), 1),
                pendingInstance("inst-sandbox-2", "sandbox-app@2", now)));

        List<Project> projects = List.of(sandbox);
        return new Workspace(workspaceId, "Development",
                minus(now, Duration.ofDays(60)), projects);
    }

    private Project project(String workspaceId, String id, String name, Instant now, List<Instance> instances) {
        boolean anyActive = instances.stream().anyMatch(instance -> instance.status() == InstState.ACTIVE);
        return new Project(id, name, minus(now, Duration.ofDays(40)), workspaceId,
                anyActive ? RecState.ACTIVE : RecState.FINISHED, instances);
    }

    private Instance activeInstance(String id, String name, Instant now, Duration startedAgo) {
        Instant createdAt = minus(now, startedAgo);
        Session active = activeSession(id, "sess-" + id + "-current", createdAt);
        return new Instance(id, name, InstState.ACTIVE, createdAt, null, active.id(), List.of(active));
    }

    private Instance finishedInstance(String id, String name, Instant now, Duration startedAgo, int sessionCount) {
        Instant createdAt = minus(now, startedAgo);
        List<Session> sessions = new ArrayList<>();
        for (int i = 0; i < sessionCount; i++) {
            Instant sessionStart = createdAt.plus(Duration.ofMinutes(20L * i));
            sessions.add(finishedSession(id, "sess-" + id + "-" + (i + 1), sessionStart, Duration.ofMinutes(15)));
        }
        Instant finishedAt = sessions.getLast().finishedAt();
        return new Instance(id, name, InstState.FINISHED, createdAt, finishedAt, null, List.copyOf(sessions));
    }

    private Instance pendingInstance(String id, String name, Instant now) {
        Instant createdAt = minus(now, Duration.ofMinutes(2));
        return new Instance(id, name, InstState.PENDING, createdAt, null, null, List.of());
    }

    private Session activeSession(String instanceId, String sessionId, Instant createdAt) {
        List<File> files = List.of(
                file(sessionId, "recording-live.jfr", createdAt, 18 * MEGABYTE, FileKind.JFR, RecState.ACTIVE),
                file(sessionId, "gc.jvm-log", createdAt, 512 * 1024L, FileKind.GC_LOG, RecState.ACTIVE));
        return new Session(sessionId, "repo-" + instanceId, "Live recording", instanceId,
                createdAt, null, true, RecState.ACTIVE, files);
    }

    private Session finishedSession(String instanceId, String sessionId, Instant createdAt, Duration length) {
        Instant finishedAt = createdAt.plus(length);
        List<File> files = new ArrayList<>();
        files.add(file(sessionId, "recording.jfr", createdAt, 42 * MEGABYTE, FileKind.JFR, RecState.FINISHED));
        files.add(file(sessionId, "gc.jvm-log", createdAt, 768 * 1024L, FileKind.GC_LOG, RecState.FINISHED));
        files.add(file(sessionId, "application-app.log", createdAt, 1280 * 1024L, FileKind.APP_LOG, RecState.FINISHED));
        if (sessionId.endsWith("-1")) {
            files.add(file(sessionId, "heapdump.hprof", finishedAt, 256 * MEGABYTE, FileKind.HEAP_DUMP, RecState.FINISHED));
        }
        return new Session(sessionId, "repo-" + instanceId, "Recording " + sessionId, instanceId,
                createdAt, finishedAt, false, RecState.FINISHED, List.copyOf(files));
    }

    private File file(String sessionId, String name, Instant createdAt, long size, FileKind kind, RecState status) {
        return new File("file-" + sessionId + "-" + name, name, createdAt, size, kind, status);
    }

    private static Instant minus(Instant now, Duration duration) {
        return now.minus(duration);
    }
}
