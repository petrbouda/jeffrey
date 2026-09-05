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
import cafe.jeffrey.profile.manager.gc.GarbageCollectionManager;
import cafe.jeffrey.shared.common.model.Type;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * The garbage-collection pages beneath the overview.
 * <p>
 * {@code jvm_gc} answers the question almost everyone has — what did collection cost, and was it the
 * young generation or the old. These are the pages a reader reaches for once that answer is not
 * enough: the tenuring distribution behind a survivor-space decision, the IHOP behind a concurrent
 * cycle starting too late, the region composition behind an evacuation failure, the allocation stalls
 * behind a ZGC pause. Each is a real page in the Jeffrey UI and none of them belongs in an overview.
 * <p>
 * They are one tool taking a name rather than ten tools, because a reader asks for at most one of them
 * and only after {@code jvm_gc} has pointed there. Ten more entries in every {@code tools/list} would
 * cost every session to serve the few that go this deep — and most are collector-specific, so a G1
 * recording has nothing to say about half of them.
 * <p>
 * Several of these carry a row per collection or per region, so each is trimmed: a recording with ten
 * thousand collections would otherwise render a document nobody can read and the cap would cut it
 * somewhere arbitrary.
 */
public record GcDetailSection(ProfileManager profileManager) implements JvmSection {

    public static final String ID = "gcDetail";

    private static final String TITLE = "Garbage Collection — detail";

    private static final int ROWS_LIMIT = 25;

    private static final Set<Type> EVENT_TYPES = Set.of(
            Type.GARBAGE_COLLECTION,
            Type.YOUNG_GARBAGE_COLLECTION,
            Type.OLD_GARBAGE_COLLECTION,
            Type.G1_GARBAGE_COLLECTION,
            Type.Z_YOUNG_GARBAGE_COLLECTION,
            Type.Z_OLD_GARBAGE_COLLECTION);

    private static final List<String> NEXT_STEPS = List.of(
            "These explain how collection behaved, never what produced the garbage. The call paths are "
                    + "an allocation flamegraph: flamegraph_export with eventType "
                    + "jdk.ObjectAllocationSample and useWeight true.",
            "A collector-specific page on the wrong collector is empty rather than wrong — jvm_gc "
                    + "reports which collector this recording used.",
            "Whether the resulting settings are the ones somebody chose is jvm_flags, which separates a "
                    + "flag that was set from one the JVM's ergonomics picked.");

    /**
     * The pages, in the order a reader meets them. A map rather than a switch so that the names the
     * tool advertises and the work behind them cannot drift apart.
     */
    private static final Map<String, Function<GarbageCollectionManager, Object>> PAGES = pages();

    private static Map<String, Function<GarbageCollectionManager, Object>> pages() {
        Map<String, Function<GarbageCollectionManager, Object>> pages = new LinkedHashMap<>();
        pages.put("configuration", GarbageCollectionManager::configuration);
        pages.put("tenuring", GcDetailSection::tenuring);
        pages.put("ihop", GarbageCollectionManager::ihop);
        pages.put("g1", GarbageCollectionManager::g1Analysis);
        pages.put("zgc", GarbageCollectionManager::zgcAnalysis);
        pages.put("stringTables", GarbageCollectionManager::stringSymbolTables);
        pages.put("finalizers", GarbageCollectionManager::finalizers);
        pages.put("references", GarbageCollectionManager::referenceProcessing);
        pages.put("phases", GcDetailSection::phases);
        pages.put("plab", GcDetailSection::plab);
        return Map.copyOf(pages);
    }

    /**
     * The page names, for the tool to advertise and for a caller that asked for none.
     */
    public static List<String> pageNames() {
        return List.copyOf(pages().keySet());
    }

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

    /**
     * With no page asked for, the answer is which pages there are — the same shape
     * {@code jvm_configuration} uses, so a reader who has met one already knows this one.
     */
    @Override
    public Object render() {
        return pageNames();
    }

    /**
     * @return the page, or {@code null} when no page goes by that name
     */
    public Object page(String name) {
        Function<GarbageCollectionManager, Object> page = PAGES.get(name);
        if (page == null) {
            return null;
        }
        return page.apply(profileManager.gcManager());
    }

    /**
     * Per-collection age histograms, trimmed. A long run produces one of these per young collection.
     */
    private static Object tenuring(GarbageCollectionManager manager) {
        return manager.tenuring().gcs().stream().limit(ROWS_LIMIT).toList();
    }

    private static Object phases(GarbageCollectionManager manager) {
        return manager.phaseParallel().stream().limit(ROWS_LIMIT).toList();
    }

    private static Object plab(GarbageCollectionManager manager) {
        return manager.plabStatistics().stream().limit(ROWS_LIMIT).toList();
    }
}
