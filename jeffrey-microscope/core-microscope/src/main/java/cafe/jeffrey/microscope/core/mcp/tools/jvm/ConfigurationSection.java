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

package cafe.jeffrey.microscope.core.mcp.tools.jvm;

import cafe.jeffrey.microscope.core.mcp.MicroscopeView;
import cafe.jeffrey.microscope.core.mcp.tools.NextSteps;
import cafe.jeffrey.microscope.mcp.protocol.McpDescription;
import cafe.jeffrey.microscope.mcp.protocol.McpNullable;
import cafe.jeffrey.microscope.model.Type;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.mcp.McpFollowUp;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The Configuration dashboard: what the JVM was actually started with, in the labelled sections the
 * Jeffrey UI shows as tabs — application and JVM information, the CPU and operating system, the
 * collector, heap, survivor, TLAB and young-generation settings, the compiler, the container and the
 * virtualisation the whole thing ran on.
 * <p>
 * This is the grounding for every tuning claim. A recommendation to raise the heap, change the
 * collector or add a compiler flag is only worth making against the values the JVM really ran with,
 * and those values are here rather than in anyone's memory of what the deployment manifest says.
 */
public record ConfigurationSection(ProfileManager profileManager)
        implements JvmSection<ConfigurationSection.ConfigurationDashboard> {

    public static final String ID = "configuration";

    private static final String TITLE = "Configuration";

    private static final Set<Type> EVENT_TYPES = Set.of(
            Type.JVM_INFORMATION,
            Type.CPU_INFORMATION,
            Type.OS_INFORMATION,
            Type.GC_CONFIGURATION,
            Type.GC_HEAP_CONFIGURATION,
            Type.GC_SURVIVOR_CONFIGURATION,
            Type.GC_TLAB_CONFIGURATION,
            Type.YOUNG_GENERATION_CONFIGURATION,
            Type.COMPILER_CONFIGURATION,
            Type.CONTAINER_CONFIGURATION,
            Type.VIRTUALIZATION_INFORMATION);

    private static final String MANIFEST_GUIDANCE =
            "These are the values the JVM really ran with. Prefer them over a deployment manifest when "
                    + "proposing any flag.";
    private static final String TAB_WHY = "returns this section's settings";
    private static final String GC_WHY = "shows what the collector actually did with these settings";
    private static final String JIT_WHY = "shows what the compiler actually did with these settings";
    private static final String FLAGS_WHY =
            "says where each flag's value came from: set on the command line, or chosen by the JVM";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String title() {
        return TITLE;
    }

    @Override
    public Set<Type> eventTypes() {
        return EVENT_TYPES;
    }

    @Override
    public MicroscopeView view() {
        return MicroscopeView.OVERVIEW;
    }

    /**
     * The list routes to each tab this profile has, by the name it lists; a tab routes to what the
     * JVM did with those settings.
     */
    @Override
    public void followUp(NextSteps.Builder next, ConfigurationDashboard dashboard) {
        String profileId = profileManager.info().id();
        if (dashboard.section() == null) {
            for (ConfigurationTab tab : dashboard.sections()) {
                next.next(SectionCalls.on(SectionCalls.JVM_CONFIGURATION, profileId)
                        .with(SectionCalls.SECTION, tab)
                        .why(TAB_WHY));
            }
            return;
        }
        next.next(SectionCalls.on(SectionCalls.JVM_GC, profileId).why(GC_WHY))
                .next(SectionCalls.on(SectionCalls.JVM_JIT, profileId).why(JIT_WHY))
                .next(SectionCalls.on(SectionCalls.JVM_FLAGS, profileId).why(FLAGS_WHY))
                .guidance(MANIFEST_GUIDANCE);
    }

    /**
     * With no section asked for, the answer is which sections this profile has.
     */
    @Override
    public ConfigurationDashboard render() {
        return new ConfigurationDashboard(sections(configuration()), null, null);
    }

    /**
     * One tab's key/value pairs beside the list of tabs; empty when this profile did not record it.
     * <p>
     * The whole set runs to a few hundred key/value pairs, most of which are irrelevant to any one
     * question, which is why a tab is asked for by name.
     */
    public Optional<ConfigurationDashboard> section(ConfigurationTab tab) {
        JsonNode configuration = configuration();
        JsonNode values = configuration.get(tab.label());
        if (values == null || !values.isObject()) {
            return Optional.empty();
        }
        Map<String, String> pairs = new LinkedHashMap<>();
        values.properties().forEach(entry -> pairs.put(entry.getKey(), text(entry.getValue())));
        return Optional.of(new ConfigurationDashboard(sections(configuration), tab, pairs));
    }

    private JsonNode configuration() {
        return profileManager.profileConfigurationManager().configuration();
    }

    /**
     * The tabs present in this profile, in the order the UI renders them. A key no tab is defined for
     * cannot be asked for, so it is not offered.
     */
    private static List<ConfigurationTab> sections(JsonNode configuration) {
        List<ConfigurationTab> tabs = new ArrayList<>();
        configuration.properties().forEach(entry -> ConfigurationTab.ofLabel(entry.getKey()).ifPresent(tabs::add));
        return tabs;
    }

    /** A value as the UI shows it; the configuration holds text, but an unmapped event keeps its JSON. */
    private static String text(JsonNode value) {
        return value.isValueNode() ? value.asString() : value.toString();
    }

    /**
     * @param sections the tabs this profile has, the names {@code section} takes
     * @param section  the tab shown; null when only the list was asked for
     * @param values   that tab's settings by label; null when only the list was asked for
     */
    public record ConfigurationDashboard(
            List<ConfigurationTab> sections,
            @McpNullable
            ConfigurationTab section,
            @McpNullable
            Map<String, String> values) {
    }

    /**
     * What {@code jvm_configuration} answers: the envelope every section shares, around this section's dashboard.
     */
    public record Answer(
            SectionStatus status,
            @McpNullable
            @McpDescription(SectionHeader.REASON)
            String reason,
            String profileId,
            @McpDescription(SectionHeader.SECTION)
            String section,
            String title,
            @McpNullable
            @McpDescription(SectionHeader.DASHBOARD)
            ConfigurationDashboard dashboard,
            McpFollowUp followUp,
            @McpDescription(SectionHeader.UI_LINK)
            String uiLink) {

        public static Answer of(SectionHeader header, ConfigurationDashboard dashboard) {
            return new Answer(header.status(), header.reason(), header.profileId(), header.section(),
                    header.title(), dashboard, header.followUp(), header.uiLink());
        }
    }
}
