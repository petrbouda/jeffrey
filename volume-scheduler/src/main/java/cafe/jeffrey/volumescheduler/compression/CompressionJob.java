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

package cafe.jeffrey.volumescheduler.compression;

import cafe.jeffrey.volumescheduler.scheduler.Job;
import cafe.jeffrey.volumescheduler.scheduler.JobType;
import cafe.jeffrey.volumescheduler.scheduler.ProjectFanOut;
import cafe.jeffrey.volumescheduler.volume.RecordingSession;
import cafe.jeffrey.volumescheduler.volume.VolumeProjects;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Compresses the closed recordings of every project to LZ4.
 *
 * <p>Each run reads, per instance of the project — and a project may have several — only its two
 * newest sessions: the newest, which keeps recording, and the one before it, whose last recording
 * became complete when the newest started. Older sessions were reached by an earlier run. Which
 * session is the newest is a fact of the instance (its sessions' order), never of the session's
 * position in the project's listing.</p>
 */
public final class CompressionJob implements Job {

    /** The newest session and the one it ended: the only sessions a run can find anything new in. */
    private static final int SESSIONS_PER_INSTANCE = 2;

    private final ProjectFanOut fanOut;
    private final RecordingCompressor compressor;

    public CompressionJob(VolumeProjects projects, RecordingCompressor compressor) {
        this.fanOut = new ProjectFanOut(projects, JobType.COMPRESSION);
        this.compressor = compressor;
    }

    @Override
    public JobType type() {
        return JobType.COMPRESSION;
    }

    @Override
    public String run() {
        AtomicInteger recordings = new AtomicInteger();
        AtomicInteger sessions = new AtomicInteger();
        int projects = fanOut.forEachProject(project -> {
            for (RecordingSession session : project.newestSessions(SESSIONS_PER_INSTANCE)) {
                int compressed = compressor.compress(session);
                if (compressed > 0) {
                    recordings.addAndGet(compressed);
                    sessions.incrementAndGet();
                }
            }
        });
        return "Compressed " + count(recordings.get(), "recording") + " in " + count(sessions.get(), "session")
                + " across " + count(projects, "project");
    }

    private static String count(int value, String noun) {
        return value + " " + noun + (value == 1 ? "" : "s");
    }
}
