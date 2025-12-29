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

package pbouda.jeffrey.platform.scheduler.job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.platform.project.repository.RepositoryStorage;
import pbouda.jeffrey.shared.JfrFileUtils;
import pbouda.jeffrey.shared.model.job.JobType;
import pbouda.jeffrey.platform.manager.project.ProjectManager;
import pbouda.jeffrey.platform.manager.workspace.WorkspacesManager;
import pbouda.jeffrey.platform.scheduler.JobContext;
import pbouda.jeffrey.platform.scheduler.job.descriptor.JobDescriptorFactory;
import pbouda.jeffrey.platform.scheduler.job.descriptor.RecordingIntervalGeneratorJobDescriptor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RecordingIntervalGeneratorProjectJob extends RepositoryProjectJob<RecordingIntervalGeneratorJobDescriptor> {

    private static final Logger LOG = LoggerFactory.getLogger(RecordingIntervalGeneratorProjectJob.class);

    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss");
    private final Duration period;

    public RecordingIntervalGeneratorProjectJob(
            WorkspacesManager workspacesManager,
            RepositoryStorage.Factory remoteRepositoryManagerFactory,
            JobDescriptorFactory jobDescriptorFactory,
            Duration period) {
        super(workspacesManager, remoteRepositoryManagerFactory, jobDescriptorFactory);
        this.period = period;
    }

    @Override
    protected void executeOnRepository(
            ProjectManager manager,
            RepositoryStorage repositoryStorage,
            RecordingIntervalGeneratorJobDescriptor jobDescriptor,
            JobContext context) {

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

        LocalTime currentTime = LocalTime.now().truncatedTo(ChronoUnit.MINUTES);

        if (!currentTime.equals(jobDescriptor.at())) {
            return;
        }

        LOG.info("Generate a new recording: project='{}' descriptor={}", projectId, jobDescriptor);

        // Filter out the files that are not readable:
        // - file is not a JFR file
        // - corrupted JFR file (e.g. killed application)
        List<Path> selectedFiles = selectRecordingFiles(files, jobDescriptor).stream()
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
                    projectId, filesToString(files), jobDescriptor);
            return;
        }

        Path targetPath = resolveRelativePath(jobDescriptor.filePattern());

//        try {
        // TODO: Fix merging and uploading
//            manager.recordingsManager().mergeAndUploadSession(targetPath, selectedFiles);

//        } catch (IOException e) {
//            LOG.error("Cannot merge and upload selected files: project='{}' files={} target={}",
//                    projectId, filesToString(selectedFiles), targetPath, e);
//            throw new RuntimeException(e);
//        }

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

    private static List<Path> selectRecordingFiles(List<Path> files, RecordingIntervalGeneratorJobDescriptor jobDescriptor) {
        List<Path> selectedFiles = new ArrayList<>();
        for (Path path : files) {
            Instant lastModified = Instant.ofEpochMilli(path.toFile().lastModified());
            LocalTime lastModifiedTime = LocalTime.ofInstant(lastModified, ZoneOffset.systemDefault());

            if (insideOrEqual(lastModifiedTime, jobDescriptor.at(), jobDescriptor.to())) {
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

    @Override
    public Duration period() {
        return period;
    }

    @Override
    public JobType jobType() {
        return JobType.INTERVAL_RECORDING_GENERATOR;
    }
}
