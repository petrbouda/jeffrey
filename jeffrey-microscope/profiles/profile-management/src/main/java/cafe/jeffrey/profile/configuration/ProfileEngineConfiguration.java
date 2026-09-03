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

package cafe.jeffrey.profile.configuration;

import cafe.jeffrey.profile.heapdump.oql.config.OqlEngineConfiguration;
import org.springframework.context.annotation.Import;

/**
 * The profile analysis engine: everything needed to open an analysed profile and answer questions
 * about it — managers, visualizations, analyses, JVM insights, custom event factories and the OQL
 * engine. Deliberately free of any AI provider so that a process which only reads profiles (the
 * external MCP server, for one) can import this alone.
 */
@Import({
        OqlEngineConfiguration.class,
        ProfileCoreConfiguration.class,
        ProfileVisualizationConfiguration.class,
        ProfileAnalysisConfiguration.class,
        ProfileJvmInsightConfiguration.class,
        ProfileCustomFactoriesConfiguration.class
})
public class ProfileEngineConfiguration {

    public static final String RECORDINGS_PATH = "recordings-path";
    public static final String PROFILES_PATH = "profiles-path";
}
