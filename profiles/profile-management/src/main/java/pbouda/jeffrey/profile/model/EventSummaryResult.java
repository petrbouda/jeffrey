/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

package pbouda.jeffrey.profile.model;


import pbouda.jeffrey.shared.common.model.EventSummary;

import java.util.Map;

public record EventSummaryResult(String code, String label, SingleResult primary, SingleResult secondary) {

    public EventSummaryResult(EventSummary primary) {
        this(primary.name(), primary.label(), new SingleResult(primary), null);
    }

    public EventSummaryResult(EventSummary primary, EventSummary secondary) {
        this(primary.name(), primary.label(), new SingleResult(primary), new SingleResult(secondary));
    }

    public record SingleResult(
            String code,
            String label,
            String source,
            String subtype,
            long samples,
            long weight,
            boolean calculated,
            Map<String, String> extras) {

        public SingleResult(EventSummary eventSummary) {
            this(eventSummary.name(),
                    eventSummary.label(),
                    eventSummary.source() != null ? eventSummary.source().getLabel() : null,
                    eventSummary.subtype() != null ? eventSummary.subtype().getLabel() : null,
                    eventSummary.samples(),
                    eventSummary.weight(),
                    eventSummary.calculated(),
                    eventSummary.extras() != null ? eventSummary.extras() : Map.of());
        }
    }
}
