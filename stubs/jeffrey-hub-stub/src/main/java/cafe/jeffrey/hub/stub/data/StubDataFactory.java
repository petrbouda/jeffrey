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

import cafe.jeffrey.hub.stub.data.StubDataset.Event;
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
    private static final String EVENT_PROJECT_CREATED = "PROJECT_CREATED";
    private static final String EVENT_SESSION_STARTED = "SESSION_STARTED";
    private static final String EVENT_SESSION_FINISHED = "SESSION_FINISHED";
    private static final String CREATED_BY_CLI = "jeffrey-cli";
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
        String workspaceId = "ws-production";
        String referenceId = "production";

        Project checkout = project(workspaceId, "proj-checkout", "checkout-service", "payments", now, List.of(
                finishedInstance("inst-checkout-blue", "checkout-service@blue", now, Duration.ofHours(6), 2),
                activeInstance("inst-checkout-green", "checkout-service@green", now, Duration.ofMinutes(40))));

        Project inventory = project(workspaceId, "proj-inventory", "inventory-service", "warehouse", now, List.of(
                finishedInstance("inst-inventory-1", "inventory-service@1", now, Duration.ofHours(30), 3),
                expiredInstance("inst-inventory-old", "inventory-service@old", now, Duration.ofDays(9))));

        List<Project> projects = List.of(checkout, inventory);
        return new Workspace(workspaceId, "Production", referenceId,
                minus(now, Duration.ofDays(45)), projects, eventsFor(referenceId, projects, now));
    }

    private Workspace stagingWorkspace(Instant now) {
        String workspaceId = "ws-staging";
        String referenceId = "staging";

        Project gateway = project(workspaceId, "proj-gateway", "api-gateway", null, now, List.of(
                activeInstance("inst-gateway-1", "api-gateway@1", now, Duration.ofMinutes(12))));

        List<Project> projects = List.of(gateway);
        return new Workspace(workspaceId, "Staging", referenceId,
                minus(now, Duration.ofDays(20)), projects, eventsFor(referenceId, projects, now));
    }

    private Workspace developmentWorkspace(Instant now) {
        String workspaceId = "ws-development";
        String referenceId = "development";

        Project sandbox = project(workspaceId, "proj-sandbox", "sandbox-app", "experiments", now, List.of(
                finishedInstance("inst-sandbox-1", "sandbox-app@1", now, Duration.ofHours(2), 1),
                pendingInstance("inst-sandbox-2", "sandbox-app@2", now)));

        Project deleted = new Project("proj-legacy", "origin-legacy", "legacy-batch", "legacy-batch", null,
                minus(now, Duration.ofDays(60)), workspaceId, RecState.FINISHED,
                minus(now, Duration.ofDays(3)), List.of());

        List<Project> projects = List.of(sandbox, deleted);
        return new Workspace(workspaceId, "Development", referenceId,
                minus(now, Duration.ofDays(60)), projects, eventsFor(referenceId, projects, now));
    }

    private Project project(String workspaceId, String id, String name, String namespace,
                            Instant now, List<Instance> instances) {
        boolean anyActive = instances.stream().anyMatch(instance -> instance.status() == InstState.ACTIVE);
        return new Project(id, "origin-" + id, name, name, namespace,
                minus(now, Duration.ofDays(40)), workspaceId,
                anyActive ? RecState.ACTIVE : RecState.FINISHED, null, instances);
    }

    private Instance activeInstance(String id, String name, Instant now, Duration startedAgo) {
        Instant createdAt = minus(now, startedAgo);
        Session active = activeSession(id, "sess-" + id + "-current", createdAt);
        return new Instance(id, name, InstState.ACTIVE, createdAt, null,
                minus(now, startedAgo.minus(Duration.ofDays(14))), null, active.id(), List.of(active));
    }

    private Instance finishedInstance(String id, String name, Instant now, Duration startedAgo, int sessionCount) {
        Instant createdAt = minus(now, startedAgo);
        List<Session> sessions = new ArrayList<>();
        for (int i = 0; i < sessionCount; i++) {
            Instant sessionStart = createdAt.plus(Duration.ofMinutes(20L * i));
            sessions.add(finishedSession(id, "sess-" + id + "-" + (i + 1), sessionStart, Duration.ofMinutes(15)));
        }
        Instant finishedAt = sessions.getLast().finishedAt();
        return new Instance(id, name, InstState.FINISHED, createdAt, finishedAt,
                finishedAt.plus(Duration.ofDays(14)), null, null, List.copyOf(sessions));
    }

    private Instance expiredInstance(String id, String name, Instant now, Duration startedAgo) {
        Instant createdAt = minus(now, startedAgo);
        Session session = finishedSession(id, "sess-" + id + "-1", createdAt, Duration.ofMinutes(25));
        Instant finishedAt = session.finishedAt();
        return new Instance(id, name, InstState.EXPIRED, createdAt, finishedAt,
                finishedAt.plus(Duration.ofDays(7)), finishedAt.plus(Duration.ofDays(7)), null, List.of(session));
    }

    private Instance pendingInstance(String id, String name, Instant now) {
        Instant createdAt = minus(now, Duration.ofMinutes(2));
        return new Instance(id, name, InstState.PENDING, createdAt, null,
                createdAt.plus(Duration.ofDays(14)), null, null, List.of());
    }

    private Session activeSession(String instanceId, String sessionId, Instant createdAt) {
        List<File> files = List.of(
                file(sessionId, "recording-live.jfr", createdAt, 18 * MEGABYTE, FileKind.JFR, RecState.ACTIVE),
                file(sessionId, "gc-jvm.log", createdAt, 512 * 1024L, FileKind.GC_LOG, RecState.ACTIVE));
        return new Session(sessionId, "repo-" + instanceId, "Live recording", instanceId,
                createdAt, null, true, RecState.ACTIVE, files);
    }

    private Session finishedSession(String instanceId, String sessionId, Instant createdAt, Duration length) {
        Instant finishedAt = createdAt.plus(length);
        List<File> files = new ArrayList<>();
        files.add(file(sessionId, "recording.jfr", createdAt, 42 * MEGABYTE, FileKind.JFR, RecState.FINISHED));
        files.add(file(sessionId, "gc-jvm.log", createdAt, 768 * 1024L, FileKind.GC_LOG, RecState.FINISHED));
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

    private List<Event> eventsFor(String workspaceRefId, List<Project> projects, Instant now) {
        List<Event> events = new ArrayList<>();
        for (Project project : projects) {
            events.add(event(project.id(), workspaceRefId, EVENT_PROJECT_CREATED,
                    "{\"projectName\":\"" + project.name() + "\"}",
                    project.createdAt(), CREATED_BY_CLI));
            for (Instance instance : project.instances()) {
                for (Session session : instance.sessions()) {
                    events.add(event(project.id(), workspaceRefId, EVENT_SESSION_STARTED,
                            "{\"sessionId\":\"" + session.id() + "\"}",
                            session.createdAt(), CREATED_BY_SYSTEM));
                    if (session.finishedAt() != null) {
                        events.add(event(project.id(), workspaceRefId, EVENT_SESSION_FINISHED,
                                "{\"sessionId\":\"" + session.id() + "\"}",
                                session.finishedAt(), CREATED_BY_SYSTEM));
                    }
                }
            }
        }
        // Latest first by created_at, matching the real server contract.
        events.sort((left, right) -> right.createdAt().compareTo(left.createdAt()));
        return List.copyOf(events);
    }

    private Event event(String projectId, String workspaceRefId, String type, String content,
                        Instant occurredAt, String createdBy) {
        long id = eventSequence.getAndIncrement();
        return new Event(id, "origin-evt-" + id, projectId, workspaceRefId, type, content,
                occurredAt, occurredAt, createdBy);
    }

    private static Instant minus(Instant now, Duration duration) {
        return now.minus(duration);
    }
}
