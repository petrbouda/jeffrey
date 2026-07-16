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

package cafe.jeffrey.microscope.core.web.controllers.profile.pprof;

import cafe.jeffrey.profile.common.pprof.PprofEventCategory;
import cafe.jeffrey.profile.model.EventSummaryResult;

import java.util.List;

/**
 * The pprof flamegraph event-summary shape: the generic {@link EventSummaryResult} plus the logical
 * {@link PprofEventCategory} the pprof event type maps to. The category is what the UI groups
 * flamegraph cards by, so the pprof-vs-JFR event-type difference (a CPU profile is {@code pprof.cpu},
 * not {@code jdk.ExecutionSample}) is resolved here, on the backend, rather than in the client.
 */
public record PprofEventSummaryResult(
        String code,
        String label,
        String category,
        EventSummaryResult.SingleResult primary,
        EventSummaryResult.SingleResult secondary) {

    public static PprofEventSummaryResult from(EventSummaryResult result) {
        return new PprofEventSummaryResult(
                result.code(),
                result.label(),
                PprofEventCategory.resolve(result.code()).name(),
                result.primary(),
                result.secondary());
    }

    public static List<PprofEventSummaryResult> from(List<EventSummaryResult> results) {
        return results.stream()
                .map(PprofEventSummaryResult::from)
                .toList();
    }
}
