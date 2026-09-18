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

/** Payloads that are not tied to a {@link ProvisionerSource}. */
public abstract class Payloads {

    private static final String PROFILER_BASE_NAME = "libasyncProfiler";
    private static final String PROFILER_EXTENSION = ".so";

    /**
     * async-profiler, carried by both payload flavours. It is the one payload that is genuinely
     * native whatever else the image carries, and at around 1 MB per architecture it is the cheap
     * half of the bundle.
     */
    public static final PayloadSpec PROFILER = new PayloadSpec.ArchScoped(
            PROFILER_BASE_NAME, PROFILER_EXTENSION, PayloadPermissions.READABLE);
}
