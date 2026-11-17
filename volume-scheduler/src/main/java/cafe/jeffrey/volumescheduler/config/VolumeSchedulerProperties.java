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

package cafe.jeffrey.volumescheduler.config;

import cafe.jeffrey.shared.common.JeffreyLayout;
import cafe.jeffrey.volumescheduler.retention.RetentionPolicy;
import cafe.jeffrey.volumescheduler.scheduler.JobSchedule;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

import java.nio.file.Path;
import java.time.Duration;

/**
 * Everything the volume scheduler is configured with, under {@code jeffrey.volume-scheduler}. Only
 * the volume's path is required; every other value has the default given here. Environment
 * variables bind the usual Spring way: {@code JEFFREY_VOLUMESCHEDULER_VOLUME_PATH},
 * {@code JEFFREY_VOLUMESCHEDULER_RETENTION_MAXSIZE}, {@code JEFFREY_VOLUMESCHEDULER_COMPRESSION_PERIOD}.
 * Durations take {@code 30s}, {@code 5m}, {@code 7d} or ISO-8601; sizes {@code 512MB}, {@code 20GB}
 * (binary multiples).
 */
@ConfigurationProperties(VolumeSchedulerProperties.PREFIX)
public record VolumeSchedulerProperties(
        Volume volume,
        @DefaultValue Retention retention,
        @DefaultValue Compression compression) {

    public static final String PREFIX = "jeffrey.volume-scheduler";

    public VolumeSchedulerProperties {
        if (volume == null) {
            throw new IllegalArgumentException(PREFIX + ".volume.path is required: "
                    + "the directory that holds the volume's workspaces/, e.g. /mnt/jeffrey");
        }
    }

    /**
     * The recording volume.
     *
     * @param path the directory that holds {@code workspaces/}: the provisioner's
     *             {@code jeffrey-home}, e.g. {@code /mnt/jeffrey}
     */
    public record Volume(Path path) {

        public Volume {
            if (path == null) {
                throw new IllegalArgumentException(PREFIX + ".volume.path is required");
            }
            // Absolute and normalized, so a directory resolved under it compares equal to its parent
            path = path.toAbsolutePath().normalize();
        }

        /** The directory every workspace lies in; missing until the provisioner wrote the first one. */
        public Path workspaces() {
            return path.resolve(JeffreyLayout.WORKSPACES_DIR);
        }
    }

    /**
     * The retention job: every rule of {@link RetentionPolicy}, run over every project per pass.
     *
     * @param enabled                    whether it runs on its own; it can always be run on demand
     * @param period                     between the end of one pass and the start of the next
     * @param sessionRetention           a session goes whole once its newest recording started longer ago
     * @param maxSessions                logical sessions kept per instance; a run of failed-empty
     *                                   sessions (a crash loop) counts as one
     * @param newestSessionFileRetention each instance's newest session loses its closed recordings
     *                                   older than this; its newest recording is never touched
     * @param maxSize                    the disk one project may occupy, reclaimed oldest first: whole
     *                                   ended sessions, then closed chunks of newest sessions
     * @param instanceRetention          an instance directory without a session goes once it is older
     */
    public record Retention(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("5m") Duration period,
            @DefaultValue("7d") Duration sessionRetention,
            @DefaultValue("10") int maxSessions,
            @DefaultValue("7d") Duration newestSessionFileRetention,
            @DefaultValue("20GB") DataSize maxSize,
            @DefaultValue("14d") Duration instanceRetention) {

        public JobSchedule schedule() {
            return new JobSchedule(enabled, period);
        }

        public RetentionPolicy policy() {
            return new RetentionPolicy(
                    sessionRetention, maxSessions, newestSessionFileRetention, maxSize.toBytes(), instanceRetention);
        }
    }

    /**
     * The compression job: LZ4 of every project's closed JFR recordings.
     *
     * @param enabled whether it runs on its own; it can always be run on demand
     * @param period  between the end of one run and the start of the next
     */
    public record Compression(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("1m") Duration period) {

        public JobSchedule schedule() {
            return new JobSchedule(enabled, period);
        }
    }
}
