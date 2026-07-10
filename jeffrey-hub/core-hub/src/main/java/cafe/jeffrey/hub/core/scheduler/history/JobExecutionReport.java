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

package cafe.jeffrey.hub.core.scheduler.history;

/**
 * Per-run collector that jobs write human-readable execution details into: a short
 * summary line ("Purged 2 soft-deleted projects") and individual detail items
 * ("Purged project: legacy-billing"). The scheduler creates a fresh collecting
 * instance for every run; jobs executed outside the scheduler see a no-op instance,
 * so reporting is always safe to call.
 */
public sealed interface JobExecutionReport permits CollectingJobExecutionReport, NoopJobExecutionReport {

    /**
     * Sets the one-line summary of what the run did. The last write wins.
     */
    void summary(String summary);

    /**
     * Appends one detail item, e.g. the name of a deleted project or a processed event.
     */
    void item(String item);

    /**
     * Appends one detail item describing a partial failure AND marks the whole run
     * as failed (the run keeps executing; the failure only affects the recorded status).
     */
    void failure(String item);
}
