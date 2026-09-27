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

package cafe.jeffrey.microscope.core.mcp;

import cafe.jeffrey.microscope.core.mcp.tools.UiLinkRoutes;
import cafe.jeffrey.microscope.mcp.protocol.McpToolSpec;
import cafe.jeffrey.profile.mcp.McpToolNames;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The owner rule, enforced: data for the agent, a link for the user. Every answer whose subject has a
 * page in the Microscope UI carries a {@code uiLink} to it; a tool whose subject has no page is on
 * {@link #NO_PAGE}, with the reason.
 * <p>
 * The rule is checked on the schema, where it is a contract: {@code uiLink} is a required, non-null
 * string. Each tool's own tests then validate every answer it gives -- the data path and each status
 * path -- against that schema, and check with {@link UiLinkRoutes} that the link lands on a page the
 * router serves. So "non-null on every non-error answer" is the schema's promise, and "resolves" is the
 * family tests'.
 * <p>
 * The rule covers every advertised tool: each either declares the link or is on {@link #NO_PAGE}.
 * Whether a tool answers structured content at all is {@code McpToolSurfaceTest}'s to pin.
 */
class McpUiLinkEnforcementTest {

    private static final String OPERATION_HAS_NO_PAGE =
            "An operation is process-local state of one attempt, which no Microscope page shows; the profile "
                    + "a finished recording operation built is linked from the recordings tools' own answers.";

    private static final String HEAP_SQL_HAS_NO_PAGE =
            "The heap-dump index's tables have no Microscope page: the UI reads the dump through its "
                    + "reports and the OQL page, which asks object questions, not table ones.";

    private static final String IDE_HAS_NO_PAGE =
            "What the ide_ tools describe lives in the developer's IntelliJ window - a checkout, a file, a line - "
                    + "not in a Microscope view.";

    private static final String JFR_SQL_HAS_NO_PAGE =
            "The profile database's tables have no Microscope page: the UI reads a profile through its views, not "
                    + "its tables. The catalogue tools name the jeffrey://profile/{id}/schema resource instead, and "
                    + "attach it as a resource link.";

    /** The advertised tools whose subject has no Microscope page, and why. */
    private static final Map<String, String> NO_PAGE = Map.ofEntries(
            Map.entry("operations_status", OPERATION_HAS_NO_PAGE),
            Map.entry("operations_cancel", OPERATION_HAS_NO_PAGE),
            Map.entry("heap_listTables", HEAP_SQL_HAS_NO_PAGE),
            Map.entry("heap_describeTable", HEAP_SQL_HAS_NO_PAGE),
            Map.entry("heap_executeQuery", HEAP_SQL_HAS_NO_PAGE),
            Map.entry("ide_windows", IDE_HAS_NO_PAGE),
            Map.entry("ide_link", IDE_HAS_NO_PAGE),
            Map.entry("ide_resolve", IDE_HAS_NO_PAGE),
            Map.entry("ide_source", IDE_HAS_NO_PAGE),
            Map.entry("ide_open", IDE_HAS_NO_PAGE),
            Map.entry("jfr_listTables", JFR_SQL_HAS_NO_PAGE),
            Map.entry("jfr_describeTable", JFR_SQL_HAS_NO_PAGE),
            Map.entry("jfr_executeQuery", JFR_SQL_HAS_NO_PAGE));

    private static final String UI_LINK = "uiLink";
    private static final String STRING_TYPE = "string";

    private static List<McpToolSpec> advertised() {
        return AdvertisedTools.all();
    }

    @Nested
    class EveryTool {

        /** The rule is checked on everything an installation can advertise, every family included. */
        @Test
        void everyKnownFamilyIsAdvertised() {
            Set<String> served = advertised().stream()
                    .map(spec -> McpToolNames.familyOf(spec.name()))
                    .collect(Collectors.toSet());

            assertEquals(ExternalMcpProperties.knownFamilies(), served);
        }

        /**
         * A tool with a page declares {@code uiLink} as a required string that admits no null, so
         * every answer that conforms to its schema carries one.
         */
        @Test
        void everyToolWithAPageDeclaresANonNullUiLink() {
            List<String> violations = new ArrayList<>();
            for (McpToolSpec spec : advertised()) {
                if (NO_PAGE.containsKey(spec.name())) {
                    continue;
                }
                JsonNode schema = spec.outputSchema();
                if (schema == null) {
                    violations.add(spec.name() + ": no outputSchema, so no uiLink can be declared");
                    continue;
                }
                JsonNode type = schema.path("properties").path(UI_LINK).path("type");
                if (!type.isString() || !STRING_TYPE.equals(type.asString())) {
                    violations.add(spec.name() + ": uiLink is not a non-null string, it is " + type);
                }
                boolean required = false;
                for (JsonNode name : schema.path("required")) {
                    required |= UI_LINK.equals(name.asString());
                }
                if (!required) {
                    violations.add(spec.name() + ": uiLink is not required");
                }
            }

            assertTrue(violations.isEmpty(), String.join("\n", violations));
        }

        /** A tool on the no-page list really has no link to give, so it does not pretend to. */
        @Test
        void aToolWithoutAPageDeclaresNoUiLink() {
            for (McpToolSpec spec : advertised()) {
                if (NO_PAGE.containsKey(spec.name()) && spec.outputSchema() != null) {
                    assertFalse(spec.outputSchema().path("properties").has(UI_LINK), spec.name());
                }
            }
        }

        @Test
        void theNoPageListNamesOnlyAdvertisedToolsWithAReason() {
            Set<String> names = advertised().stream().map(McpToolSpec::name).collect(Collectors.toCollection(TreeSet::new));

            for (Map.Entry<String, String> entry : NO_PAGE.entrySet()) {
                assertTrue(names.contains(entry.getKey()), entry.getKey() + " is not an advertised tool");
                assertFalse(entry.getValue().isBlank(), entry.getKey() + " has no reason");
            }
        }

        /**
         * Every advertised tool is accounted for: a link, or a reason on the no-page list. The tool count
         * itself is pinned once, by {@code McpToolsetAssemblerTest}; this only refuses an empty walk.
         */
        @Test
        void everyToolIsAccountedFor() {
            List<String> unaccounted = advertised().stream()
                    .filter(spec -> !NO_PAGE.containsKey(spec.name()))
                    .filter(spec -> spec.outputSchema() == null
                            || !spec.outputSchema().path("properties").has(UI_LINK))
                    .map(McpToolSpec::name)
                    .toList();

            assertFalse(advertised().isEmpty());
            assertEquals(List.of(), unaccounted);
        }
    }

    /**
     * Every link is built from a {@link MicroscopeView} or a {@link MicroscopePage}, so a link lands on
     * a real page exactly when every constant does: with the schema's required, non-null uiLink, this
     * makes resolution hold by construction rather than tool by tool.
     */
    @Nested
    class LinkTargets {

        @Test
        void everyProfileViewIsARouteTheFrontendServes() {
            Set<String> routes = UiLinkRoutes.profileRoutes();

            List<String> missing = Arrays.stream(MicroscopeView.values())
                    .map(MicroscopeView::path)
                    .filter(path -> !routes.contains(path))
                    .toList();

            assertEquals(List.of(), missing);
        }

        @Test
        void everyGlobalPageIsATopLevelRouteTheFrontendServes() {
            Set<String> routes = UiLinkRoutes.globalRoutes();

            List<String> missing = Arrays.stream(MicroscopePage.values())
                    .map(MicroscopePage::path)
                    .filter(path -> !routes.contains(path))
                    .toList();

            assertEquals(List.of(), missing);
        }

        @Test
        void aViewIsFoundByItsPathAndOnlyByIt() {
            for (MicroscopeView view : MicroscopeView.values()) {
                assertEquals(view, MicroscopeView.ofPath(view.path()).orElseThrow(), view.path());
            }
            assertTrue(MicroscopeView.ofPath("no-such-view").isEmpty());
        }
    }

    /** The check the family tests make on every link they are handed. */
    @Nested
    class Routes {

        @Test
        void acceptsAProfilesLandingPageAndItsRoutedViews() {
            UiLinkRoutes.assertResolves("http://localhost:8585/profiles/p-1");
            UiLinkRoutes.assertResolves("http://localhost:8585/profiles/p-1/auto-analysis");
            UiLinkRoutes.assertResolves("http://localhost:8585/profiles/p-1/heap-dump/gc-root-path?objectId=42");
        }

        @Test
        void acceptsTheRecordingsList() {
            UiLinkRoutes.assertResolves("http://localhost:8585/recordings");
        }

        @Test
        void refusesAViewTheRouterDoesNotServe() {
            assertThrows(AssertionError.class,
                    () -> UiLinkRoutes.assertResolves("http://localhost:8585/profiles/p-1/no-such-view"));
            assertThrows(AssertionError.class,
                    () -> UiLinkRoutes.assertResolves("http://localhost:8585/no-such-page"));
            assertThrows(AssertionError.class, () -> UiLinkRoutes.assertResolves("/profiles/p-1"));
        }

        /**
         * A query parameter the page does not read opens the page unfiltered while the answer claims
         * otherwise; the frontend's link contract (link-params.json) says what each page reads.
         */
        @Test
        void refusesAQueryParameterThePageDoesNotRead() {
            AssertionError undeclared = assertThrows(AssertionError.class, () -> UiLinkRoutes.assertResolves(
                    "http://localhost:8585/profiles/p-1/flamegraph-view?eventType=jdk.ExecutionSample&window=5"));
            assertTrue(undeclared.getMessage().contains("window"), undeclared.getMessage());
            assertThrows(AssertionError.class, () -> UiLinkRoutes.assertResolves(
                    "http://localhost:8585/profiles/p-1/auto-analysis?eventType=jdk.ExecutionSample"));
            assertThrows(AssertionError.class,
                    () -> UiLinkRoutes.assertResolves("http://localhost:8585/profiles/p-1?baseline=p-2"));
            assertThrows(AssertionError.class,
                    () -> UiLinkRoutes.assertResolves("http://localhost:8585/recordings?sort=size"));
        }

        @Test
        void acceptsTheQueryParametersThePageDeclares() {
            UiLinkRoutes.assertResolves("http://localhost:8585/profiles/p-1/flamegraph-view?eventType=jdk.ExecutionSample"
                    + "&graphMode=DIFFERENTIAL&baseline=p-2&useWeight=true&startEpochMs=1&endEpochMs=2&search=a%20b");
            UiLinkRoutes.assertResolves("http://localhost:8585/profiles/p-1/flamegraphs/differential?baseline=p-2");
            UiLinkRoutes.assertResolves("http://localhost:8585/profiles/p-1/events?eventType=jdk.GarbageCollection");
        }

        @Test
        void everyPageWithDeclaredParametersIsARouteTheFrontendServes() {
            Set<String> routes = UiLinkRoutes.profileRoutes();
            List<String> unknown = UiLinkRoutes.linkParameters().keySet().stream()
                    .filter(route -> !routes.contains(route))
                    .toList();

            assertTrue(unknown.isEmpty(), "link-params.json names pages the router does not serve: " + unknown);
            assertEquals(Set.of("startEpochMs", "endEpochMs", "search", "eventType", "graphMode", "baseline",
                            "useWeight", "useThreadMode", "excludeIdleSamples", "excludeNonJavaSamples",
                            "onlyUnsafeAllocationSamples"),
                    UiLinkRoutes.linkParameters().get(MicroscopeView.FLAMEGRAPH_VIEW.path()));
        }
    }
}
