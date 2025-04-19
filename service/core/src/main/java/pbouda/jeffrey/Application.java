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

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.web.embedded.tomcat.TomcatConnectorCustomizer;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@SpringBootApplication(exclude = {
        DataSourceAutoConfiguration.class,
        FlywayAutoConfiguration.class,
})
public class Application implements WebMvcConfigurer {

    public static void main(String[] args) {
        if (args.length == 0) {
            SpringApplication.run(Application.class, args);
        } else {
            switch (args[0]) {
                case "--version" -> JeffreyVersion.print();
                case "upload-recordings" -> CommandLineRecordingUploader.uploadRecordings(args);
                default -> SpringApplication.run(Application.class, args);
            }
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

//    @Bean
//    public TomcatServletWebServerFactory tomcatFactory() {
//        return new TomcatServletWebServerFactory() {
//            @Override
//            protected void postProcessContext(Context context) {
//                context.setAllowCasualMultipartParsing(true);
//            }
//        };
//    }

    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> tomcatConnectorCustomizer() {
        return factory -> {
            factory.addContextCustomizers((context) -> {
                context.setAllowCasualMultipartParsing(true);
            });
            TomcatConnectorCustomizer parseBodyMethodCustomizer = connector -> {
                connector.setMaxPostSize(1024 * 1024 * 1024); // 1 GB
            };
            factory.addConnectorCustomizers(parseBodyMethodCustomizer);
        };
    }
}
