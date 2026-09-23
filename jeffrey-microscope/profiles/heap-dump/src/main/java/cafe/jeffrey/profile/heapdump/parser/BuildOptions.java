/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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

    public static final int MIN_DEFAULT_WALK_WORKERS = 1;

    /**
     * Ceiling for the CPU-derived default: every Pass B worker owns a private
     * in-memory DuckDB with open appenders, so the per-worker memory cost is
     * real and the parquet bulk-load gains flatten out well before this point.
     */
    public static final int MAX_DEFAULT_WALK_WORKERS = 16;

    /**
     * CPU-scaled fanout: one worker per available core, clamped to
     * [{@link #MIN_DEFAULT_WALK_WORKERS}, {@link #MAX_DEFAULT_WALK_WORKERS}].
     * Computed once at class load.
     */
    public static final int DEFAULT_WALK_WORKERS = Math.clamp(
            Runtime.getRuntime().availableProcessors(),
            MIN_DEFAULT_WALK_WORKERS,
            MAX_DEFAULT_WALK_WORKERS);

    public BuildOptions {
        if (walkWorkers < 1) {
            throw new IllegalArgumentException("walkWorkers must be >= 1: walkWorkers=" + walkWorkers);
        }
    }

    public static BuildOptions defaults() {
        return new BuildOptions(DEFAULT_STRING_CONTENT_THRESHOLD, DEFAULT_WALK_WORKERS);
    }
}
