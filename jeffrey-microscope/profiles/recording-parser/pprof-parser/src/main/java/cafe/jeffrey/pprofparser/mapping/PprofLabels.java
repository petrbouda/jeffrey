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

package cafe.jeffrey.pprofparser.mapping;

import com.google.perftools.profiles.ProfileProto.Label;
import cafe.jeffrey.pprofparser.PprofTables;
import cafe.jeffrey.provider.profile.api.EventThread;
import cafe.jeffrey.shared.common.Json;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Interprets a pprof sample's {@code label} list. pprof labels are the only per-sample context the
 * format carries (there is no dedicated thread message like JFR's {@code RecordedThread}); this
 * <ul>
 *   <li>flattens all labels into the {@code Event.fields} JSON so they stay browsable, and</li>
 *   <li>opportunistically recovers a thread when a producer emits the conventional thread labels
 *       (a numeric {@code tid}/{@code thread_id} plus an optional {@code thread name}).</li>
 * </ul>
 */
public final class PprofLabels {

    private static final Set<String> THREAD_ID_KEYS = Set.of("tid", "thread_id", "threadid", "thread id");
    private static final Set<String> THREAD_NAME_KEYS = Set.of("thread name", "thread_name", "threadname", "thread");

    private PprofLabels() {
    }

    /**
     * @return the labels of {@code sample} flattened into a JSON object (string labels as text,
     * numeric labels as numbers), or an empty object when the sample carries no labels
     */
    public static ObjectNode toFields(List<Label> labels, PprofTables tables) {
        ObjectNode node = Json.createObject();
        for (Label label : labels) {
            String key = tables.string(label.getKey());
            if (key.isBlank()) {
                continue;
            }
            String str = tables.string(label.getStr());
            if (!str.isBlank()) {
                node.put(key, str);
            } else {
                node.put(key, label.getNum());
            }
        }
        return node;
    }

    /**
     * Recovers a per-sample thread from the conventional thread labels, or {@code null} when the
     * sample carries none (the caller then falls back to a synthetic profile-wide thread).
     */
    public static EventThread resolveThread(List<Label> labels, PprofTables tables) {
        Long threadId = null;
        String threadName = null;
        for (Label label : labels) {
            String key = tables.string(label.getKey()).toLowerCase(Locale.ROOT);
            if (THREAD_ID_KEYS.contains(key) && label.getNum() != 0) {
                threadId = label.getNum();
            } else if (THREAD_NAME_KEYS.contains(key)) {
                String value = tables.string(label.getStr());
                if (!value.isBlank()) {
                    threadName = value;
                }
            }
        }

        if (threadId == null && threadName == null) {
            return null;
        }
        String name = threadName != null ? threadName : "[tid=" + threadId + "]";
        return new EventThread(name, threadId, null, false);
    }
}
