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

package cafe.jeffrey.jib.payload;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * What to bake into one image.
 *
 * <p>The provisioner is not optional and so is not a field: it is Jeffrey's own binary, the
 * protocol it writes is the one Jeffrey Hub reads, and an image that carried someone else's copy
 * would drift from that protocol with nothing recording which one it had. Only async-profiler,
 * a third-party library an image may legitimately already ship, can be left out.
 *
 * @param descriptor    the payload jar found on the class path, which decides the provisioner build
 * @param architectures the Linux architectures the build plan targets, in declaration order
 * @param bakeProfiler  false when the build configuration already names a profiler path; setting
 *                      {@code profilerPath} has always meant "use my async-profiler" and still does
 */
public record PayloadPlan(PayloadDescriptor descriptor, Set<String> architectures, boolean bakeProfiler) {

    public PayloadPlan {
        if (descriptor == null) {
            throw new IllegalArgumentException("Payload descriptor must not be null");
        }
        if (architectures == null || architectures.isEmpty()) {
            throw new IllegalArgumentException("At least one target architecture is required");
        }
        architectures = new LinkedHashSet<>(architectures);
    }
}
