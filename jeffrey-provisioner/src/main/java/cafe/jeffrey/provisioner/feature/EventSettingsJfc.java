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

package cafe.jeffrey.provisioner.feature;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Renders {@code event#setting=value} entries, comma-separated as {@code -XX:StartFlightRecording}
 * spells them, as a JFR configuration ({@code .jfc}) holding those events and nothing else.
 *
 * <p>Every listed event is enabled: the configuration feeds a recording of its own, and JFR records
 * an event while <em>any</em> recording enables it, so the recording can switch events on and
 * lower their thresholds but never switch off one the profiler records. {@code enabled=false} is
 * therefore refused rather than silently ignored.
 */
public abstract class EventSettingsJfc {

    private static final String ENTRY_SEPARATOR = ",";
    private static final char SETTING_SEPARATOR = '#';
    private static final char ASSIGN = '=';
    private static final String ENABLED_SETTING = "enabled";
    private static final String ENABLED_VALUE = "true";
    private static final String CANNOT_DISABLE =
            "JFR event settings can only enable events, a custom .jfc in profiler-command disables them: entry=";

    /**
     * @throws IllegalArgumentException for an entry without an {@code event#setting} part, or one
     *                                  that switches an event off
     */
    public static String render(String eventSettings) {
        Map<String, Map<String, String>> events = new LinkedHashMap<>();
        for (String entry : eventSettings.split(ENTRY_SEPARATOR)) {
            String trimmed = entry.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int separator = trimmed.indexOf(SETTING_SEPARATOR);
            int assign = trimmed.indexOf(ASSIGN, separator + 1);
            if (separator <= 0 || assign < 0 || assign == separator + 1) {
                throw new IllegalArgumentException(
                        "A JFR event setting is spelled event#setting=value: entry=" + trimmed);
            }
            String setting = trimmed.substring(separator + 1, assign);
            String value = trimmed.substring(assign + 1);
            if (setting.equals(ENABLED_SETTING) && !value.equals(ENABLED_VALUE)) {
                throw new IllegalArgumentException(CANNOT_DISABLE + trimmed);
            }
            events.computeIfAbsent(trimmed.substring(0, separator), EventSettingsJfc::enabledEvent)
                    .put(setting, value);
        }

        StringBuilder jfc = new StringBuilder("""
                <?xml version="1.0" encoding="UTF-8"?>
                <configuration version="2.0" label="Jeffrey events" provider="Jeffrey">
                """);
        events.forEach((event, settings) -> {
            jfc.append("  <event name=\"").append(escape(event)).append("\">\n");
            settings.forEach((setting, value) -> jfc
                    .append("    <setting name=\"").append(escape(setting)).append("\">")
                    .append(escape(value)).append("</setting>\n"));
            jfc.append("  </event>\n");
        });
        return jfc.append("</configuration>\n").toString();
    }

    private static Map<String, String> enabledEvent(String event) {
        Map<String, String> settings = new LinkedHashMap<>();
        settings.put(ENABLED_SETTING, ENABLED_VALUE);
        return settings;
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
