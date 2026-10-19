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
import cafe.jeffrey.profile.common.exceptions.ThrowFrame;
import cafe.jeffrey.profile.common.exceptions.ThrowOrigin;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.manager.model.exceptions.ExceptionMessageCount;
import cafe.jeffrey.profile.manager.model.exceptions.ExceptionSiteStat;
import cafe.jeffrey.profile.manager.model.exceptions.ExceptionTypeStat;
import cafe.jeffrey.profile.manager.model.exceptions.ExceptionsOverview;
import cafe.jeffrey.profile.manager.model.exceptions.ThrowAnalysis;
import cafe.jeffrey.profile.mcp.McpFollowUp;
import cafe.jeffrey.provider.profile.api.TraceOperationSortField;

import java.util.List;
import java.util.Set;

/**
 * What the application threw.
 * <p>
 * Exceptions are a cost the profile rarely attributes anywhere else: constructing one walks the stack,
 * and an exception thrown in a loop can dominate a recording while every flamegraph frame above it
 * looks ordinary. The two questions worth separating are how many were thrown and what kinds — a
 * million of one type is a control-flow decision, a scattering of many is error handling doing its job.
 * <p>
 * The counts come from two different sources and they measure different things. {@code totalThrowables}
 * is from {@code jdk.ExceptionStatistics}, which counts every throwable including the ones nothing
 * sampled; the per-type list comes from the throw events, which are off in most recordings. So a
 * profile can honestly report millions of exceptions and name none of them, and that is a finding
 * about the profiler's configuration rather than an empty result.
 * <p>
 * Every throw is also split by origin. {@code JVM_INTERNAL} is the JVM resolving and linking —
 * class loading, reflection, method-handle linking — which throws and catches by the handful and
 * which nobody can act on; everything else, libraries included, is {@code APPLICATION}, because a
 * timeout inside Netty is the application's problem too. {@code topApplicationSites} names where the
 * application throws come from; {@code jvm_exceptionSites} adds their stacks.
 */
public record ExceptionsSection(ProfileManager profileManager) implements JvmSection<ExceptionsSection.ExceptionsDashboard> {

    public static final String ID = "exceptions";

    private static final String TITLE = "Exceptions";

    private static final int TOP_TYPES_LIMIT = 20;
    private static final int TOP_SITES_LIMIT = 10;
    private static final int MESSAGES_LIMIT = 3;

    private static final Set<Type> EVENT_TYPES = Set.of(
            Type.EXCEPTION_STATISTICS,
            Type.JAVA_EXCEPTION_THROW,
            Type.JAVA_ERROR_THROW);

    private static final String THROW_SITE_FRAME = "fillInStackTrace";

