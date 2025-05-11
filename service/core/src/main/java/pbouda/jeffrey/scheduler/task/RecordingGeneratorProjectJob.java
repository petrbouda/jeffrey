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
import pbouda.jeffrey.common.JfrFileUtils;
import pbouda.jeffrey.manager.ProjectManager;
import pbouda.jeffrey.manager.ProjectsManager;
import pbouda.jeffrey.project.repository.RemoteRepositoryStorage;
import pbouda.jeffrey.provider.api.model.job.JobInfo;
import pbouda.jeffrey.provider.api.model.job.JobType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RecordingGeneratorProjectJob extends RepositoryProjectJob {

    private static final Logger LOG = LoggerFactory.getLogger(RecordingGeneratorProjectJob.class);
    private static final JobType JOB_TYPE = JobType.INTERVAL_RECORDING_GENERATOR;

    /**
     * { "filePattern": "generated/recording-%t.jfr", "at": "17:00", "from": "10:00", "to": "12:00" }
     * `at` can be missing, and it's automatically 1 minute after `to`
     */
    private static final String PARAM_FILE_PATTERN = "filePattern";
    private static final String PARAM_AT = "at";
    private static final String PARAM_FROM = "from";
    private static final String PARAM_TO = "to";

    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss");

    private record JobParams(String filePattern, LocalTime at, LocalTime from, LocalTime to) {
        private static JobParams parse(Map<String, String> params) {
            LocalTime from = requiredTime(params, PARAM_FROM);
            LocalTime to = requiredTime(params, PARAM_TO);
            LocalTime at = requiredTime(params, PARAM_AT);
            return new JobParams(params.get(PARAM_FILE_PATTERN), at, from, to);
        }

        private static LocalTime requiredTime(Map<String, String> params, String paramName) {
            String paramValue = params.get(paramName);
            if (paramValue == null) {
                throw new IllegalArgumentException("Missing parameter: " + paramName);
            }
            return LocalTime.parse(paramValue);
        }
    }

    public RecordingGeneratorProjectJob(
            ProjectsManager projectsManager,
            RemoteRepositoryStorage.Factory remoteRepositoryManagerFactory,
            Duration period) {
        super(projectsManager, remoteRepositoryManagerFactory, JOB_TYPE, period);
    }

    @Override
    protected void executeOnRepository(
            ProjectManager manager, RemoteRepositoryStorage remoteRepositoryStorage, JobInfo jobInfo) {

        String projectId = manager.info().id();

        LOG.info("Recording generation from the repository: project='{}'", projectId);

        // TODO: Use Remote Repository Manager to get files
        List<Path> files = List.of();
        // List<Path> files = allFiles(repositoryInfo.repositoryPath());

        /*
         * No files found in the repository
         * - No recordings in the repository, the application does not record the events
         * - Application is not running
         */
        if (files.isEmpty()) {
            LOG.warn("No recordings found in the repository: project='{}'", projectId);
            return;
        }

        JobParams params = JobParams.parse(jobInfo.params());
        LocalTime currentTime = LocalTime.now().truncatedTo(ChronoUnit.MINUTES);

        if (!currentTime.equals(params.at())) {
            return;
        }

        LOG.info("Generate a new recording: project='{}' params={}", projectId, params);

        // Filter out the files that are not readable:
        // - file is not a JFR file
        // - corrupted JFR file (e.g. killed application)
        List<Path> selectedFiles = selectRecordingFiles(files, params).stream()
                .sorted()
                .filter(file -> {
                    boolean validJfr = JfrFileUtils.isJfrFileReadable(file);
                    if (!validJfr) {
                        LOG.warn("Invalid/Corrupted file (e.g. killed application): {}", file);
                    }
                    return validJfr;
                })
                .toList();

        /*
         * No files found for the generation
         * - Incorrect interval from-to for generating a new recording
         * - No recordings in the repository, the application does not record the events
         * - Application is not running
         */
        if (selectedFiles.isEmpty()) {
            LOG.warn("No files found for the generation: project='{}' files={} params={}",
                    projectId, filesToString(files), params);
            return;
        }

        Path targetPath = resolveRelativePath(params.filePattern);

        try {
            manager.recordingsManager().mergeAndUpload(targetPath, selectedFiles);
        } catch (IOException e) {
            LOG.error("Cannot merge and upload selected files: project='{}' files={} target={}",
                    projectId, filesToString(selectedFiles), targetPath, e);
            throw new RuntimeException(e);
        }

        LOG.info("New recording generated: project='{}' files={} target={}",
                projectId, filesToString(selectedFiles), targetPath);
    }

    private static String filesToString(List<Path> files) {
        return files.stream()
                .map(path -> path.getFileName().toString())
                .collect(Collectors.joining(", "));
    }

    private static Path resolveRelativePath(String filePattern) {
        String relative = filePattern;
        if (filePattern.contains("%t")) {
            var replacement = DATETIME_FORMATTER.format(LocalDateTime.now());
            relative = filePattern.replaceFirst("%t", replacement);
        }
        return Path.of(relative);
    }

    private static List<Path> selectRecordingFiles(List<Path> files, JobParams params) {
        List<Path> selectedFiles = new ArrayList<>();
        for (Path path : files) {
            Instant lastModified = Instant.ofEpochMilli(path.toFile().lastModified());
            LocalTime lastModifiedTime = LocalTime.ofInstant(lastModified, ZoneOffset.systemDefault());

            if (insideOrEqual(lastModifiedTime, params.from, params.to)) {
                selectedFiles.add(path);
            }
        }

        return selectedFiles;
    }

    private static boolean insideOrEqual(LocalTime value, LocalTime from, LocalTime to) {
        LocalTime tValue = value.truncatedTo(ChronoUnit.MINUTES);
        LocalTime tFrom = from.truncatedTo(ChronoUnit.MINUTES);
        LocalTime tTo = to.truncatedTo(ChronoUnit.MINUTES);

        return (tValue.isAfter(tFrom) && tValue.isBefore(tTo)) || tValue.equals(tFrom) || tValue.equals(tTo);
    }

    private static List<Path> allFiles(Path repositoryPath) {
        try (Stream<Path> stream = Files.list(repositoryPath)) {
            return stream.filter(Files::isRegularFile).toList();
        } catch (IOException e) {
            throw new RuntimeException("Cannot list all recordings from the repository: " + repositoryPath, e);
        }
    }
}
