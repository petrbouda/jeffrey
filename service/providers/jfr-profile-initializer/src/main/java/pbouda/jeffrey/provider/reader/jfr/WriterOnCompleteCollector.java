/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.provider.reader.jfr;

import pbouda.jeffrey.common.model.ProfileInfo;
import pbouda.jeffrey.jfrparser.jdk.Collector;
import pbouda.jeffrey.provider.api.EventWriter;

import java.util.function.Supplier;

public class WriterOnCompleteCollector implements Collector<Void, ProfileInfo> {

    private final EventWriter eventWriter;

    public WriterOnCompleteCollector(EventWriter eventWriter) {
        this.eventWriter = eventWriter;
    }

    @Override
    public Supplier<Void> empty() {
        return null;
    }

    @Override
    public Void combiner(Void partial1, Void partial2) {
        return null;
    }

    @Override
    public ProfileInfo finisher(Void combined) {
        return eventWriter.onComplete();
    }
}
