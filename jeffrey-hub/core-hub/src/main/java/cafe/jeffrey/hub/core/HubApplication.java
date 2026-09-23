/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cafe.jeffrey.hub.core;

import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;
import cafe.jeffrey.shared.common.JeffreyVersion;

import java.io.IOException;

@SpringBootApplication
public class HubApplication implements WebMvcConfigurer {

    private static final String INDEX_PAGE = "index.html";
    private static final String API_PATH = "api";
    private static final String ASSETS_PATH = "assets";

    private static final String VERSION_FLAG = "--version";

    static void main(String[] args) {
        if (args.length > 0 && VERSION_FLAG.equals(args[0])) {
            JeffreyVersion.print();
            return;
        }
        runApplication(args);
    }

    private static void runApplication(String[] args) {
        SpringApplication app = new SpringApplication(HubApplication.class);
        app.setBannerMode(Banner.Mode.OFF);
        app.run(args);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry
                .addResourceHandler("/**")
                .addResourceLocations("classpath:/pages-hub/")
                // Revalidate HTML so a page cached before deployment cannot retain obsolete bundle URLs.
                .setCacheControl(CacheControl.noCache())
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        Resource requestedResource = super.getResource(resourcePath, location);
                        if (requestedResource != null) {
                            return requestedResource;
                        }
                        // Only browser routes fall back to the SPA. Missing bundles must be 404s,
                        // not successful HTML responses that fail the browser's module MIME check.
                        if (!resourcePath.contains(".")
                                && !isUnderPath(resourcePath, API_PATH)
                                && !isUnderPath(resourcePath, ASSETS_PATH)) {
                            return super.getResource(INDEX_PAGE, location);
                        }
                        return null;
                    }
                });
    }

    private static boolean isUnderPath(String resourcePath, String prefix) {
        return resourcePath.equals(prefix) || resourcePath.startsWith(prefix + "/");
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/").setViewName("forward:/index.html");
    }
}
