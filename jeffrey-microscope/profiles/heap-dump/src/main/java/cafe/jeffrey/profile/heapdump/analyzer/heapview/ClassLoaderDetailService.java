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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import cafe.jeffrey.profile.heapdump.model.BlockingClass;
import cafe.jeffrey.profile.heapdump.model.ClassEntry;
import cafe.jeffrey.profile.heapdump.model.ClassLoaderDetail;
import cafe.jeffrey.profile.heapdump.model.ClassLoaderUnloadability;
import cafe.jeffrey.profile.heapdump.model.LoaderType;
import cafe.jeffrey.profile.heapdump.model.UnloadabilityVerdict;
import cafe.jeffrey.profile.heapdump.parser.HeapView;
import cafe.jeffrey.profile.heapdump.parser.InstanceFieldValue;

/**
 * On-demand loader for the Class Loader Detail drawer. Pulls one loader's
 * identifying header, its parent reference, the list of classes it defined,
 * and the per-loader unloadability verdict — all directly against the
 * {@link HeapView}, with no caching. Callers must hold an open
 * {@code HeapDumpSession}.
 */
public final class ClassLoaderDetailService {

    private static final long BOOTSTRAP_LOADER_ID = 0L;

    private static final String BOOTSTRAP_DISPLAY_NAME = "<bootstrap>";

    /**
     * Generous safety cap on the per-loader class list. The drawer ships the
     * full list to the browser so client-side search/sort can run over every
     * class without round-trips; only pathological loaders (a synthetic
     * fixture with hundreds of thousands of classes) would actually hit this
     * limit.
     */
    private static final int MAX_CLASSES_PER_LOADER = 50_000;

    private static final int TOP_BLOCKING_CLASSES_PER_LOADER = 5;

    private static final String PARENT_FIELD_NAME = "parent";

    private static final String LOADER_CLASS_NAME_SQL =
            "SELECT c.name FROM instance i JOIN class c ON i.class_id = c.class_id "
                    + "WHERE i.instance_id = ?";

    private static final String LOADER_INSTANCE_COUNT_SQL = """
            SELECT COUNT(i.instance_id)
            FROM class c
            LEFT JOIN instance i ON i.class_id = c.class_id
            WHERE COALESCE(c.classloader_id, 0) = ?
            """;

    private static final String LOADER_CLASS_COUNT_SQL = """
            SELECT COUNT(*)
            FROM class
            WHERE COALESCE(classloader_id, 0) = ?
            """;

    private static final String LOADER_RETAINED_SIZE_SQL =
            "SELECT bytes FROM retained_size WHERE instance_id = ?";

    private static final String CLASSES_FOR_LOADER_SQL = """
            SELECT
                c.class_id                       AS class_id,
                c.name                           AS class_name,
                COUNT(i.instance_id)             AS inst_count,
                COALESCE(SUM(i.shallow_size), 0) AS total_size
            FROM class c
            LEFT JOIN instance i ON i.class_id = c.class_id
            WHERE COALESCE(c.classloader_id, 0) = ?
            GROUP BY c.class_id, c.name
            ORDER BY inst_count DESC, total_size DESC, c.name ASC
            LIMIT ?
            """;

    private static final String TOP_BLOCKING_CLASSES_SQL = """
            SELECT
                c.class_id                       AS class_id,
                c.name                           AS class_name,
                COUNT(i.instance_id)             AS inst_count,
                COALESCE(SUM(i.shallow_size), 0) AS total_size
            FROM class c
            LEFT JOIN instance i ON i.class_id = c.class_id
            WHERE COALESCE(c.classloader_id, 0) = ?
            GROUP BY c.class_id, c.name
            HAVING COUNT(i.instance_id) > 0
            ORDER BY inst_count DESC
            LIMIT ?
            """;

    private ClassLoaderDetailService() {
    }

