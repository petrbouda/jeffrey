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
package cafe.jeffrey.profile.mcp;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Every tool's {@link McpToolWeight}, by its advertised name, read once from the {@link McpToolMeta}
 * of the classes that declare the tools — so a {@link McpNextTool} carries the weight of the tool it
 * names without the code that builds it stating the weight a second time.
 */
public final class McpToolWeights {

    /**
     * One class whose {@code @Tool} methods are advertised under one family prefix.
     *
     * @param type   the class declaring the tools
     * @param prefix the family prefix, e.g. {@code jfr} for {@code jfr_listTables}
     */
    public record ToolClass(Class<?> type, String prefix) {

        public ToolClass {
            if (type == null || prefix == null || prefix.isBlank()) {
                throw new IllegalArgumentException("type and prefix are required: type=" + type + " prefix=" + prefix);
            }
        }
    }

    private final Map<String, McpToolWeight> byTool;

    private McpToolWeights(Map<String, McpToolWeight> byTool) {
        this.byTool = Collections.unmodifiableMap(new TreeMap<>(byTool));
    }

    /**
     * Reads every tool the classes declare. A tool without {@link McpToolMeta}, or a name declared
     * twice, is refused here, the way assembling the family would refuse it.
     */
    public static McpToolWeights read(List<ToolClass> classes) {
        Map<String, McpToolWeight> byTool = new TreeMap<>();
        for (ToolClass toolClass : classes) {
            for (Method method : ToolMethodIndex.toolMethods(toolClass.type())) {
                String name = ToolMethodIndex.toolName(toolClass.prefix(), method);
                McpToolMeta declared = method.getAnnotation(McpToolMeta.class);
                if (declared == null) {
                    throw new IllegalStateException("Tool " + name + " declares no @McpToolMeta, so it has no weight");
                }
                if (byTool.put(name, McpToolWeight.of(declared)) != null) {
                    throw new IllegalStateException("Tool " + name + " is declared twice");
                }
            }
        }
        return new McpToolWeights(byTool);
    }

    /** The weight of the named tool; a name no class declares is a programming error. */
    public McpToolWeight of(String tool) {
        McpToolWeight weight = byTool.get(tool);
        if (weight == null) {
            throw new IllegalArgumentException("Unknown tool: tool=" + tool);
        }
        return weight;
    }

    /** Every tool name read, sorted. */
    public Set<String> tools() {
        return byTool.keySet();
    }
}
