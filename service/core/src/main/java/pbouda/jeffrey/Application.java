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
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import pbouda.jeffrey.filesystem.HomeDirs;
import pbouda.jeffrey.filesystem.ProjectDirs;
import pbouda.jeffrey.repository.model.ProfileInfo;
import pbouda.jeffrey.repository.model.ProjectInfo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@SpringBootApplication(exclude = {
        DataSourceAutoConfiguration.class,
        FlywayAutoConfiguration.class,
})
public class Application implements WebMvcConfigurer, ApplicationListener<ApplicationReadyEvent> {

    private static final Logger LOG = LoggerFactory.getLogger(Application.class);

    private static final String JEFFREY_VERSION = "jeffrey-tag.txt";
    private static final String NO_VERSION = "Cannot resolve the version!";

    public static void main(String[] args) {
        if (args.length == 0) {
            SpringApplication.run(Application.class, args);
        } else {
            switch (args[0]) {
                case "--version" -> System.out.println(resolveJeffreyVersion());
                case "upload-recordings" -> uploadRecordings(args);
                default -> SpringApplication.run(Application.class, args);
            }
        }
    }

    private static void uploadRecordings(String[] args) {
        if (args.length != 2) {
            System.out.println("Invalid number of arguments: jeffrey.jar upload-recordings <dir>");
            System.exit(1);
        }

        Path recordingPath = Path.of(args[1]);
        if (!Files.isDirectory(recordingPath)) {
            System.out.println("Provided location of recordings is not a directory");
            System.exit(1);
        }

        PropertySourcesPlaceholderConfigurer ppc = new PropertySourcesPlaceholderConfigurer();
        ppc.setLocations(new ClassPathResource("application.properties"));

        SpringApplication application = new SpringApplication(Application.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setBannerMode(Banner.Mode.OFF);
        application.setLogStartupInfo(false);
        application.setListeners(List.of(new CommandLineRecordingUploader(recordingPath)));
        application.run();
    }

    private static String resolveJeffreyVersion() {
        try (InputStream in = Application.class.getClassLoader()
                .getResourceAsStream(JEFFREY_VERSION)) {
            if (in != null) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
                    String version = reader.readLine();
                    return version.isBlank() ? NO_VERSION : version;
                }
            } else {
                LOG.warn("Unable to read a version: {}", JEFFREY_VERSION);
                return NO_VERSION;
            }
        } catch (IOException ex) {
            LOG.warn("Unable to read a version: {}", JEFFREY_VERSION, ex);
            return NO_VERSION;
        }
    }

    // For DEV purposes
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**").allowedMethods("*");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry
                .addResourceHandler("/**")
                .addResourceLocations("classpath:/pages/");
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/").setViewName("forward:/index.html");
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        ConfigurableApplicationContext context = event.getApplicationContext();
        HomeDirs homeDirs = context.getBean(HomeDirs.class);
        homeDirs.initialize();

        for (ProjectInfo project : homeDirs.allProjects()) {
            // Migrate the database belonging to a single project
            ProjectDirs projectDirs = homeDirs.project(project);
            FlywayMigration.migrate(projectDirs);

            // Migration of all profiles belonging to the given project
            for (ProfileInfo profile : projectDirs.allProfiles()) {
                FlywayMigration.migrate(projectDirs.profile(profile));
            }
        }

        // Migrate the database belonging to Jeffrey
        FlywayMigration.migrate(homeDirs);
    }

    /**
     * It needs to be here to correctly load properties for `upload-recordings` features
     * (to load recordings automatically, e.g. for dockerfile examples)
     */
    @Configuration
    @PropertySource("classpath:application.properties")
    public static class UploadRecordingsProperties {

        @Bean
        public static PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer() {
            return new PropertySourcesPlaceholderConfigurer();
        }
    }
}
