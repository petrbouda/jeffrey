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
 * @param source          which provisioner build to install
 * @param architectures   the Linux architectures the build plan targets, in declaration order
 * @param bakeProvisioner false when the build configuration already names a provisioner path, in
 *                        which case the operator has put one in the base image themselves and
 *                        baking a second copy would only cost image size
 * @param bakeProfiler    false when the build configuration already names a profiler path; setting
 *                        {@code profilerPath} has always meant "use my async-profiler" and still does
 */
public record PayloadPlan(
        ProvisionerSource source,
        Set<String> architectures,
        boolean bakeProvisioner,
        boolean bakeProfiler) {

    public PayloadPlan {
        if (source == null) {
            throw new IllegalArgumentException("Provisioner source must not be null");
        }
        if (architectures == null || architectures.isEmpty()) {
            throw new IllegalArgumentException("At least one target architecture is required");
        }
        architectures = new LinkedHashSet<>(architectures);
    }

    /** Whether anything at all is baked; an image may legitimately supply both paths itself. */
    public boolean bakesAnything() {
        return bakeProvisioner || bakeProfiler;
    }
}
