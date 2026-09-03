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

package cafe.jeffrey.microscope.core.configuration;

import cafe.jeffrey.profile.advisor.config.AdvisorConfiguration;
import cafe.jeffrey.profile.ai.claudecode.config.ClaudeCodeConfiguration;
import cafe.jeffrey.profile.ai.config.AiChatModelConfiguration;
import cafe.jeffrey.profile.ai.duckdb.heapdump.config.HeapDumpMcpConfiguration;
import cafe.jeffrey.profile.ai.duckdb.jfr.config.DuckDbMcpConfiguration;
import cafe.jeffrey.profile.ai.oql.config.AiAssistantConfiguration;
import org.springframework.context.annotation.Import;

/**
 * The in-app AI features — the direction in which Jeffrey calls out to a model provider: chat
 * backends, the JFR and heap-dump analysis assistants, the OQL assistant, the Advisor and the headless
 * Claude Code backend. Kept apart from {@code ProfileEngineConfiguration} so that the analysis engine
 * can be assembled without any of them.
 */
@Import({
        AiChatModelConfiguration.class,
        ClaudeCodeConfiguration.class,
        AiAssistantConfiguration.class,
        DuckDbMcpConfiguration.class,
        HeapDumpMcpConfiguration.class,
        AdvisorConfiguration.class
})
public class AiFeaturesConfiguration {
}