    public static Optional<ClassLoaderDetail> loadDetail(HeapView view, long loaderId) throws SQLException {
        if (loaderId != BOOTSTRAP_LOADER_ID && !loaderExists(view, loaderId)) {
            return Optional.empty();
        }

        String displayName = resolveDisplayName(view, loaderId);
        long parentLoaderId = loaderId == BOOTSTRAP_LOADER_ID
                ? BOOTSTRAP_LOADER_ID
                : readParentLoaderId(view, loaderId);
        String parentDisplayName = parentLoaderId == BOOTSTRAP_LOADER_ID
                ? BOOTSTRAP_DISPLAY_NAME
                : resolveDisplayName(view, parentLoaderId);

        LoaderType type = LoaderTypeClassifier.classify(loaderId, displayName);
        long instanceCount = lookupCount(view, LOADER_INSTANCE_COUNT_SQL, loaderId);
        int classCount = (int) lookupCount(view, LOADER_CLASS_COUNT_SQL, loaderId);
        long retainedSize = loaderId == BOOTSTRAP_LOADER_ID ? 0L : lookupRetainedSize(view, loaderId);

        boolean rooted = loaderId == BOOTSTRAP_LOADER_ID || view.isGcRoot(loaderId);
        List<BlockingClass> topBlocking = readTopBlockingClasses(view, loaderId);
        UnloadabilityVerdict verdict;
        if (rooted) {
            verdict = UnloadabilityVerdict.PINNED_ROOTED;
        } else if (instanceCount > 0) {
            verdict = UnloadabilityVerdict.PINNED_TRANSITIVE;
        } else {
            verdict = UnloadabilityVerdict.UNLOADABLE;
        }
        ClassLoaderUnloadability unloadability =
                new ClassLoaderUnloadability(verdict, instanceCount, rooted, topBlocking);

        List<ClassEntry> classes = readClasses(view, loaderId);

        return Optional.of(new ClassLoaderDetail(
                loaderId,
                displayName,
                parentLoaderId,
                parentDisplayName,
                type,
                unloadability,
                retainedSize,
                classCount,
                instanceCount,
                classes));
    }

    private static boolean loaderExists(HeapView view, long loaderId) throws SQLException {
        return view.findInstanceById(loaderId).isPresent();
    }

    private static String resolveDisplayName(HeapView view, long loaderId) throws SQLException {
        if (loaderId == BOOTSTRAP_LOADER_ID) {
            return BOOTSTRAP_DISPLAY_NAME;
        }
        try (PreparedStatement stmt = view.databaseClient().connection().prepareStatement(LOADER_CLASS_NAME_SQL)) {
            stmt.setLong(1, loaderId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getString(1) : "<unresolved>";
            }
        }
    }

    private static long readParentLoaderId(HeapView view, long loaderId) {
        try {
            List<InstanceFieldValue> fields = view.readInstanceFields(loaderId);
            for (InstanceFieldValue field : fields) {
                if (PARENT_FIELD_NAME.equals(field.name()) && field.value() instanceof Long ref) {
                    return ref;
                }
            }
        } catch (SQLException | RuntimeException ignored) {
            // Fall through to bootstrap when the parent field can't be read.
        }
        return BOOTSTRAP_LOADER_ID;
    }

    private static long lookupCount(HeapView view, String sql, long loaderId) throws SQLException {
        try (PreparedStatement stmt = view.databaseClient().connection().prepareStatement(sql)) {
            stmt.setLong(1, loaderId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        }
    }

    private static long lookupRetainedSize(HeapView view, long loaderId) throws SQLException {
        try (PreparedStatement stmt = view.databaseClient().connection().prepareStatement(LOADER_RETAINED_SIZE_SQL)) {
            stmt.setLong(1, loaderId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        }
    }

    private static List<ClassEntry> readClasses(HeapView view, long loaderId) throws SQLException {
        List<ClassEntry> out = new ArrayList<>();
        try (PreparedStatement stmt = view.databaseClient().connection().prepareStatement(CLASSES_FOR_LOADER_SQL)) {
            stmt.setLong(1, loaderId);
            stmt.setInt(2, MAX_CLASSES_PER_LOADER);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    out.add(new ClassEntry(
                            rs.getLong(1),
                            rs.getString(2),
                            rs.getLong(3),
                            rs.getLong(4)));
                }
            }
        }
        return out;
    }

    private static List<BlockingClass> readTopBlockingClasses(HeapView view, long loaderId) throws SQLException {
        List<BlockingClass> out = new ArrayList<>();
        try (PreparedStatement stmt = view.databaseClient().connection().prepareStatement(TOP_BLOCKING_CLASSES_SQL)) {
            stmt.setLong(1, loaderId);
            stmt.setInt(2, TOP_BLOCKING_CLASSES_PER_LOADER);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    out.add(new BlockingClass(
                            rs.getLong(1),
                            rs.getString(2),
                            rs.getLong(3),
                            rs.getLong(4)));
                }
            }
        }
        return out;
    }
}
