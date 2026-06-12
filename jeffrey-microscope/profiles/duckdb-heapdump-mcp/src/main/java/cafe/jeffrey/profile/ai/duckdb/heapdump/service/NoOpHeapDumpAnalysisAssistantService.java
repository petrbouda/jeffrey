/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package cafe.jeffrey.profile.ai.duckdb.heapdump.service;

import cafe.jeffrey.profile.ai.chat.AssistantResponse;
import cafe.jeffrey.profile.ai.chat.NoOpAssistantService;
import cafe.jeffrey.profile.ai.duckdb.heapdump.model.HeapDumpAnalysisRequest;
import cafe.jeffrey.profile.ai.duckdb.heapdump.tools.HeapDumpToolsDelegate;

/**
 * No-op implementation of heap dump analysis assistant when AI is not configured.
 */
public class NoOpHeapDumpAnalysisAssistantService extends NoOpAssistantService implements HeapDumpAnalysisAssistantService {

    private static final String ANALYSIS_KIND = "heap dump analysis";

    public NoOpHeapDumpAnalysisAssistantService() {
        super(ANALYSIS_KIND);
    }

    @Override
    public AssistantResponse analyze(HeapDumpToolsDelegate delegate, HeapDumpAnalysisRequest request) {
        return notConfiguredResponse();
    }
}
