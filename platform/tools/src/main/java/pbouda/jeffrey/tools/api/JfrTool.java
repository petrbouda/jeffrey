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

package pbouda.jeffrey.tools.api;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;

public interface JfrTool extends ExternalTool {

    enum ScrubOperation {
        INCLUDE_EVENTS("include-events"),
        EXCLUDE_EVENTS("exclude-events"),
        INCLUDE_THREADS("include-threads"),
        EXCLUDE_THREADS("exclude-threads"),
        INCLUDE_CATEGORIES("include-categories"),
        EXCLUDE_CATEGORIES("exclude-categories");

        private final String option;

        ScrubOperation(String option) {
            this.option = option;
        }

        public String getOption() {
            return option;
        }
    }

    record SummaryItem(String eventType, long count, long size) {
    }

    record Summary(String version, int chunks, ZonedDateTime startTime, Duration duration, List<SummaryItem> events) {
    }

    /**
     * Disassemble chunks into a maximum number of files.
     *
     * <pre>
     * jfr disassemble --output output-dir recording.jfr
     * </pre>
     *
     * @param jfrPath   the path to the JFR recording
     * @param outputDir the output directory
     * @throws IOException if an I/O error occurs
     */
    void disassemble(Path jfrPath, Path outputDir);

    /**
     * Remove or create a new recording with the specified events excluded.
     *
     * <pre>
     * jfr scrub --exclude-events jdk.InitialSystemProperty,jdk.InitialEnvironmentVariable
     *  recording.jfr recording-scrubbed.jfr
     * </pre>
     *
     * @param jfrPath the path to the JFR recording
     * @param output  scrubbed recording
     * @throws IOException if an I/O error occurs
     */
    void scrub(ScrubOperation scrubOperation, Path jfrPath, Path output);

    /**
     * Returns a summary of the recording.
     *
     * <pre>
     *  jfr summary allocation.jfr
     *
     *  Version: 2.1
     *  Chunks: 32
     *  Start: 2024-07-29 05:24:20 (UTC)
     *  Duration: 674 s
     *
     *  Event Type                              Count  Size (bytes)
     * =============================================================
     *  jdk.ObjectAllocationOutsideTLAB      17958986     264286464
     *  jdk.Checkpoint                            590     355426528
     *  jdk.Metadata                               32       3255344
     *  jdk.ActiveRecording                        32          1485
     *  jdk.ResidentSetSize                         0             0
     *  jdk.ThreadStart                             0             0
     *  jdk.ThreadEnd                               0             0
     *  ...
     *  </pre>
     *
     * @param jfrPath the path to the JFR recording
     * @return the summary with information about the recording and all enabled registered events
     * IOException if an I/O error occurs
     */
    Summary summary(Path jfrPath);
}
