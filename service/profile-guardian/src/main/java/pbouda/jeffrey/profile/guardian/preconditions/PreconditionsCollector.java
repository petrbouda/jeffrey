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

package pbouda.jeffrey.profile.guardian.preconditions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.common.Collector;
import pbouda.jeffrey.common.EventSource;

import java.util.function.Supplier;

public class PreconditionsCollector implements Collector<GuardRecordingInformationBuilder, GuardRecordingInformation> {

    private static final Logger LOG = LoggerFactory.getLogger(PreconditionsCollector.class);

    @Override
    public Supplier<GuardRecordingInformationBuilder> empty() {
        return GuardRecordingInformationBuilder::new;
    }

    @Override
    public GuardRecordingInformationBuilder combiner(GuardRecordingInformationBuilder p1, GuardRecordingInformationBuilder p2) {
        if (p1.getDebugSymbolsAvailable() != null) {
            if (diff(p1.getDebugSymbolsAvailable(), p2.getDebugSymbolsAvailable())) {
                LOG.warn("Debug symbols are not consistent between the preconditions: {} and {}", p1, p2);
            }
        } else {
            p1.setDebugSymbolsAvailable(p2.getDebugSymbolsAvailable());
        }

        if (p1.getKernelSymbolsAvailable() != null) {
            if (diff(p1.getKernelSymbolsAvailable(), p2.getKernelSymbolsAvailable())) {
                LOG.warn("Kernel symbols are not consistent between the preconditions: {} and {}", p1, p2);
            }
        } else {
            p1.setKernelSymbolsAvailable(p2.getKernelSymbolsAvailable());
        }

        // Prefer AsyncProfiler over JDK
        // There are multiple Recordings and one of the is from AsyncProfiler
        // It's caused by using `jfrsync` option in AsyncProfiler
        if (p1.getEventSource() == EventSource.ASYNC_PROFILER || p2.getEventSource() == EventSource.ASYNC_PROFILER) {
            p1.setEventSource(EventSource.ASYNC_PROFILER);
        } else if (p1.getEventSource() == null) {
            p1.setEventSource(p2.getEventSource());
        }

        if (p1.getGarbageCollectorType() != null) {
            if (diff(p1.getGarbageCollectorType(), p2.getGarbageCollectorType())) {
                LOG.warn("GC Type is not consistent between the preconditions: {} and {}", p1, p2);
            }
        } else {
            p1.setGarbageCollectorType(p2.getGarbageCollectorType());
        }

        return p1;
    }

    private static boolean diff(Object p1, Object p2) {
        return p1 != null && p2 != null && !p1.equals(p2);
    }

    @Override
    public GuardRecordingInformation finisher(GuardRecordingInformationBuilder combined) {
        return combined.build();
    }
}
