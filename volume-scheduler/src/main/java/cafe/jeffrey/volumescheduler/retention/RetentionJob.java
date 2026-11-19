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

package cafe.jeffrey.volumescheduler.retention;

import cafe.jeffrey.volumescheduler.scheduler.Job;
import cafe.jeffrey.volumescheduler.scheduler.JobType;
import cafe.jeffrey.volumescheduler.scheduler.ProjectFanOut;
import cafe.jeffrey.volumescheduler.storage.LatestStorageOverview;
import cafe.jeffrey.volumescheduler.storage.ProjectStorage;
import cafe.jeffrey.volumescheduler.volume.VolumeProject;
import cafe.jeffrey.volumescheduler.volume.VolumeProjects;

import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

/**
 * Every retention rule of every project in one pass, and the storage overview from what is left.
 *
 * <p>Each project's sessions are listed once, with their files — a stat per file over the volume,
 * which is the expensive part — and the rules run over that one listing in order: the session age
 * window and per-instance cap, the trim of newest sessions, the disk quota, then stale instances.
 * What remains is measured into {@link LatestStorageOverview}, so the storage endpoint needs no walk
 * of its own.</p>
 */
public final class RetentionJob implements Job {

    /**
     * What one pass over every project did.
     */
    record Summary(int projects, int sessionsDeleted, int filesDeleted, int instancesDeleted, long usedBytes) {

        private static final long BYTES_PER_KIB = 1024;

        /** The binary prefixes {@link #formatBytes} steps through, kibi to exbi. */
        private static final String UNIT_PREFIXES = "KMGTPE";

        static Summary of(List<RetentionPass.Outcome> outcomes, List<ProjectStorage> measured) {
            return new Summary(
                    measured.size(),
                    outcomes.stream().mapToInt(RetentionPass.Outcome::sessionsDeleted).sum(),
                    outcomes.stream().mapToInt(RetentionPass.Outcome::filesDeleted).sum(),
                    outcomes.stream().mapToInt(RetentionPass.Outcome::instancesDeleted).sum(),
                    measured.stream().mapToLong(ProjectStorage::totalBytes).sum());
        }

        String describe() {
            return "Deleted " + count(sessionsDeleted, "session") + ", " + count(filesDeleted, "file")
                    + " and " + count(instancesDeleted, "instance") + "; "
                    + formatBytes(usedBytes) + " across " + count(projects, "project");
        }

        private static String count(int value, String noun) {
            return value + " " + noun + (value == 1 ? "" : "s");
        }

        /** Binary units with one decimal, {@code 512 B}, {@code 1.5 GiB}. */
        private static String formatBytes(long bytes) {
            long absolute = bytes == Long.MIN_VALUE ? Long.MAX_VALUE : Math.abs(bytes);
            if (absolute < BYTES_PER_KIB) {
                return bytes + " B";
            }
            long value = absolute;
            CharacterIterator units = new StringCharacterIterator(UNIT_PREFIXES);
            for (int i = 40; i >= 0 && absolute > 0xfffccccccccccccL >> i; i -= 10) {
                value >>= 10;
                units.next();
            }
            value *= Long.signum(bytes);
            return String.format("%.1f %ciB", value / (double) BYTES_PER_KIB, units.current());
        }
    }

    private final ProjectFanOut fanOut;
    private final List<RetentionRule> rules;
    private final LatestStorageOverview storageOverview;
    private final Clock clock;

    public RetentionJob(
            VolumeProjects projects,
            RetentionPolicy policy,
            LatestStorageOverview storageOverview,
            Clock clock) {

        this.fanOut = new ProjectFanOut(projects, JobType.RETENTION);
        this.rules = List.of(
                new SessionRetentionRule(policy.sessionRetention(), policy.maxSessions()),
                new NewestSessionTrimRule(policy.newestSessionFileRetention()),
                new StorageQuotaRule(policy.maxProjectBytes()),
                new StaleInstanceRule(policy.instanceRetention()));
        this.storageOverview = storageOverview;
        this.clock = clock;
    }

    @Override
    public JobType type() {
        return JobType.RETENTION;
    }

    /** A full pass now, deleting what is due, which also refreshes the storage overview. */
    @Override
    public String run() {
        List<RetentionPass.Outcome> outcomes = new ArrayList<>();
        List<ProjectStorage> measured = new ArrayList<>();
        fanOut.forEachProject(project -> {
            RetentionPass.Outcome outcome = retain(project);
            outcomes.add(outcome);
            measured.add(ProjectStorage.measure(project.ref(), outcome.remaining()));
        });
        storageOverview.publish(measured);
        return Summary.of(outcomes, measured).describe();
    }

    /** One project: every rule, in order, over a single listing of its sessions. */
    RetentionPass.Outcome retain(VolumeProject project) {
        RetentionPass pass = new RetentionPass(project, clock.instant());
        for (RetentionRule rule : rules) {
            rule.apply(pass);
        }
        return pass.outcome();
    }
}
