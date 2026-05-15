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
package cafe.jeffrey.profile.heapdump.parser;

/**
 * Tunable options for the HPROF index build.
 *
 * <ul>
 *   <li>{@code stringContentThreshold} — maximum decoded character length
 *       of a {@code java.lang.String} whose content is materialised into the
 *       {@code string_content} table; {@code -1} means unlimited.</li>
 *   <li>{@code walkWorkers} — virtual-thread fanout for Pass B (the fused
 *       instance/ref/root walk) and for {@code write_string_content}. Clamped
 *       at runtime to the number of HPROF regions / String-id ranges so we
 *       never spin up more workers than there is work to partition.</li>
 * </ul>
 */
public record BuildOptions(int stringContentThreshold, int walkWorkers) {

    public static final int DEFAULT_STRING_CONTENT_THRESHOLD = 4096;

    public static final int DEFAULT_WALK_WORKERS = 4;

    public BuildOptions {
        if (walkWorkers < 1) {
            throw new IllegalArgumentException("walkWorkers must be >= 1: walkWorkers=" + walkWorkers);
        }
    }

    public static BuildOptions defaults() {
        return new BuildOptions(DEFAULT_STRING_CONTENT_THRESHOLD, DEFAULT_WALK_WORKERS);
    }
}
