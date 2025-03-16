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

import pbouda.jeffrey.common.model.EventSource;
import pbouda.jeffrey.common.GarbageCollectorType;

public class GuardianInformationBuilder {
    private Boolean debugSymbolsAvailable;
    private Boolean kernelSymbolsAvailable;
    private EventSource eventSource;
    private GarbageCollectorType garbageCollectorType;

    public Boolean getDebugSymbolsAvailable() {
        return debugSymbolsAvailable;
    }

    public void setDebugSymbolsAvailable(Boolean debugSymbolsAvailable) {
        this.debugSymbolsAvailable = debugSymbolsAvailable;
    }

    public Boolean getKernelSymbolsAvailable() {
        return kernelSymbolsAvailable;
    }

    public void setKernelSymbolsAvailable(Boolean kernelSymbolsAvailable) {
        this.kernelSymbolsAvailable = kernelSymbolsAvailable;
    }

    public EventSource getEventSource() {
        return eventSource;
    }

    public void setEventSource(EventSource eventSource) {
        this.eventSource = eventSource;
    }

    public GarbageCollectorType getGarbageCollectorType() {
        return garbageCollectorType;
    }

    public void setGarbageCollectorType(GarbageCollectorType garbageCollectorType) {
        this.garbageCollectorType = garbageCollectorType;
    }

    public boolean isCompleted() {
        return debugSymbolsAvailable != null
                && kernelSymbolsAvailable != null
                && eventSource != null
                && garbageCollectorType != null;
    }

    public GuardianInformation build() {
        return new GuardianInformation(
                debugSymbolsAvailable != null ? debugSymbolsAvailable : false,
                kernelSymbolsAvailable != null ? kernelSymbolsAvailable : false,
                eventSource,
                garbageCollectorType);
    }
}
