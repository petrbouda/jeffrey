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

package cafe.jeffrey.microscope.mcp.app;

import cafe.jeffrey.microscope.mcp.LocalProfileResolver;
import cafe.jeffrey.microscope.mcp.McpEnablement;
import cafe.jeffrey.microscope.mcp.McpProfileResolver;
import cafe.jeffrey.microscope.mcp.McpServerConfiguration;
import cafe.jeffrey.microscope.persistence.api.MicroscopeCorePersistenceProvider;
import cafe.jeffrey.microscope.runtime.MicroscopeJeffreyDirs;
import cafe.jeffrey.microscope.runtime.MicroscopeRuntimeConfiguration;
import cafe.jeffrey.microscope.runtime.web.JeffreyRequestLoggingFilter;
import cafe.jeffrey.profile.configuration.ProfileEngineConfiguration;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.shared.common.model.repository.SupportedRecordingFile;
import cafe.jeffrey.storage.recording.api.RecordingStorage;
import cafe.jeffrey.storage.recording.filesystem.FilesystemRecordingStorage;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;

import java.nio.file.Path;
import java.util.List;

/**
 * The whole application: the home directory and persistence, the analysis engine, the MCP server —
 * and the two answers only a host can give: profiles resolve straight from the core database, and the
 * server is always on.
 */
@Configuration
@Import({
        MicroscopeRuntimeConfiguration.class,
        ProfileEngineConfiguration.class,
        McpServerConfiguration.class
})
public class McpApplicationConfiguration {

    private static final String API_URL_PATTERN = "/api/*";

    @Bean
    public McpProfileResolver mcpProfileResolver(
            MicroscopeCorePersistenceProvider localCorePersistenceProvider,
            ProfileManager.Factory profileManagerFactory) {
        return new LocalProfileResolver(localCorePersistenceProvider.localCoreRepositories(), profileManagerFactory);
    }

    @Bean
    public McpEnablement mcpEnablement() {
        return McpEnablement.ALWAYS;
    }

    @Bean(ProfileEngineConfiguration.PROFILES_PATH)
    public Path profilesPath(MicroscopeJeffreyDirs jeffreyDirs) {
        return jeffreyDirs.profiles();
    }

    @Bean(ProfileEngineConfiguration.RECORDINGS_PATH)
    public Path recordingsPath(MicroscopeJeffreyDirs jeffreyDirs) {
        return jeffreyDirs.recordings();
    }

    /**
     * The engine reads a profile's original recording for a few features (the event viewer, flags);
     * the layout under {@code recordings/} is the one the full Microscope wrote.
     */
    @Bean
    public RecordingStorage projectRecordingStorage(MicroscopeJeffreyDirs jeffreyDirs) {
        return new FilesystemRecordingStorage(
                jeffreyDirs.recordings(),
                List.of(SupportedRecordingFile.JFR_LZ4, SupportedRecordingFile.JFR,
                        SupportedRecordingFile.PPROF, SupportedRecordingFile.OTLP_PROFILE));
    }

    @Bean
    public FilterRegistrationBean<JeffreyRequestLoggingFilter> jeffreyRequestLoggingFilter() {
        FilterRegistrationBean<JeffreyRequestLoggingFilter> bean =
                new FilterRegistrationBean<>(new JeffreyRequestLoggingFilter());
        bean.addUrlPatterns(API_URL_PATTERN);
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return bean;
    }
}
