/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

package pbouda.jeffrey.cli.commands;

import pbouda.jeffrey.common.EventSummary;
import pbouda.jeffrey.common.Type;

import java.util.*;

public class EventSummariesTablePrinter {

    private static final String[] HEADERS = {"Event Type", "Samples", "Weight", "Extras"};

    public static void print(List<EventSummary> summaries) {
        int eventTypeLength = HEADERS[0].length();
        int samplesLength = HEADERS[1].length();
        int weightLength = HEADERS[2].length();
        int extrasLength = HEADERS[3].length();

        String[][] data = new String[summaries.size()][4];
        for (int i = 0; i < summaries.size(); i++) {
            EventSummary event = summaries.get(i);
            data[i][0] = event.name();
            data[i][1] = String.valueOf(event.samples());
            data[i][2] = formatWeight(event);
            data[i][3] = formatExtras(event.extras());

            eventTypeLength = Math.max(eventTypeLength, data[i][0].length());
            samplesLength = Math.max(samplesLength, data[i][1].length());
            weightLength = Math.max(weightLength, data[i][2].length());
            extrasLength = Math.max(extrasLength, data[i][3].length());
        }

        String formatter = "| %-" + (eventTypeLength) + "s | %-" + (samplesLength)
                + "s | %-" + (weightLength) + "s | %-" + (extrasLength) + "s |%n";

        printDashes(formatter, eventTypeLength, samplesLength, weightLength, extrasLength);
        System.out.printf(formatter, "Event Type", "Samples",  "Weight", "Extras");
        printDashes(formatter, eventTypeLength, samplesLength, weightLength, extrasLength);
        for (String[] datum : data) {
            System.out.printf(formatter, datum[0], datum[1], datum[2], datum[3]);
        }
        printDashes(formatter, eventTypeLength, samplesLength, weightLength, extrasLength);
    }

    private static String formatExtras(Map<String, String> extras) {
        Map<Object, Object> map = new HashMap<>(extras);
        map.putIfAbsent("source", "JDK");
        return map.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
    }

    private static String formatWeight(EventSummary event) {
        Optional<Type> knownType = Type.getKnownType(event.name());
        if (knownType.isPresent() && knownType.get().isWeightSupported()) {
            return knownType.get().weight().formatter().apply(event.weight());
        }
        return "";
    }

    private static void printDashes(String formatter, int eventType, int samples, int weight, int extras) {
        System.out.printf(formatter, "-".repeat(eventType), "-".repeat(samples)
                ,  "-".repeat(weight),  "-".repeat(extras));
    }
}