    private static final String UNATTRIBUTED_GUIDANCE =
            "The total is counted, but jdk.JavaExceptionThrow was not recorded, so no type is named: the "
                    + "attribution is missing, and only a new recording with that event enabled supplies it.";
    private static final String THROW_SITE_WHY =
            "finds the hot throw sites: constructing an exception walks the stack, which shows up as "
                    + "fillInStackTrace in the on-CPU flamegraph";
    private static final String SITES_WHY =
            "returns the stacks behind these throw sites; pass your own packages as framePrefixes and each "
                    + "site names the frame where your code entered the throw";
    private static final String JVM_INTERNAL_GUIDANCE =
            "jvmInternalThrows are the JVM resolving and linking - class loading, reflection, method-handle "
                    + "linking - thrown and caught inside the JDK; they are noise, so read applicationThrows "
                    + "and topApplicationSites, which keep library throws such as timeouts.";
    private static final String PER_OPERATION_WHY =
            "counts the exceptions crossing a traced boundary per operation, which says which request they "
                    + "belong to";

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
        return MicroscopeView.EXCEPTIONS;
    }

    @Override
    public void followUp(NextSteps.Builder next, ExceptionsDashboard dashboard) {
        String profileId = profileManager.info().id();
        SectionCalls.onCpu(next, profileManager, eventType -> SectionCalls.on(SectionCalls.FLAMEGRAPH_EXPORT, profileId)
                .with(SectionCalls.EVENT_TYPE, eventType)
                .with(SectionCalls.SEARCH, THROW_SITE_FRAME)
                .why(THROW_SITE_WHY));
        List<ApplicationSite> sites = dashboard.topApplicationSites();
        next.nextWhen(!sites.isEmpty(), () -> SectionCalls.on(SectionCalls.JVM_EXCEPTION_SITES, profileId)
                        .with(SectionCalls.THROWN_CLASS, sites.getFirst().thrownClass())
                        .why(SITES_WHY))
                .next(SectionCalls.on(SectionCalls.TRACES_OPERATIONS, profileId)
                        .with(SectionCalls.SORT, TraceOperationSortField.ERRORS)
                        .why(PER_OPERATION_WHY))
                .guidanceWhen(dashboard.totalThrowables() > 0 && !dashboard.exceptionThrowsRecorded(),
                        UNATTRIBUTED_GUIDANCE)
                .guidanceWhen(dashboard.jvmInternalThrows() > 0, JVM_INTERNAL_GUIDANCE);
    }

    @Override
    public ExceptionsDashboard render() {
        ThrowAnalysis analysis = profileManager.exceptionsManager().analysis();
        ExceptionsOverview overview = profileManager.exceptionsManager().overview(analysis);
        return new ExceptionsDashboard(
                overview.totalThrowables(),
                overview.sampledThrowCount(),
                overview.errorCount(),
                overview.distinctTypes(),
                overview.hasExceptionThrowEvents(),
                overview.hasErrorThrowEvents(),
                overview.applicationThrowCount(),
                overview.jvmInternalThrowCount(),
                analysis.types().stream()
                        .limit(TOP_TYPES_LIMIT)
                        .map(ExceptionsSection::thrownType)
                        .toList(),
                analysis.sites().stream()
                        .filter(site -> site.origin() == ThrowOrigin.APPLICATION)
                        .limit(TOP_SITES_LIMIT)
                        .map(ExceptionsSection::applicationSite)
                        .toList());
    }

    private static ThrownType thrownType(ExceptionTypeStat stat) {
        return new ThrownType(
                stat.thrownClass(),
                stat.count(),
                stat.error(),
                stat.threadCount(),
                stat.applicationCount(),
                stat.jvmInternalCount(),
                messages(stat.messages(), MESSAGES_LIMIT));
    }

    private static ApplicationSite applicationSite(ExceptionSiteStat site) {
        return new ApplicationSite(
                site.thrownClass(),
                site.error(),
                Frame.of(site.throwingFrame()),
                site.count(),
                site.threadCount(),
                messages(site.messages(), MESSAGES_LIMIT),
                site.stacktraceId());
    }

    /** The commonest messages, as the answer carries them. */
    static List<Message> messages(List<ExceptionMessageCount> messages, int limit) {
        return messages.stream()
                .limit(limit)
                .map(message -> new Message(message.message(), message.count()))
                .toList();
    }

    /**
     * @param sampledThrows how many throws were actually captured, which is what the type list is
     *                      built from — well below {@code totalThrowables} whenever the throw events
     *                      were not recorded
     */
    public record ExceptionsDashboard(
            long totalThrowables,
            long sampledThrows,
            long errors,
            int distinctTypes,
            boolean exceptionThrowsRecorded,
            boolean errorThrowsRecorded,
            @McpDescription("Sampled throws the application raised, libraries included")
            long applicationThrows,
            @McpDescription("Sampled throws the JVM raised while loading, linking or reflecting - noise")
            long jvmInternalThrows,
            @McpDescription("The " + TOP_TYPES_LIMIT + " types thrown most often, out of distinctTypes")
            List<ThrownType> topTypes,
            @McpDescription("The " + TOP_SITES_LIMIT + " application throw sites (class + throwing frame) "
                    + "thrown most often; jvm_exceptionSites adds their stacks")
            List<ApplicationSite> topApplicationSites) {
    }

    public record ThrownType(
            @McpNullable
            String thrownClass,
            long count,
            boolean error,
            int threadCount,
            @McpDescription("Throws of this type the application raised, libraries included")
            long applicationCount,
            @McpDescription("Throws of this type the JVM raised while loading, linking or reflecting")
            long jvmInternalCount,
            @McpDescription("The " + MESSAGES_LIMIT + " commonest messages")
            List<Message> messages) {
    }

    /**
     * Where an application throw came from: its class and the frame that contained the {@code throw}.
     */
    public record ApplicationSite(
            String thrownClass,
            boolean error,
            @McpNullable
            @McpDescription("The frame that contained the throw, below the exception's constructors; "
                    + "null when no stack was recorded")
            Frame throwingFrame,
            long count,
            int threadCount,
            @McpDescription("The " + MESSAGES_LIMIT + " commonest messages")
            List<Message> messages,
            @McpNullable
            @McpDescription("The site's commonest stack; null when no stack was recorded")
            String stacktraceId) {
    }

    /**
     * One stack frame.
     */
    public record Frame(
            @McpNullable
            @McpDescription("Null for a native frame the recording could not attribute")
            String className,
            @McpNullable
            String methodName,
            @McpNullable
            @McpDescription("Null when the recording carries no line")
            Integer lineNumber) {

        /** The frame as the answer carries it; null for no frame. */
        static Frame of(ThrowFrame frame) {
            return frame == null ? null : new Frame(frame.className(), frame.methodName(), frame.lineNumber());
        }
    }

    public record Message(
            @McpNullable
            String message,
            long count) {
    }

    /**
     * What {@code jvm_exceptions} answers: the envelope every section shares, around this section's dashboard.
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
            ExceptionsDashboard dashboard,
            McpFollowUp followUp,
            @McpDescription(SectionHeader.UI_LINK)
            String uiLink) {

        public static Answer of(SectionHeader header, ExceptionsDashboard dashboard) {
            return new Answer(header.status(), header.reason(), header.profileId(), header.section(),
                    header.title(), dashboard, header.followUp(), header.uiLink());
        }
    }
}
