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
import pbouda.jeffrey.model.RepositoryInfo;
import pbouda.jeffrey.provider.api.model.JobInfo;
import pbouda.jeffrey.provider.api.model.JobType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RecordingGeneratorJob extends RepositoryJob {

    private static final Logger LOG = LoggerFactory.getLogger(RecordingGeneratorJob.class);
    private static final JobType JOB_TYPE = JobType.RECORDING_GENERATOR;

    // { "filePattern": "generated/recording-%t.jfr", "at": "17:0", "from": "10:0", "to": "12:0" }
    private static final String PARAM_FILE_PATTERN = "filePattern";
    private static final String PARAM_AT = "at";
    private static final String PARAM_FROM = "from";
    private static final String PARAM_TO = "to";

    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss");

    private record JobParams(String filePattern, LocalTime at, LocalTime from, LocalTime to) {
        private static JobParams parse(Map<String, String> params) {
            return new JobParams(
                    params.get(PARAM_FILE_PATTERN),
                    LocalTime.parse(params.get(PARAM_AT)),
                    LocalTime.parse(params.get(PARAM_FROM)),
                    LocalTime.parse(params.get(PARAM_TO))
            );
        }
    }

    public RecordingGeneratorJob(ProjectsManager projectsManager) {
        super(projectsManager, JOB_TYPE);
    }

    @Override
    protected void executeOnRepository(ProjectManager manager, RepositoryInfo repositoryInfo, List<JobInfo> jobInfos) {
        String projectId = manager.info().id();

        LOG.info("Recording generation from the repository: project='{}' repository={}",
                projectId, repositoryInfo.repositoryPath());

        List<Path> files = allFiles(repositoryInfo.repositoryPath());
        /*
         * No files found in the repository
         * - No recordings in the repository, the application does not record the events
         * - Application is not running
         */
        if (files.isEmpty()) {
            LOG.warn("No recordings found in the repository: project='{}' repository={}",
                    projectId, repositoryInfo.repositoryPath());
            return;
        }

        for (JobInfo jobInfo : jobInfos) {
            JobParams params = JobParams.parse(jobInfo.params());
            LocalTime currentTime = LocalTime.now().truncatedTo(ChronoUnit.MINUTES);

            if (!currentTime.equals(params.at())) {
                continue;
            }

            LOG.info("Generate a new recording: project='{}' repository={} params={}",
                    projectId, repositoryInfo.repositoryPath(), params);

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
                continue;
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
