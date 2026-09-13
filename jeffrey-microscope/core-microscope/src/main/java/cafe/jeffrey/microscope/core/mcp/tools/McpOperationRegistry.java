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
package cafe.jeffrey.microscope.core.mcp.tools;

import cafe.jeffrey.profile.common.operation.OperationHandle;
import cafe.jeffrey.profile.common.operation.OperationSnapshot;
import cafe.jeffrey.profile.common.operation.OperationState;
import cafe.jeffrey.profile.mcp.McpToolOutput;
import cafe.jeffrey.shared.common.Json;
import tools.jackson.databind.node.ObjectNode;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** Process-local catalogue of existing workers. This class never schedules or starts work. */
public final class McpOperationRegistry {

    public static final Duration RETENTION = Duration.ofHours(1);
    private final Map<String, Entry<?>> entries = new ConcurrentHashMap<>();
    private final Clock clock;
    private final AtomicLong registrationSequence = new AtomicLong();

    public McpOperationRegistry(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public McpOperationRegistry() {
        this(Clock.systemUTC());
    }

    public <V> String register(String kind, OperationHandle<V> handle, Function<V, Object> result) {
        return register(kind, handle, result, null);
    }

    public <V> String register(String kind, OperationHandle<V> handle, Function<V, Object> result,
                               Supplier<String> recordingIdentity) {
        return registerIfRetained(kind, handle, result, recordingIdentity)
                .orElseThrow(() -> new IllegalArgumentException("Operation retention has expired"));
    }

    public <V> Optional<String> registerIfRetained(String kind, OperationHandle<V> handle, Function<V, Object> result) {
        return registerIfRetained(kind, handle, result, null);
    }

    private <V> Optional<String> registerIfRetained(String kind, OperationHandle<V> handle,
                                                   Function<V, Object> result, Supplier<String> recordingIdentity) {
        evictExpired();
        Instant finished = handle.finishedAt();
        if (finished != null && finished.isBefore(clock.instant().minus(RETENTION))) {
            return Optional.empty();
        }
        String id = handle.operationId();
        entries.putIfAbsent(id, new Entry<>(kind, handle, result, recordingIdentity, registrationSequence.incrementAndGet()));
        return Optional.of(id);
    }

    public Optional<String> latestForRecording(String recordingId) {
        evictExpired();
        return entries.values().stream()
                .filter(entry -> entry.kind.startsWith("recording_"))
                .filter(entry -> entry.recordingIdentity != null
                        && recordingId.equals(entry.recordingIdentity.get()))
                .max(Comparator.<Entry<?>, Instant>comparing(entry -> entry.startedAt)
                        .thenComparingLong(entry -> entry.registrationSequence)).map(entry -> entry.operationId);
    }

    public Snapshot status(String operationId) {
        return status(operationId, kind -> true);
    }

    public Snapshot status(String operationId, Predicate<String> allowedKind) {
        return require(operationId, allowedKind).snapshot();
    }

    public Snapshot cancel(String operationId, Predicate<String> allowedKind) {
        Entry<?> entry = require(operationId, allowedKind);
        entry.cancel();
        return entry.snapshot(false);
    }

    /** Adds the same canonical snapshot to existing family responses without renaming old fields. */
    public String decorate(String legacyJson, String operationId) {
        ObjectNode json = (ObjectNode) Json.mapper().readTree(legacyJson);
        json.put("operationId", operationId);
        json.set("operation", Json.mapper().valueToTree(status(operationId)));
        return McpToolOutput.json(json);
    }

    private Entry<?> require(String id, Predicate<String> allowedKind) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("operationId is required");
        }
        evictExpired();
        Entry<?> entry = entries.get(id.trim());
        if (entry == null || !allowedKind.test(entry.kind)) {
            throw new IllegalArgumentException("Unknown or expired operation: " + id
                    + ". Operations are process-local and retained for one hour after completion.");
        }
        return entry;
    }

    private void evictExpired() {
        Instant cutoff = clock.instant().minus(RETENTION);
        entries.values().removeIf(entry -> {
            Instant finished = entry.finishedAt();
            return finished != null && finished.isBefore(cutoff);
        });
    }

    public record Snapshot(
            String operationId, String kind, String status,
            Instant startedAt, Instant finishedAt, boolean cancellationRequested,
            boolean retryable, Progress progress, Object result, Failure error, List<String> nextSteps) {
    }

    public record Progress(String phase, Object details) {
    }

    public record Failure(String code, String message) {
    }

    private static final class Entry<V> {
        private final String kind;
        private final String operationId;
        private final long registrationSequence;
        private final Instant startedAt;
        private final Supplier<String> recordingIdentity;
        private volatile OperationHandle<V> handle;
        private Function<V, Object> renderer;
        private volatile Snapshot terminal;

        private Entry(String kind, OperationHandle<V> handle, Function<V, Object> renderer,
                      Supplier<String> recordingIdentity, long registrationSequence) {
            this.registrationSequence = registrationSequence;
            this.kind = kind;
            this.operationId = handle.operationId();
            this.startedAt = handle.startedAt();
            this.recordingIdentity = recordingIdentity;
            this.handle = handle;
            this.renderer = renderer;
        }

        private Instant finishedAt() {
            OperationHandle<V> current = handle;
            return current == null ? terminal.finishedAt() : current.finishedAt();
        }

        private void cancel() {
            OperationHandle<V> current = handle;
            if (current != null) {
                current.cancel();
            }
        }

        private Snapshot snapshot() {
            return snapshot(true);
        }

        private Snapshot snapshot(boolean includeProgress) {
            OperationHandle<V> current;
            Function<V, Object> currentRenderer;
            synchronized (this) {
                if (terminal != null) {
                    return terminal;
                }
                current = handle;
                currentRenderer = renderer;
            }
            OperationSnapshot<V> source = includeProgress ? current.snapshot() : current.lifecycleSnapshot();
            boolean retryable = source.state() == OperationState.FAILED || source.state() == OperationState.CANCELLED;
            Failure error = source.failure() == null ? null : new Failure(
                    source.state() == OperationState.CANCELLED ? "CANCELLED" : "OPERATION_FAILED",
                    source.failure().getMessage());
            List<String> nextSteps;
            if (!source.state().terminal()) {
                nextSteps = List.of("Poll operations_status with this operationId. A cancellation request remains pending until the worker exits.");
            } else if (retryable) {
                nextSteps = List.of(retryInstruction(kind),
                        "Cancellation does not roll back files or reports already written.");
            } else {
                nextSteps = List.of("The operation completed. Its status is retained in this process for one hour.");
            }
            Snapshot snapshot = new Snapshot(source.operationId(), kind, source.state().code(),
                    source.startedAt(), source.finishedAt(), source.cancellationRequested(), retryable,
                    new Progress(source.phase(), source.progress()),
                    source.result() == null ? null : currentRenderer.apply(source.result()), error, nextSteps);
            synchronized (this) {
                if (terminal != null) {
                    return terminal;
                }
                if (includeProgress && source.state().terminal()) {
                    terminal = snapshot;
                    handle = null;
                    renderer = null;
                }
                return snapshot;
            }
        }

        private static String retryInstruction(String kind) {
            return switch (kind) {
                case "recording_import" -> "If progress contains a recordingId, call recordings_analyzeRecording with retry=true; otherwise call recordings_analyzeFile again to start a new import.";
                case "recording_analysis" -> "Call recordings_analyzeRecording with the same recordingId and retry=true to start a new attempt.";
                case "hub_download" -> "Call hubs_download with the same sessionRef and retry=true to start a new attempt.";
                case "heap_prepare" -> "Call heap_prepare for the same profile/report with retry=true to start a new attempt.";
                default -> "Start a new attempt explicitly using the originating tool.";
            };
        }
    }
}
