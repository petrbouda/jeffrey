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

package cafe.jeffrey.microscope.core.mcp.tools.jvm;

import cafe.jeffrey.microscope.core.mcp.MicroscopeView;
import cafe.jeffrey.microscope.core.mcp.tools.NextSteps;
import cafe.jeffrey.microscope.mcp.protocol.McpDescription;
import cafe.jeffrey.microscope.mcp.protocol.McpNullable;
import cafe.jeffrey.microscope.model.Type;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.manager.model.classloading.ClassLoadActivity;
import cafe.jeffrey.profile.manager.model.classloading.ClassLoaderStat;
import cafe.jeffrey.profile.manager.model.classloading.ClassLoadingOverview;
import cafe.jeffrey.profile.manager.model.classloading.RedefinitionData;
import cafe.jeffrey.profile.mcp.McpFollowUp;

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
public record ClassLoadingSection(ProfileManager profileManager) implements JvmSection<ClassLoadingSection.ClassLoadingDashboard> {

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

    private static final String LOADER_LEAK_WHY =
            "names a class loader that should have gone away and the GC-root path keeping it, on a "
                    + "profile that has an indexed heap dump";
    private static final String START_UP_WHY =
            "shows whether the CPU concentrates in the start-up window, where loading happens, before "
                    + "concluding that loading matters";
    private static final String AGENT_GUIDANCE =
            "Redefinitions and retransforms come from an agent. Their count climbing during a run means "
                    + "something is instrumenting continuously, which costs both time and metaspace.";

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
    public MicroscopeView view() {
        return MicroscopeView.CLASS_LOADING;
    }

    @Override
    public void followUp(NextSteps.Builder next, ClassLoadingDashboard dashboard) {
        String profileId = profileManager.info().id();
        next.next(SectionCalls.on(SectionCalls.HEAP_CLASS_LOADER_LEAK_CHAINS, profileId).why(LOADER_LEAK_WHY));
        SectionCalls.onCpu(next, profileManager, eventType -> SectionCalls.on(SectionCalls.TIMELINE_HOT_WINDOWS, profileId)
                .with(SectionCalls.EVENT_TYPE, eventType)
                .why(START_UP_WHY));
        next.guidanceWhen(!dashboard.redefinitions().isEmpty(), AGENT_GUIDANCE);
    }

    @Override
    public ClassLoadingDashboard render() {
        ClassLoadingOverview overview = profileManager.classLoadingManager().overview();
        ClassLoadActivity activity = profileManager.classLoadingManager().classLoadActivity();
        RedefinitionData redefinitions = profileManager.classLoadingManager().redefinitions();
        List<ClassLoaderStat> loaders = profileManager.classLoadingManager().classLoaders();

        return new ClassLoadingDashboard(
                overview.currentlyLoaded(),
                overview.totalLoaded(),
                overview.totalUnloaded(),
                overview.classLoaderCount(),
                overview.metaspaceUsedBytes(),
                overview.hiddenClassCount(),
                overview.hasClassLoadEvents(),
                loaders(loaders),
                Math.max(0, loaders.size() - LOADERS_LIMIT),
                activity.totalCount(),
                slowestLoads(activity),
                redefinitions(redefinitions));
    }

    private static List<Loader> loaders(List<ClassLoaderStat> loaders) {
        return loaders.stream()
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
    public record ClassLoadingDashboard(
            long currentlyLoaded,
            long totalLoaded,
            long totalUnloaded,
            int classLoaderCount,
            long metaspaceUsedBytes,
            long hiddenClassCount,
            boolean slowLoadsRecorded,
            @McpDescription("The " + LOADERS_LIMIT + " class loaders holding the most classes")
            List<Loader> loaders,
            @McpDescription("How many further class loaders the list leaves out")
            int omittedLoaders,
            long classLoadEvents,
            @McpDescription("The " + SLOWEST_LOADS_LIMIT + " slowest class loads, out of classLoadEvents")
            List<SlowLoad> slowestLoads,
            @McpDescription("The first " + REDEFINITIONS_LIMIT + " redefined classes")
            List<Redefinition> redefinitions) {
    }

    public record Loader(
            @McpNullable
            String name,
            @McpNullable
            String parentName, long classCount, long metaspaceBytes, long hiddenClassCount) {
    }

    public record SlowLoad(
            @McpNullable
            String className,
            double durationMs,
            @McpNullable
            String definingClassLoader) {
    }

    public record Redefinition(
            @McpNullable
            String className,
            int modificationCount) {
    }

    /**
     * What {@code jvm_classLoading} answers: the envelope every section shares, around this section's dashboard.
     */
    public record Answer(
            SectionStatus status,
            @McpNullable
            @McpDescription(SectionHeader.REASON)
            String reason,
            String profileId,
            @McpDescription(SectionHeader.SECTION)
            String section,
            String title,
            @McpNullable
            @McpDescription(SectionHeader.DASHBOARD)
            ClassLoadingDashboard dashboard,
            McpFollowUp followUp,
            @McpDescription(SectionHeader.UI_LINK)
            String uiLink) {

        public static Answer of(SectionHeader header, ClassLoadingDashboard dashboard) {
            return new Answer(header.status(), header.reason(), header.profileId(), header.section(),
                    header.title(), dashboard, header.followUp(), header.uiLink());
        }
    }
}
