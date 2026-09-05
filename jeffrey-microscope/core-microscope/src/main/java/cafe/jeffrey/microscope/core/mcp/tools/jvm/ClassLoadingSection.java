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
import cafe.jeffrey.profile.manager.model.classloading.ClassLoadActivity;
import cafe.jeffrey.profile.manager.model.classloading.ClassLoaderStat;
import cafe.jeffrey.profile.manager.model.classloading.ClassLoadingOverview;
import cafe.jeffrey.profile.manager.model.classloading.RedefinitionData;
import cafe.jeffrey.shared.common.model.Type;

import java.util.List;
import java.util.Set;

/**
 * What the JVM loaded, and who loaded it.
 * <p>
 * Class loading answers two questions a flamegraph cannot. The first is start-up: a process that
 * spends its first seconds loading tens of thousands of classes is not slow in any method, it is slow
 * in the loader. The second is metaspace, which grows with the classes held and with the loaders
 * holding them — a count of loaders that keeps climbing across redeploys is the signature of a leaked
 * one, and the class-loader analysis of a heap dump is where that gets confirmed.
 * <p>
 * The slowest individual loads come from {@code jdk.ClassLoad}, which is off by default because it
 * fires per class; its absence is reported rather than shown as no slow loads.
 */
public record ClassLoadingSection(ProfileManager profileManager) implements JvmSection {

    public static final String ID = "classLoading";

    private static final String TITLE = "Class Loading";

    private static final int LOADERS_LIMIT = 20;
    private static final int SLOWEST_LOADS_LIMIT = 15;
    private static final int REDEFINITIONS_LIMIT = 15;
    private static final double NANOS_IN_MILLI = 1_000_000d;

    private static final Set<Type> EVENT_TYPES = Set.of(
            Type.CLASS_LOADING_STATISTICS,
            Type.CLASS_LOADER_STATISTICS,
            Type.CLASS_LOAD,
            Type.CLASS_DEFINE,
            Type.CLASS_UNLOAD,
            Type.CLASS_REDEFINITION,
            Type.RETRANSFORM_CLASSES);

    private static final List<String> NEXT_STEPS = List.of(
            "Metaspace held by loaders that should have gone away is a heap-dump question: "
                    + "heap_getClassLoaderLeakChains names the loader and the GC-root path keeping it "
                    + "alive, on a profile that has a dump.",
            "Loading dominates start-up rather than steady state, so compare a window against the whole "
                    + "recording with timeline_hotWindows before concluding it matters.",
            "Redefinitions and retransforms come from an agent. Their count climbing during a run means "
                    + "something is instrumenting continuously, which costs both time and metaspace.");

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

    @Override
    public Object render() {
        ClassLoadingOverview overview = profileManager.classLoadingManager().overview();
        ClassLoadActivity activity = profileManager.classLoadingManager().classLoadActivity();
        RedefinitionData redefinitions = profileManager.classLoadingManager().redefinitions();

        return new ClassLoadingDashboard(
                overview.currentlyLoaded(),
                overview.totalLoaded(),
                overview.totalUnloaded(),
                overview.classLoaderCount(),
                overview.metaspaceUsedBytes(),
                overview.hiddenClassCount(),
                overview.hasClassLoadEvents(),
                loaders(),
                activity.totalCount(),
                slowestLoads(activity),
                redefinitions(redefinitions));
    }

    private List<Loader> loaders() {
        return profileManager.classLoadingManager().classLoaders().stream()
                .limit(LOADERS_LIMIT)
                .map(ClassLoadingSection::loader)
                .toList();
    }

    private static Loader loader(ClassLoaderStat stat) {
        return new Loader(
                stat.name(),
                stat.parentName(),
                stat.classCount(),
                stat.metaspaceBytes(),
                stat.hiddenClassCount());
    }

    private static List<SlowLoad> slowestLoads(ClassLoadActivity activity) {
        return activity.slowest().stream()
                .limit(SLOWEST_LOADS_LIMIT)
                .map(entry -> new SlowLoad(
                        entry.className(),
                        entry.durationNanos() / NANOS_IN_MILLI,
                        entry.definingClassLoader()))
                .toList();
    }

    private static List<Redefinition> redefinitions(RedefinitionData data) {
        return data.redefinitions().stream()
                .limit(REDEFINITIONS_LIMIT)
                .map(stat -> new Redefinition(stat.className(), stat.modificationCount()))
                .toList();
    }

    /**
     * @param slowLoadsRecorded false when jdk.ClassLoad was not captured, which is the usual case and
     *                          is why an empty slowestLoads list is not evidence that loading was fast
     */
    private record ClassLoadingDashboard(
            long currentlyLoaded,
            long totalLoaded,
            long totalUnloaded,
            int classLoaderCount,
            long metaspaceUsedBytes,
            long hiddenClassCount,
            boolean slowLoadsRecorded,
            List<Loader> loaders,
            long classLoadEvents,
            List<SlowLoad> slowestLoads,
            List<Redefinition> redefinitions) {
    }

    private record Loader(
            String name, String parentName, long classCount, long metaspaceBytes, long hiddenClassCount) {
    }

    private record SlowLoad(String className, double durationMillis, String definingClassLoader) {
    }

    private record Redefinition(String className, int modificationCount) {
    }
}
