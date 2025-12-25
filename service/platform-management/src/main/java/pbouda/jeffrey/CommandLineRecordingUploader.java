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
import pbouda.jeffrey.common.filesystem.JeffreyDirs;
import pbouda.jeffrey.common.model.repository.SupportedRecordingFile;
import pbouda.jeffrey.common.model.workspace.WorkspaceType;
import pbouda.jeffrey.configuration.AppConfiguration;
import pbouda.jeffrey.profile.manager.model.CreateProject;
import pbouda.jeffrey.manager.project.ProjectManager;
import pbouda.jeffrey.manager.project.ProjectsManager;
import pbouda.jeffrey.manager.workspace.SandboxWorkspacesManager;
import pbouda.jeffrey.manager.workspace.WorkspaceManager;
import pbouda.jeffrey.manager.workspace.WorkspacesManager;
import pbouda.jeffrey.provider.api.NewRecordingHolder;
import pbouda.jeffrey.provider.api.model.recording.NewRecording;
import pbouda.jeffrey.recording.ProjectRecordingInitializer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public record CommandLineRecordingUploader(Path recordingsDir) implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger LOG = LoggerFactory.getLogger(CommandLineRecordingUploader.class);

    private static final String PROJECT_NAME = "jeffrey-examples-project";
    private static final String PROJECT_LABEL = "Jeffrey Examples Project";

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

        SpringApplication application = new SpringApplication(Application.class, AppConfiguration.class);
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
                        "jeffrey.job.scheduler.enabled", false,
                        "jeffrey.logging.jfr-events.application.enabled", false
                );
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
        var homeDirs = context.getBean(JeffreyDirs.class);
        homeDirs.initialize();

        WorkspacesManager.CreateWorkspaceRequest createRequest = WorkspacesManager.CreateWorkspaceRequest.builder()
                .name("Jeffrey Examples")
                .description("Workspace for example recordings")
                .type(WorkspaceType.SANDBOX)
                .build();

        WorkspacesManager workspacesManager = context.getBean(SandboxWorkspacesManager.class);
        var workspaceInfo = workspacesManager.create(createRequest);

        WorkspaceManager workspaceManager = workspacesManager.mapToWorkspaceManager(workspaceInfo);
        ProjectsManager projectsManager = workspaceManager.projectsManager();

        CreateProject createProject = new CreateProject(
                null, PROJECT_NAME, PROJECT_LABEL, null, null, Map.of());

        ProjectManager projectManager = projectsManager.create(createProject);

        try (var stream = Files.list(recordingsDir)) {
            List<Path> files = stream.filter(SupportedRecordingFile.JFR_LZ4::matches)
                    .toList();

            ProjectRecordingInitializer recordingInitializer = projectManager.recordingInitializer();
            for (Path file : files) {
                String filename = file.getFileName().toString();
                NewRecording recording = new NewRecording(filename, filename, null);

                try (NewRecordingHolder holder = recordingInitializer.newRecording(recording);
                     var inputStream = Files.newInputStream(file)) {
                    holder.transferFrom(inputStream);
                    LOG.info("Uploaded recording file: {}", filename);
                } catch (Exception e) {
                    throw new RuntimeException("Cannot upload the recording: " + recording, e);
                }
            }

            LOG.info("Uploaded {} recordings to project '{}'", files.size(), PROJECT_NAME);
        } catch (IOException e) {
            throw new RuntimeException("Cannot upload the recordings", e);
        }
    }
}
