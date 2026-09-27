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

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * How Jeffrey names its tools: {@code <family>_<name>}, where the family is the tool-name prefix every
 * {@code @Tool} class is registered under ({@code heap_getClassHistogram}), and a display title derived
 * from that name ({@code Heap: Get Class Histogram}). A convention of Jeffrey's, not of the protocol: the
 * protocol takes whatever name and title the server gives it.
 */
public final class McpToolNames {

    /** What separates a tool's family from the rest of its name: {@code heap_getClassHistogram}. */
    private static final char FAMILY_SEPARATOR = '_';

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

    private McpToolNames() {
    }

    /**
     * The family a tool belongs to: the text of its name before the first underscore, or the whole
     * name when it has none. The one place a tool name is read this way — the family filter, the
     * next-step gate, the server info and the title all ask here.
     */
    public static String familyOf(String toolName) {
        int separator = toolName.indexOf(FAMILY_SEPARATOR);
        return separator < 0 ? toolName : toolName.substring(0, separator);
    }

    /**
     * A display name built from the tool name: {@code profiles_summary} reads as
     * {@code Profiles: Summary}, {@code heap_getPathToGCRoot} as {@code Heap: Get Path To GC Root}.
     * <p>
     * Derived rather than annotated on all hundred-odd methods. A title is a label, and one written by
     * hand beside every {@code @Tool} would be a hundred more strings to keep in step with the names
     * they already repeat; deriving it means it cannot disagree with the name, which is the only thing
     * a reader actually matches on.
     */
    public static String titleFor(String name) {
        String family = familyOf(name);
        String rest = name.substring(Math.min(family.length() + 1, name.length()));
        if (family.isEmpty() || rest.isEmpty()) {
            return capitalize(name);
        }
        return FAMILY_TITLES.getOrDefault(family, capitalize(family)) + ": " + splitCamelCase(rest);
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
