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

package cafe.jeffrey.microscope.core.mcp.tools.jvm;

import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.shared.common.model.Type;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
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
public record ConfigurationSection(ProfileManager profileManager) implements JvmSection {

    public static final String ID = "configuration";

    private static final String TITLE = "Configuration";

    private static final Set<Type> EVENT_TYPES = Set.of(
            Type.APP_INFORMATION,
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

    private static final List<String> NEXT_STEPS = List.of(
            "These are the values the JVM really ran with. Prefer them over a deployment manifest when "
                    + "proposing any flag.",
            "What the collector and the compiler actually did with these settings is in jvm_gc and jvm_jit.");

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
    public List<String> nextSteps() {
        return NEXT_STEPS;
    }

    @Override
    public Object render() {
        return profileManager.profileConfigurationManager().configuration();
    }

    /**
     * The section names present in this profile — the UI's tabs, in the order it renders them.
     * <p>
     * Exposed so one configuration section can be asked for by name: the whole set runs to a few
     * hundred key/value pairs, most of which are irrelevant to any one question.
     */
    public List<String> sectionNames() {
        List<String> names = new ArrayList<>();
        profileManager.profileConfigurationManager().configuration()
                .properties()
                .forEach(entry -> names.add(entry.getKey()));
        return names;
    }

    /**
     * One named section's key/value pairs, or null when this profile has no section by that name.
     */
    public JsonNode section(String name) {
        return profileManager.profileConfigurationManager().configuration().get(name);
    }
}
