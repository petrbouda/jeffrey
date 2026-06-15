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

package cafe.jeffrey.profile.guardian.definition;

import cafe.jeffrey.profile.common.event.GarbageCollectorType;
import cafe.jeffrey.profile.guardian.preconditions.Preconditions;
import cafe.jeffrey.profile.guardian.preconditions.PreconditionsBuilder;
import cafe.jeffrey.shared.common.model.RecordingEventSource;

/**
 * Declares the conditions under which a configurable guard is applicable. Every field is optional
 * (null = "don't care"). Mirrors the subset of {@link Preconditions} that shipped guards declared in
 * code — recording event source, garbage collector type, and symbol availability.
 */
public record GuardPreconditions(
        RecordingEventSource eventSource,
        GarbageCollectorType garbageCollectorType,
        Boolean debugSymbolsAvailable,
        Boolean kernelSymbolsAvailable) {

    public static final GuardPreconditions NONE = new GuardPreconditions(null, null, null, null);

    /** Translates this declaration into a runtime {@link Preconditions} instance for matching. */
    public Preconditions toPreconditions() {
        PreconditionsBuilder builder = new PreconditionsBuilder();
        if (eventSource != null) {
            builder.withEventSource(eventSource);
        }
        if (garbageCollectorType != null) {
            builder.withGarbageCollectorType(garbageCollectorType);
        }
        if (debugSymbolsAvailable != null) {
            builder.withDebugSymbolsAvailable(debugSymbolsAvailable);
        }
        if (kernelSymbolsAvailable != null) {
            builder.withKernelSymbolsAvailable(kernelSymbolsAvailable);
        }
        return builder.build();
    }
}
