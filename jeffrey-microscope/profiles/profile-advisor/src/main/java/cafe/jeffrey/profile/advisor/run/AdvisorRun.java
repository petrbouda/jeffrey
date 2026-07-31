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

package cafe.jeffrey.profile.advisor.run;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

/**
 * One advisor run for a profile and sample event type. The worker thread drives the phase transitions
 * while request threads read {@link #progress()}; every mutable field is volatile and every read builds
 * a fresh snapshot, so a poll can never observe a half-applied transition.
 *
 * <p>The run holds no result. Once it completes, the artifacts live in the profile database and the UI
 * reads them from there — which means a page opened after the run finished sees exactly what a page
 * that watched it live sees.</p>
 */
public class AdvisorRun implements AdvisorProgressSink {

    private final AdvisorTarget target;
    private final Clock clock;
    private final Instant startedAt;

    private volatile AdvisorStatus status = AdvisorStatus.QUEUED;
    private volatile String message = AdvisorStatus.QUEUED.message();
    private volatile String errorMessage;
    private volatile Instant completedAt;
    private volatile CompletableFuture<Void> future;

    public AdvisorRun(AdvisorTarget target, Clock clock) {
        this.target = target;
        this.clock = clock;
        this.startedAt = clock.instant();
    }

    public AdvisorTarget target() {
        return target;
    }

    public void attach(CompletableFuture<Void> future) {
        this.future = future;
    }

    public AdvisorProgress progress() {
        return new AdvisorProgress(
                target.profileId(), target.eventType(), status, message, errorMessage, startedAt, completedAt);
    }

    public boolean isRunning() {
        return !status.isTerminal();
    }

    @Override
    public void preparingPrompt() {
        transition(AdvisorStatus.PREPARING_PROMPT);
    }

    @Override
    public void resolvingSource() {
        transition(AdvisorStatus.RESOLVING_SOURCE);
    }

    @Override
    public void analyzing() {
        transition(AdvisorStatus.ANALYZING);
    }

    @Override
    public void verifying() {
        transition(AdvisorStatus.VERIFYING);
    }

    public void completed() {
        this.completedAt = clock.instant();
        transition(AdvisorStatus.COMPLETED);
    }

    public void failed(String error) {
        this.errorMessage = error;
        this.completedAt = clock.instant();
        transition(AdvisorStatus.FAILED);
    }

    public void cancel() {
        if (future != null) {
            future.cancel(true);
        }
        failed("Cancelled");
    }

    private void transition(AdvisorStatus newStatus) {
        this.status = newStatus;
        this.message = newStatus.message();
    }
}
