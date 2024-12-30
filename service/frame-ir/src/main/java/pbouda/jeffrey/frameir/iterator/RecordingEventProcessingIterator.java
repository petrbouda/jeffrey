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

package pbouda.jeffrey.frameir.iterator;

import pbouda.jeffrey.common.Collector;
import pbouda.jeffrey.jfrparser.api.EventProcessor;
import pbouda.jeffrey.jfrparser.jdk.JdkRecordingIterators;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;

public class RecordingEventProcessingIterator implements EventProcessingIterator {

    private final List<Path> recordings;

    public RecordingEventProcessingIterator(List<Path> recordings) {
        this.recordings = recordings;
    }

    @Override
    public <PARTIAL, RESULT> RESULT iterate(
            Supplier<? extends EventProcessor<PARTIAL>> processorSupplier,
            Collector<PARTIAL, RESULT> collector) {

        return JdkRecordingIterators.automaticAndCollect(recordings, processorSupplier, collector);
    }
}
