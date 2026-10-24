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

package cafe.jeffrey.profile.perfcounters;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Typed reads of a perf-data snapshot, where a counter the JVM did not write reads as null. */
public final class PerfCounterValues {

    private final Map<String, Object> values;

    private PerfCounterValues(Map<String, Object> values) {
        this.values = values;
    }

    public static PerfCounterValues of(Map<String, Object> values) {
        return new PerfCounterValues(Map.copyOf(values));
    }

    public static PerfCounterValues of(Collection<PerfCounter> counters) {
        Map<String, Object> values = new HashMap<>();
        for (PerfCounter counter : counters) {
            if (counter.value() != null) {
                values.put(counter.key(), counter.value());
            }
        }
        return new PerfCounterValues(values);
    }

    /** The counter as a number, or null when it is missing or not a number. */
    public Long number(String name) {
        return values.get(name) instanceof Number number ? number.longValue() : null;
    }

    /** The counter as a string, or null when it is missing, blank or not a string. */
    public String text(String name) {
        return values.get(name) instanceof String text && !text.isBlank() ? text : null;
    }

    /**
     * The numbers the first group of {@code pattern} captures across all counter names, ascending: the
     * generations, spaces or collectors this JVM actually wrote, whatever its policy counters claim.
     */
    public SortedSet<Integer> indexes(Pattern pattern) {
        SortedSet<Integer> indexes = new TreeSet<>();
        for (String name : values.keySet()) {
            Matcher matcher = pattern.matcher(name);
            if (matcher.matches()) {
                indexes.add(Integer.parseInt(matcher.group(1)));
            }
        }
        return indexes;
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }
}
