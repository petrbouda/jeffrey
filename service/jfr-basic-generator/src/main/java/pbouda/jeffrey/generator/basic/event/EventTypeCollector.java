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

package pbouda.jeffrey.generator.basic.event;

import jdk.jfr.EventType;

import java.util.Objects;

public class EventTypeCollector {

        private final EventType eventType;
        private final String weightFieldName;

        private long samples = 0L;
        private long weight = 0L;

        public EventTypeCollector(EventType eventType) {
            this(eventType, null);
        }

        public EventTypeCollector(EventType eventType, String weightFieldName) {
            this.eventType = eventType;
            this.weightFieldName = weightFieldName;
        }

        public void incrementSamples() {
            samples++;
        }

        public void incrementWeight(long weight) {
            this.weight += weight;
        }

        public boolean isWeightBased() {
            return weightFieldName != null;
        }

        public String getWeightFieldName() {
            return weightFieldName;
        }

        public EventSummary buildSummary() {
            return new EventSummary(eventType, samples, isWeightBased() ? weight : -1);
        }

        public EventTypeCollector merge(EventTypeCollector other) {
            this.samples += other.samples;
            this.weight += other.weight;
            return this;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof EventTypeCollector that)) {
                return false;
            }
            return Objects.equals(eventType.getLabel(), that.eventType.getLabel());
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(eventType.getLabel());
        }
    }
