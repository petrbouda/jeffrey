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

package cafe.jeffrey.performance.analyst.recommendations;

import cafe.jeffrey.shared.common.IDGenerator;
import cafe.jeffrey.shared.common.model.Severity;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * A single AI recommendation run for one recording + sample event type. Tracks the current
 * {@link RecommendationProgress} and notifies SSE listeners on every transition. Thread-safe: the
 * worker thread drives the phase transitions while SSE request threads subscribe/unsubscribe.
 */
public class RecommendationTask implements RecommendationProgressSink {

    private final String taskId;
    private final String hubId;
    private final String workspaceId;
    private final String projectId;
    private final String recordingId;
    private final String eventType;
    private final Clock clock;
    private final Instant createdAt;

    private final List<Consumer<RecommendationProgress>> listeners = new CopyOnWriteArrayList<>();

    private volatile RecommendationStatus status;
    private volatile String message;
    private volatile Severity severity;
    private volatile String recommendations;
    private volatile String patch;
    private volatile String errorMessage;
    private volatile Instant completedAt;
    private volatile CompletableFuture<Void> future;

    public RecommendationTask(
            String hubId,
            String workspaceId,
            String projectId,
            String recordingId,
            String eventType,
            Clock clock) {
        this.taskId = IDGenerator.generate();
        this.hubId = hubId;
        this.workspaceId = workspaceId;
        this.projectId = projectId;
        this.recordingId = recordingId;
        this.eventType = eventType;
        this.clock = clock;
        this.createdAt = clock.instant();
        this.status = RecommendationStatus.CLONING;
        this.message = "Cloning repository…";
    }

    public String taskId() {
        return taskId;
    }

    public String projectId() {
        return projectId;
    }

    public String recordingId() {
        return recordingId;
    }

    public String eventType() {
        return eventType;
    }

    public void setFuture(CompletableFuture<Void> future) {
        this.future = future;
    }

    public RecommendationProgress currentProgress() {
        return new RecommendationProgress(
                taskId, recordingId, eventType, status, message, severity, recommendations, patch, errorMessage,
                createdAt, completedAt);
    }

    public void addProgressListener(Consumer<RecommendationProgress> listener) {
        listeners.add(listener);
        // Immediately replay the current state to a freshly attached listener.
        listener.accept(currentProgress());
    }

    public void removeProgressListener(Consumer<RecommendationProgress> listener) {
        listeners.remove(listener);
    }

    // --- RecommendationProgressSink (intermediate phases) ---

    @Override
    public void cloning() {
        transition(RecommendationStatus.CLONING, "Cloning repository…");
    }

    @Override
    public void analyzing() {
        transition(RecommendationStatus.ANALYZING, "Analyzing repository…");
    }

    // --- Terminal transitions (driven by the controller) ---

    public void completed(RecommendationResult result) {
        this.severity = result.severity();
        this.recommendations = result.recommendations();
        this.patch = result.patch();
        this.completedAt = clock.instant();
        transition(RecommendationStatus.COMPLETED, "Recommendations ready");
    }

    public void failed(String error) {
        this.errorMessage = error;
        this.completedAt = clock.instant();
        transition(RecommendationStatus.FAILED, "Recommendation generation failed");
    }

    public void cancel() {
        if (future != null) {
            future.cancel(true);
        }
        failed("Cancelled");
    }

    private void transition(RecommendationStatus newStatus, String newMessage) {
        this.status = newStatus;
        this.message = newMessage;
        RecommendationProgress snapshot = currentProgress();
        for (Consumer<RecommendationProgress> listener : listeners) {
            try {
                listener.accept(snapshot);
            } catch (RuntimeException e) {
                // A failing SSE listener must not break the task or other listeners.
                listeners.remove(listener);
            }
        }
    }
}
