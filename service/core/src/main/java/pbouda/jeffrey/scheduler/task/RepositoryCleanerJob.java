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

package pbouda.jeffrey.scheduler.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.manager.ProjectManager;
import pbouda.jeffrey.manager.ProjectsManager;
import pbouda.jeffrey.model.JobInfo;
import pbouda.jeffrey.model.JobType;
import pbouda.jeffrey.model.RepositoryInfo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Stream;

public class RepositoryCleanerJob extends RepositoryJob {

    private static final Logger LOG = LoggerFactory.getLogger(RepositoryCleanerJob.class);
    private static final JobType JOB_TYPE = JobType.REPOSITORY_CLEANER;

    private static final String PARAM_DURATION = "duration";
    private static final String PARAM_TIME_UNIT = "timeUnit";

    public RepositoryCleanerJob(ProjectsManager projectsManager) {
        super(projectsManager, JOB_TYPE);
    }

    protected void executeOnRepository(ProjectManager manager, RepositoryInfo repository, List<JobInfo> jobInfos) {
        String projectName = manager.info().name();
        LOG.info("Cleaning the repository: project='{}' repository={}", projectName, repository.repositoryPath());

        if (jobInfos.size() > 1) {
            LOG.warn("Multiple jobs found: project='{}' job_type={} job_count={}",
                    projectName, JOB_TYPE, jobInfos.size());
        }

        Duration duration = parseDuration(jobInfos.getFirst());

        List<Path> candidatesForDeletion = findCandidatesForDeletion(repository.repositoryPath(), duration);
        candidatesForDeletion.forEach(path -> {
            try {
                Files.delete(path);
                LOG.info("Deleted recording from the repository: project='{}' file={}", projectName, path.getFileName());
            } catch (IOException e) {
                LOG.error("Failed to delete file: {}", path, e);
            }
        });
    }

    private List<Path> findCandidatesForDeletion(Path repositoryPath, Duration duration) {
        Instant currentTime = Instant.now();
        try (Stream<Path> allFilesInRepository = Files.walk(repositoryPath)) {
            return allFilesInRepository
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".jfr"))
                    .filter(path -> {
                        long lastModified = path.toFile().lastModified();
                        return currentTime.isAfter(Instant.ofEpochMilli(lastModified).plus(duration));
                    })
                    .toList();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static Duration parseDuration(JobInfo jobInfo) {
        String duration = jobInfo.params().get(PARAM_DURATION);
        String timeUnit = jobInfo.params().get(PARAM_TIME_UNIT);
        return Duration.of(Long.parseLong(duration), parseTimeUnit(timeUnit));
    }

    private static ChronoUnit parseTimeUnit(String timeUnit) {
        return switch (timeUnit) {
            case "Seconds" -> ChronoUnit.SECONDS;
            case "Minutes" -> ChronoUnit.MINUTES;
            case "Hours" -> ChronoUnit.HOURS;
            case "Days" -> ChronoUnit.DAYS;
            default -> throw new IllegalArgumentException("Unknown time unit: " + timeUnit);
        };
    }
}