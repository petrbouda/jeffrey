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

import cafe.jeffrey.shared.volume.FilesystemVolume;
import cafe.jeffrey.volumescheduler.compression.CompressionJob;
import cafe.jeffrey.volumescheduler.compression.RecordingCompressor;
import cafe.jeffrey.volumescheduler.retention.RetentionJob;
import cafe.jeffrey.volumescheduler.retention.RetentionPolicy;
import cafe.jeffrey.volumescheduler.scheduler.JobScheduler;
import cafe.jeffrey.volumescheduler.scheduler.ScheduledJob;
import cafe.jeffrey.volumescheduler.storage.LatestStorageOverview;
import cafe.jeffrey.volumescheduler.volume.VolumeProjects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;

/**
 * Every bean of the volume scheduler: the volume it maintains, the two jobs and the scheduler that
 * runs them. The volume is its only state; nothing here creates a directory on it.
 */
@Configuration
@EnableConfigurationProperties(VolumeSchedulerProperties.class)
public class VolumeSchedulerConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(VolumeSchedulerConfiguration.class);

    @Bean
    public Clock applicationClock() {
        return Clock.systemUTC();
    }

    /**
     * The volume's workspaces directory. The volume's own directory has to be there: a path that is
     * not a directory is a volume that is not mounted, which would otherwise read as a volume with
     * no project and quietly do nothing. Its {@code workspaces/} may still be missing; the
     * provisioner creates it with the first session.
     */
    @Bean
    public FilesystemVolume recordingVolume(VolumeSchedulerProperties properties) {
        Path path = properties.volume().path();
        if (!Files.isDirectory(path)) {
            throw new IllegalStateException(
                    "The recording volume is not a directory, is it mounted? " + VolumeSchedulerProperties.PREFIX
                            + ".volume.path=" + path);
        }
        LOG.info("Maintaining the recording volume: path={}", path);
        return new FilesystemVolume(properties.volume().workspaces());
    }

    @Bean
    public VolumeProjects volumeProjects(FilesystemVolume recordingVolume) {
        return new VolumeProjects(recordingVolume);
    }

    @Bean
    public LatestStorageOverview latestStorageOverview(Clock clock) {
        return new LatestStorageOverview(clock);
    }

    @Bean
    public RetentionPolicy retentionPolicy(VolumeSchedulerProperties properties) {
        return properties.retention().policy();
    }

    @Bean
    public RetentionJob retentionJob(
            VolumeProjects projects, RetentionPolicy policy, LatestStorageOverview storageOverview, Clock clock) {
        return new RetentionJob(projects, policy, storageOverview, clock);
    }

    @Bean
    public CompressionJob compressionJob(VolumeProjects projects) {
        return new CompressionJob(projects, new RecordingCompressor());
    }

    @Bean(destroyMethod = "close")
    public JobScheduler jobScheduler(
            RetentionJob retentionJob,
            CompressionJob compressionJob,
            VolumeSchedulerProperties properties,
            Clock clock) {

        return new JobScheduler(List.of(
                new ScheduledJob(retentionJob, properties.retention().schedule()),
                new ScheduledJob(compressionJob, properties.compression().schedule())), clock);
    }

    /** The first runs start once the application is up, so the API answers while they work. */
    @Bean
    public ApplicationListener<ApplicationReadyEvent> jobSchedulerStart(JobScheduler jobScheduler) {
        return _ -> jobScheduler.start();
    }
}
