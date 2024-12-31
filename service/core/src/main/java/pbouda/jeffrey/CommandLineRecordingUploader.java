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

package pbouda.jeffrey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.ResourcePropertySource;
import pbouda.jeffrey.common.filesystem.FileSystemUtils;
import pbouda.jeffrey.common.filesystem.HomeDirs;
import pbouda.jeffrey.common.model.ProjectInfo;
import pbouda.jeffrey.manager.ProjectManager;
import pbouda.jeffrey.manager.ProjectsManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public record CommandLineRecordingUploader(Path recordingsDir) implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger LOG = LoggerFactory.getLogger(CommandLineRecordingUploader.class);

    private static final String PROJECT_NAME = "Examples";

    public static void uploadRecordings(String[] args) {
        if (args.length != 2) {
            System.out.println("Invalid number of arguments: jeffrey.jar upload-recordings <dir>");
            System.exit(1);
        }

        Path recordingPath = Path.of(args[1]);
        if (!Files.isDirectory(recordingPath)) {
            System.out.println("Provided location of recordings is not a directory");
            System.exit(1);
        }

        SpringApplication application = new SpringApplication(Application.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setBannerMode(Banner.Mode.OFF);
        application.setLogStartupInfo(false);
        application.addInitializers(new UploadRecordingsPropertiesInitializer());
        application.setListeners(List.of(new CommandLineRecordingUploader(recordingPath)));
        application.run();
    }

    private static class UploadRecordingsPropertiesInitializer
            implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        @Override
        public void initialize(ConfigurableApplicationContext context) {
            try {
                PropertiesPropertySource propertySource =
                        new ResourcePropertySource(new ClassPathResource("application.properties"));

                Map<String, Object> mapSources = Map.of(
                        "jeffrey.profile.initializer.enabled", true,
                        "jeffrey.profile.initializer.blocking", true,
                        "jeffrey.profile.initializer.concurrent", false);

                var sources = context.getEnvironment().getPropertySources();
                sources.addFirst(propertySource);
                sources.addFirst(new MapPropertySource("example-initializer-props", mapSources));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        var context = event.getApplicationContext();
        var homeDirs = context.getBean(HomeDirs.class);
        homeDirs.initialize();

        var projectsManager = context.getBean(ProjectsManager.class);
        ProjectManager projectManager = projectsManager.create(new ProjectInfo(PROJECT_NAME));
        Path targetDir = projectManager.dirs().recordingsDir();

        try (var fileStream = Files.walk(recordingsDir)) {
            List<Path> files = fileStream.toList();

            for (Path file : files) {
                Path relativizePath = recordingsDir.relativize(file);

                if (file.equals(recordingsDir)) {
                    // Skip the root directory
                    continue;
                }

                if (Files.isDirectory(file)) {
                    Path path = targetDir.resolve(relativizePath);
                    FileSystemUtils.createDirectories(path);
                    continue;
                }

                if (validRecordingName(relativizePath)) {
                    try {
                        projectManager.recordingsManager().upload(relativizePath, Files.newInputStream(file));
                        projectManager.profilesManager().createProfile(relativizePath);
                    } catch (IOException e) {
                        LOG.error("Cannot upload recording: file={}", file.getFileName().toString(), e);
                    }
                    LOG.info("Uploaded and initialized recording: {}", relativizePath);
                }
            }
        } catch (IOException e) {
            LOG.error("Cannot upload recording: error={}", e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private static boolean validRecordingName(Path recording) {
        return !Files.isDirectory(recording)
                && recording.toString().endsWith(".jfr");
    }
}
