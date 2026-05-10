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
package cafe.jeffrey.profile.heapdump.analyzer.heapview;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import cafe.jeffrey.profile.heapdump.model.HeapThreadInfo;
import cafe.jeffrey.profile.heapdump.parser.HeapView;
import cafe.jeffrey.profile.heapdump.parser.HprofTag;
import cafe.jeffrey.profile.heapdump.parser.InstanceFieldValue;

/**
 * HeapView-backed equivalent of
 * {@link cafe.jeffrey.profile.heapdump.analyzer.ThreadAnalyzer}.
 *
 * Discovers thread objects via {@code gc_root} entries with
 * {@code root_kind = ROOT_THREAD_OBJECT} and decodes each one's instance fields.
 * For the {@code name} field (a {@code String} reference) the
 * {@link JavaStringDecoder} is used so the output matches what the JVM would
 * have surfaced.
 *
 * Limitations vs the existing NetBeans-backed analyzer (documented in javadoc):
 * <ul>
 *   <li>Stack frames are <strong>not</strong> populated — the existing path
 *       parses HPROF STACK_TRACE / STACK_FRAME top-level records, which the
 *       new parser currently treats as opaque. Wiring them in is a small
 *       parser extension plus a couple of tables in V001 and stays for a
 *       follow-up PR. The shape of {@link HeapThreadInfo} doesn't carry
 *       stack frames anyway, so this analyzer is feature-equivalent.</li>
 *   <li>{@code retainedSize} is null until callers run
 *       {@link cafe.jeffrey.profile.heapdump.parser.DominatorTreeBuilder}.
 *       When the dominator tree is present, retained size is populated
 *       from the index.</li>
 * </ul>
 */
public final class ThreadAnalyzer {

    private ThreadAnalyzer() {
    }

    public static List<HeapThreadInfo> analyze(HeapView view) throws SQLException {
        boolean haveRetained = view.hasDominatorTree();
        List<HeapThreadInfo> out = new ArrayList<>();

        try (Statement stmt = view.connection().createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT instance_id FROM gc_root WHERE root_kind = "
                             + HprofTag.Sub.ROOT_THREAD_OBJECT)) {
            while (rs.next()) {
                long threadInstanceId = rs.getLong(1);
                Optional<HeapThreadInfo> info = decodeThread(view, threadInstanceId, haveRetained);
                info.ifPresent(out::add);
            }
        }
        return out;
    }

    private static Optional<HeapThreadInfo> decodeThread(
            HeapView view, long instanceId, boolean haveRetained) throws SQLException {
        List<InstanceFieldValue> fields;
        try {
            fields = view.readInstanceFields(instanceId);
        } catch (IllegalStateException noHprof) {
            return Optional.empty();
        }
        if (fields.isEmpty()) {
            return Optional.empty();
        }

        String name = "<unknown>";
        boolean daemon = false;
        int priority = 0;
        for (InstanceFieldValue f : fields) {
            switch (f.name()) {
                case "name" -> {
                    if (f.value() instanceof Long stringRef && stringRef != 0L) {
                        Optional<JavaStringDecoder.Decoded> decoded =
                                JavaStringDecoder.decode(view, stringRef);
                        if (decoded.isPresent()) {
                            name = decoded.get().content();
                        }
                    }
                }
                case "daemon" -> {
                    if (f.value() instanceof Boolean b) {
                        daemon = b;
                    }
                }
                case "priority" -> {
                    if (f.value() instanceof Integer i) {
                        priority = i;
                    }
                }
                default -> {
                    // ignore other Thread fields (group, threadStatus, target, etc.)
                }
            }
        }

        Long retained = haveRetained ? probeRetainedSize(view, instanceId) : null;
        return Optional.of(new HeapThreadInfo(instanceId, name, daemon, priority, retained));
    }

    private static Long probeRetainedSize(HeapView view, long instanceId) throws SQLException {
        try (PreparedStatement stmt = view.connection().prepareStatement(
                "SELECT bytes FROM retained_size WHERE instance_id = ?")) {
            stmt.setLong(1, instanceId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getLong(1) : null;
            }
        }
    }
}
