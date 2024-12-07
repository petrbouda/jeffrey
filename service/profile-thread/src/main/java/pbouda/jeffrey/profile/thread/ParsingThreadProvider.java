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

package pbouda.jeffrey.profile.thread;

import pbouda.jeffrey.common.Type;
import pbouda.jeffrey.common.model.ProfileInfo;
import pbouda.jeffrey.jfrparser.jdk.JdkRecordingIterators;
import pbouda.jeffrey.profile.summary.EventSummaryProvider;

import java.nio.file.Path;
import java.util.List;

public class ParsingThreadProvider implements ThreadInfoProvider {

    // Metadata to correctly create and format Tooltip
    private static final ThreadMetadata METADATA = new ThreadMetadata(
            new EventMetadata("Thread's Lifespan", ThreadState.STARTED,
                    List.of()),
            new EventMetadata("Java Thread Park", ThreadState.PARKED,
                    List.of(field("Duration", "jdk.jfr.Timespan"),
                            field("Parked On", "Class"),
                            field("Timeout", "jdk.jfr.Timespan"),
                            field("Until", "jdk.jfr.Timestamp"))),
            new EventMetadata("Java Monitor Blocked", ThreadState.BLOCKED,
                    List.of(field("Duration", "jdk.jfr.Timespan"),
                            field("Monitor Class", "Class"),
                            field("Previous Owner", "Thread"))),
            new EventMetadata("Java Monitor Wait", ThreadState.WAITING,
                    List.of(field("Duration", "jdk.jfr.Timespan"),
                            field("Monitor Class", "Class"),
                            field("Notifier Thread", "Thread"),
                            field("Timeout", "jdk.jfr.Timespan"),
                            field("Timed Out", "Boolean"))),
            new EventMetadata("Thread Sleep", ThreadState.SLEEP,
                    List.of(field("Duration", "jdk.jfr.Timespan"),
                            field("Sleep Time", "jdk.jfr.Timespan"))),
            new EventMetadata("Socket Read", ThreadState.SOCKET_READ,
                    List.of(field("Duration", "jdk.jfr.Timespan"),
                            field("Remote Host"),
                            field("Remote Addr"),
                            field("Remote Port"),
                            field("Timeout", "jdk.jfr.Timespan"),
                            field("Read Bytes", "jdk.jfr.DataAmount"),
                            field("End of Stream", "Boolean"))),
            new EventMetadata("Socket Write", ThreadState.SOCKET_WRITE,
                    List.of(field("Duration", "jdk.jfr.Timespan"),
                            field("Remote Host"),
                            field("Remote Addr"),
                            field("Remote Port"),
                            field("Read Written", "jdk.jfr.DataAmount"))),
            new EventMetadata("File Read", ThreadState.FILE_READ,
                    List.of(field("Duration", "jdk.jfr.Timespan"),
                            field("Path"),
                            field("Read Bytes", "jdk.jfr.DataAmount"),
                            field("End of File", "Boolean"))),
            new EventMetadata("File Write", ThreadState.FILE_WRITE,
                    List.of(field("Duration", "jdk.jfr.Timespan"),
                            field("Path"),
                            field("Read Written", "jdk.jfr.DataAmount"))
            )
    );

    private static ThreadField field(String value, String type) {
        return new ThreadField(value, type);
    }

    private static ThreadField field(String value) {
        return new ThreadField(value, null);
    }

    private final EventSummaryProvider summaryProvider;
    private final ProfileInfo profileInfo;
    private final List<Path> recordings;

    public ParsingThreadProvider(
            EventSummaryProvider summaryProvider,
            ProfileInfo profileInfo,
            List<Path> recordings) {

        this.summaryProvider = summaryProvider;
        this.profileInfo = profileInfo;
        this.recordings = recordings;
    }

    @Override
    public ThreadRoot get() {
        List<ThreadRow> threads = JdkRecordingIterators.automaticAndCollect(
                recordings,
                ThreadsEventProcessor::new,
                new ThreadCollector(profileInfo.startedAt(), profileInfo.endedAt()));

        // To be able to provide WallClock flamegraph in Thread View
        boolean containsWallClock = summaryProvider.get().stream()
                .anyMatch(event -> Type.WALL_CLOCK_SAMPLE.code().equals(event.name()));

        return new ThreadRoot(new ThreadCommon(profileInfo.duration().toNanos(), containsWallClock, METADATA), threads);
    }
}
