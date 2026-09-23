/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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

import cafe.jeffrey.hub.core.web.JeffreyExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Exercises the real Vite output copied by pages-hub's Maven resources phase. */
@Tag("frontend")
class HubFrontendTest {

    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(FrontendConfiguration.class);
        context.refresh();
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    @Test
    void rootForwardsToIndex() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));
    }

    @Test
    void indexReferencesExistingJavascriptAndStylesheets() throws Exception {
        String html = mvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andReturn().getResponse().getContentAsString();

        var assets = Pattern.compile("(?:src|href)=\"(/assets/[^\"]+\\.(?:js|css))\"")
                .matcher(html).results().map(match -> match.group(1)).toList();
        assertThat(assets).as("Build pages-hub before running frontend tests (npm run build)")
                .anyMatch(asset -> asset.endsWith(".js"))
                .anyMatch(asset -> asset.endsWith(".css"));
        for (String asset : assets) {
            var response = mvc.perform(get(asset)).andExpect(status().isOk())
                    .andReturn().getResponse();
            assertThat(response.getContentType()).as(asset)
                    .isIn(asset.endsWith(".js")
                            ? new String[]{"text/javascript", "application/javascript"}
                            : new String[]{"text/css"});
            assertThat(response.getContentAsByteArray()).as(asset).isNotEmpty();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"/index.html", "/scheduler", "/storage", "/api-docs"})
    void htmlIsRevalidatedAfterDeployment(String path) throws Exception {
        mvc.perform(get(path).accept(MediaType.TEXT_HTML))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-cache"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/assets/missing.js", "/assets/missing.css", "/assets/missing",
            "/missing.svg", "/api", "/api/missing"})
    void missingAssetsAndApiRoutesDoNotReturnTheSpa(String path) throws Exception {
        mvc.perform(get(path).accept(MediaType.ALL)).andExpect(status().isNotFound());
        mvc.perform(get(path).accept(MediaType.TEXT_HTML)).andExpect(status().isNotFound());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    static class FrontendConfiguration implements WebMvcConfigurer {

        @Override
        public void addResourceHandlers(ResourceHandlerRegistry registry) {
            new HubApplication().addResourceHandlers(registry);
        }

        @Override
        public void addViewControllers(ViewControllerRegistry registry) {
            new HubApplication().addViewControllers(registry);
        }

        @Bean
        JeffreyExceptionHandler exceptionHandler() {
            return new JeffreyExceptionHandler();
        }
    }
}
