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

package pbouda.jeffrey.model;

import pbouda.jeffrey.generator.basic.event.EventSummary;

import java.util.Map;

public record EventSummaryResult(String code, String label, SingleResult primary, SingleResult secondary) {

    public EventSummaryResult(EventSummary primary) {
        this(primary.eventType().getName(),
                primary.eventType().getLabel(),
                new SingleResult(primary),
                null);
    }

    public EventSummaryResult(EventSummary primary, EventSummary secondary) {
        this(primary.eventType().getName(),
                primary.eventType().getLabel(),
                new SingleResult(primary),
                new SingleResult(secondary));
    }

    public record SingleResult(String code, String label, long samples, long weight, Map<String, Object> extras) {

        public SingleResult(EventSummary eventSummary) {
            this(eventSummary.eventType().getName(),
                    eventSummary.eventType().getLabel(),
                    eventSummary.samples(),
                    eventSummary.weight(),
                    eventSummary.extras());
        }
    }
}
