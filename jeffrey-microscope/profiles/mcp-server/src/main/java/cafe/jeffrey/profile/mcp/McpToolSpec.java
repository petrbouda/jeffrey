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

import tools.jackson.databind.node.ObjectNode;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The {@code tools/list} description of a single MCP tool: its name, human-readable description,
 * JSON-Schema input definition, and the behavioural hints a client shows before approving a call.
 *
 * @param name        the tool name as seen by the model ({@code mcp__<server>__<name>})
 * @param title       a human-readable display name, derived from {@code name}
 * @param description the tool description
 * @param inputSchema the JSON-Schema object describing the tool's arguments
 * @param annotations what the tool does to the world — read-only unless it says otherwise
 */
public record McpToolSpec(
        String name,
        String title,
        String description,
        ObjectNode inputSchema,
        McpToolAnnotations annotations,
        ObjectNode outputSchema
) {
    public McpToolSpec(String name, String description, ObjectNode inputSchema, McpToolAnnotations annotations) {
        this(name, description, inputSchema, annotations, null);
    }

    public McpToolSpec(
            String name,
            String description,
            ObjectNode inputSchema,
            McpToolAnnotations annotations,
            ObjectNode outputSchema) {

        this(name, titleFor(name), description, inputSchema, annotations, outputSchema);
    }

    /**
     * How each tool family is spelled in a title. A map rather than a rule, because most of these are
     * acronyms or compound words that no capitalisation rule gets right: {@code jfr} is JFR, not Jfr,
     * and {@code methodtracing} is two words with nothing in the name to split on. A family missing
     * here is simply capitalised, so adding one is never a requirement.
     */
    private static final Map<String, String> FAMILY_TITLES = Map.ofEntries(
            Map.entry("jfr", "JFR"),
            Map.entry("jvm", "JVM"),
            Map.entry("jdbc", "JDBC"),
            Map.entry("grpc", "gRPC"),
            Map.entry("http", "HTTP"),
            Map.entry("io", "I/O"),
            Map.entry("ide", "IDE"),
            Map.entry("methodtracing", "Method tracing"));

    /**
     * Words a title keeps in capitals. {@code getPathToGCRoot} already carries {@code GC}, but a name
     * that spells one in lower case would otherwise read as {@code Oql}.
     */
    private static final Set<String> ACRONYMS = Set.of("gc", "jit", "oql", "sql", "uri", "url", "cpu", "nmt", "id");

    /**
     * A display name built from the tool name: {@code profiles_summary} reads as
     * {@code Profiles: Summary}, {@code heap_getPathToGCRoot} as {@code Heap: Get Path To GC Root}.
     * <p>
     * Derived rather than annotated on all hundred-odd methods. A title is a label, and one written by
     * hand beside every {@code @Tool} would be a hundred more strings to keep in step with the names
     * they already repeat; deriving it means it cannot disagree with the name, which is the only thing
     * a reader actually matches on.
     */
    private static String titleFor(String name) {
        int separator = name.indexOf('_');
        if (separator < 1 || separator == name.length() - 1) {
            return capitalize(name);
        }
        String family = name.substring(0, separator);
        return FAMILY_TITLES.getOrDefault(family, capitalize(family))
                + ": " + splitCamelCase(name.substring(separator + 1));
    }


    /** {@code getPathToGCRoot} to {@code Get Path To GC Root}, keeping runs of capitals together. */
    private static String splitCamelCase(String identifier) {
        StringBuilder words = new StringBuilder(identifier.length() + 8);
        for (int i = 0; i < identifier.length(); i++) {
            char current = identifier.charAt(i);
            boolean startsWord = i > 0
                    && Character.isUpperCase(current)
                    && (!Character.isUpperCase(identifier.charAt(i - 1))
                            || (i + 1 < identifier.length() && Character.isLowerCase(identifier.charAt(i + 1))));
            if (startsWord) {
                words.append(' ');
            }
            words.append(i == 0 ? Character.toUpperCase(current) : current);
        }
        return capitalizeAcronyms(words.toString());
    }

    /** Uppercases any whole word that is one of {@link #ACRONYMS}, leaving the rest untouched. */
    private static String capitalizeAcronyms(String title) {
        String[] words = title.split(" ");
        StringBuilder result = new StringBuilder(title.length());
        for (int i = 0; i < words.length; i++) {
            if (i > 0) {
                result.append(' ');
            }
            String word = words[i];
            result.append(ACRONYMS.contains(word.toLowerCase(Locale.ROOT)) ? word.toUpperCase(Locale.ROOT) : word);
        }
        return result.toString();
    }

    private static String capitalize(String word) {
        if (word.isEmpty()) {
            return word;
        }
        return Character.toUpperCase(word.charAt(0)) + word.substring(1);
    }
}
