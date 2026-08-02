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

/**
 * Intermediate phase callbacks {@link AdvisorService} uses to report progress. Kept free of any web
 * types so the service stays framework-agnostic; {@link AdvisorRun} implements it. The terminal
 * completed/failed transitions are driven by {@link AdvisorRunner} from the service's return value (or
 * thrown exception), not through this sink.
 */
public interface AdvisorProgressSink {

    /** The flamegraph prompt is being built, or the cached one loaded. */
    void preparingPrompt();

    /** The configured source folder is being validated. */
    void resolvingSource();

    /** The source folder is ready; the model is reviewing the source. */
    void reviewing();

    /** The model has answered; its cited frames are being checked against the measured call tree. */
    void verifying();

    /** The claims are settled; the diff the model proposed is being built into an applicable patch. */
    void buildingPatch();

    /**
     * True once the run has ended without the work knowing — cancelled from the UI, or failed.
     *
     * <p>Cancellation marks the run and interrupts its worker, but an HTTP call already in flight may
     * swallow the interrupt and return normally, so the thread's own flag is not something the service
     * can rely on. This reads the run's state instead, which the cancel sets synchronously.</p>
     */
    boolean ended();
}
