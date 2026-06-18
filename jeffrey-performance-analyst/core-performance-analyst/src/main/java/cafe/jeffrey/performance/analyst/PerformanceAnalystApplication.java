/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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

package cafe.jeffrey.performance.analyst;

import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;
import cafe.jeffrey.shared.common.JeffreyVersion;

@SpringBootApplication
public class PerformanceAnalystApplication implements WebMvcConfigurer {

    private static final String PAGES_LOCATION = "classpath:/pages-performance-analyst/";
    private static final String INDEX_PAGE = "/pages-performance-analyst/index.html";
    private static final String API_PREFIX = "api/";

    static void main(String[] args) {
        if (args.length == 0) {
            runApplication(args);
        } else {
            if (args[0].equals("--version")) {
                JeffreyVersion.print();
            } else {
                runApplication(args);
            }
        }
    }

    private static void runApplication(String[] args) {
        SpringApplication app = new SpringApplication(PerformanceAnalystApplication.class);
        app.setBannerMode(Banner.Mode.OFF);
        app.run(args);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry
                .addResourceHandler("/**")
                .addResourceLocations(PAGES_LOCATION)
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
                            if (!resourcePath.startsWith(API_PREFIX)) {
                                return new ClassPathResource(INDEX_PAGE);
                            }
                        } catch (Exception e) {
                            // If there's any error, fall back to index.html for non-API routes
                            if (!resourcePath.startsWith(API_PREFIX)) {
                                return new ClassPathResource(INDEX_PAGE);
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
}
