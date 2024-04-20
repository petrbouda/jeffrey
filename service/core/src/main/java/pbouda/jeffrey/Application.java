package pbouda.jeffrey;

import org.flywaydb.core.Flyway;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JndiDataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.XADataSourceAutoConfiguration;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import pbouda.jeffrey.repository.JdbcTemplateFactory;
import pbouda.jeffrey.repository.model.ProfileInfo;

import java.util.List;

@SpringBootApplication(exclude = {
        DataSourceAutoConfiguration.class,
        FlywayAutoConfiguration.class,
})
public class Application implements WebMvcConfigurer, ApplicationListener<ApplicationReadyEvent> {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
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
