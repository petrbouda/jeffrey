package pbouda.jeffrey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
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
import pbouda.jeffrey.repository.JdbcTemplateFactory;
import pbouda.jeffrey.repository.model.ProfileInfo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;

@SpringBootApplication(exclude = {
        DataSourceAutoConfiguration.class,
        FlywayAutoConfiguration.class,
})
public class Application implements WebMvcConfigurer, ApplicationListener<ApplicationReadyEvent> {

    private static final Logger LOG = LoggerFactory.getLogger(Application.class);
    private static final String JEFFREY_VERSION = "jeffrey-version.txt";
    public static final String NO_VERSION = "Cannot resolve the version!";

    public static void main(String[] args) {
        if ("version".equals(args[0])) {
            System.out.println(resolveJeffreyVersion());
        } else {
            SpringApplication.run(Application.class, args);
        }
    }

    private static String resolveJeffreyVersion() {
        try (InputStream in = Application.class.getClassLoader()
                .getResourceAsStream(JEFFREY_VERSION)) {
            if (in != null) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
                    String line = reader.readLine();
                    return line.isBlank() ? NO_VERSION : line;
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
