/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

package cafe.jeffrey.profile.model;


import cafe.jeffrey.microscope.model.EventSummary;

import java.util.Map;

public record EventSummaryResult(
        String code, String label, SingleResult primary, SingleResult secondary) {

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
            Map<String, String> extras,
            Map<String, String> settings) {

        public SingleResult {
            settings = settings == null ? Map.of() : Map.copyOf(settings);
        }

        public SingleResult(String code, String label, String source, String subtype, long samples,
                            long weight, boolean calculated, Map<String, String> extras) {
            this(code, label, source, subtype, samples, weight, calculated, extras, Map.of());
        }

        public SingleResult(EventSummary eventSummary) {
            this(eventSummary.name(),
                    eventSummary.label(),
                    eventSummary.source() != null ? eventSummary.source().getLabel() : null,
                    eventSummary.subtype() != null ? eventSummary.subtype().getLabel() : null,
                    eventSummary.samples(),
                    eventSummary.weight(),
                    eventSummary.calculated(),
                    eventSummary.extras() != null ? eventSummary.extras() : Map.of(),
                    eventSummary.settings());
        }
    }
}
