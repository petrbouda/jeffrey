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
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import pbouda.jeffrey.manager.FileBasedRecordingManager;
import pbouda.jeffrey.manager.ProfileManager;
import pbouda.jeffrey.manager.ProfilesManager;
import pbouda.jeffrey.manager.RecordingManager;
import pbouda.jeffrey.repository.JdbcTemplateFactory;
import pbouda.jeffrey.repository.model.ProfileInfo;

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
                case "version" -> System.out.println(resolveJeffreyVersion());
                case "upload-recordings" -> uploadRecordings(args);
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
        WorkingDirs workingDirs = context.getBean(WorkingDirs.class);
        workingDirs.initializeDirectories();

        JdbcTemplateFactory jdbcTemplateFactory = new JdbcTemplateFactory(workingDirs);

        List<ProfileInfo> profiles = workingDirs.retrieveAllProfiles();
        for (ProfileInfo profile : profiles) {
            FlywayMigration.migrate(jdbcTemplateFactory.create(profile));
        }
    }
}
