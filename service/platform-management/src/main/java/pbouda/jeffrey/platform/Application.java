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

package pbouda.jeffrey.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.tomcat.TomcatConnectorCustomizer;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

@SpringBootApplication
public class Application implements WebMvcConfigurer {

    static void main(String[] args) {
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
    // @Override
    // public void addCorsMappings(CorsRegistry registry) {
    //     registry.addMapping("/**")
    //             .allowedMethods("*")
    //             .exposedHeaders("Content-Disposition");
    // }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry
                .addResourceHandler("/**")
                .addResourceLocations("classpath:/pages/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) {
                        try {
                            Resource requestedResource = location.createRelative(resourcePath);
                            // If the requested resource exists and is readable, return it
                            if (requestedResource.exists() && requestedResource.isReadable()) {
                                return requestedResource;
                            }
                            // If the resource doesn't exist and doesn't start with /api, return index.html
                            if (!resourcePath.startsWith("api/")) {
                                return new ClassPathResource("/pages/index.html");
                            }
                        } catch (Exception e) {
                            // If there's any error, fall back to index.html for non-API routes
                            if (!resourcePath.startsWith("api/")) {
                                return new ClassPathResource("/pages/index.html");
                            }
                        }
                        return null;
                    }
                });
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/").setViewName("forward:/index.html");
    }

    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> tomcatConnectorCustomizer() {
        return factory -> {
            factory.addContextCustomizers((context) -> {
                context.setAllowCasualMultipartParsing(true);
            });
            TomcatConnectorCustomizer parseBodyMethodCustomizer = connector -> {
                // Set the max size of the request body to unlimited
                connector.setMaxPostSize(-1);
            };
            factory.addConnectorCustomizers(parseBodyMethodCustomizer);
        };
    }
}
