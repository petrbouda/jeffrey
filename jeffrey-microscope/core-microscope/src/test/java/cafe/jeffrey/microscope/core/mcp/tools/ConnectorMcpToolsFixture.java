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

package cafe.jeffrey.microscope.core.mcp.tools;

import cafe.jeffrey.microscope.core.manager.connector.ConnectorManager;
import cafe.jeffrey.microscope.core.manager.recordings.RecordingsManager;
import cafe.jeffrey.microscope.core.mcp.AdvertisedFamilies;
import cafe.jeffrey.microscope.core.mcp.AdvertisedFamiliesFixture;
import cafe.jeffrey.microscope.core.mcp.tools.connector.DownloadWindowQuestion;
import cafe.jeffrey.microscope.core.web.ProjectManagerResolver;

import java.time.Clock;
import java.time.Duration;

/**
 * Builds {@link ConnectorMcpTools} for a test: the collaborators it names, and the defaults the
 * {@code jeffrey.microscope.mcp.connector.*} properties give production for everything it leaves out.
 */
public record ConnectorMcpToolsFixture(
        ConnectorManager connectorManager,
        ProjectManagerResolver resolver,
        RecordingsManager recordingsManager,
        Clock clock,
        Duration scanBudget,
        Duration downloadResponseBudget,
        McpOperationRegistry operations,
        OperationAnswers answers,
        DownloadWindowQuestion windowQuestion,
        AdvertisedFamilies advertised) {

    public static final Duration SCAN_BUDGET = Duration.ofSeconds(20);
    public static final Duration DOWNLOAD_RESPONSE_BUDGET = BoundedJobs.WAIT_BUDGET;

    /** The defaults of the two {@code ask-window-over-*} properties: an hour or a gibibyte. */
    public static final DownloadWindowQuestion WINDOW_QUESTION =
            new DownloadWindowQuestion(Duration.ofHours(1), 1024L * 1024 * 1024);

    /**
     * The production budgets and window thresholds, an operation registry of their own, and every
     * family served.
     */
    public static ConnectorMcpToolsFixture of(
            ConnectorManager connectorManager, ProjectManagerResolver resolver, RecordingsManager recordingsManager,
            Clock clock) {
        McpOperationRegistry operations = new McpOperationRegistry(clock);
        return new ConnectorMcpToolsFixture(connectorManager, resolver, recordingsManager, clock, SCAN_BUDGET,
                DOWNLOAD_RESPONSE_BUDGET, operations, ToolFixtures.answers(),
                WINDOW_QUESTION, AdvertisedFamiliesFixture.EVERY_FAMILY);
    }

    public ConnectorMcpToolsFixture withBudgets(Duration scan, Duration downloadResponse) {
        return new ConnectorMcpToolsFixture(connectorManager, resolver, recordingsManager, clock, scan, downloadResponse,
                operations, answers, windowQuestion, advertised);
    }

    /** Another registry, and the standard answers over it. */
    public ConnectorMcpToolsFixture withOperations(McpOperationRegistry registry) {
        return new ConnectorMcpToolsFixture(connectorManager, resolver, recordingsManager, clock, scanBudget,
                downloadResponseBudget, registry, ToolFixtures.answers(), windowQuestion, advertised);
    }

    public ConnectorMcpToolsFixture withAnswers(OperationAnswers replacement) {
        return new ConnectorMcpToolsFixture(connectorManager, resolver, recordingsManager, clock, scanBudget,
                downloadResponseBudget, operations, replacement, windowQuestion, advertised);
    }

    /** Only the families an installation serves, so a next call to a withheld one is left out. */
    public ConnectorMcpToolsFixture withAdvertised(AdvertisedFamilies replacement) {
        return new ConnectorMcpToolsFixture(connectorManager, resolver, recordingsManager, clock, scanBudget,
                downloadResponseBudget, operations, answers, windowQuestion, replacement);
    }

    public ConnectorMcpToolsFixture withWindowQuestion(DownloadWindowQuestion replacement) {
        return new ConnectorMcpToolsFixture(connectorManager, resolver, recordingsManager, clock, scanBudget,
                downloadResponseBudget, operations, answers, replacement, advertised);
    }

    public ConnectorMcpTools build() {
        return new ConnectorMcpTools(connectorManager, resolver, recordingsManager, clock, scanBudget, downloadResponseBudget,
                operations, answers, windowQuestion, advertised);
    }
}
