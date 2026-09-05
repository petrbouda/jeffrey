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
import cafe.jeffrey.profile.manager.model.exceptions.ExceptionTypeStat;
import cafe.jeffrey.profile.manager.model.exceptions.ExceptionsOverview;
import cafe.jeffrey.shared.common.model.Type;

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
 */
public record ExceptionsSection(ProfileManager profileManager) implements JvmSection {

    public static final String ID = "exceptions";

    private static final String TITLE = "Exceptions";

    private static final int TOP_TYPES_LIMIT = 20;
    private static final int MESSAGES_LIMIT = 3;

    private static final Set<Type> EVENT_TYPES = Set.of(
            Type.EXCEPTION_STATISTICS,
            Type.JAVA_EXCEPTION_THROW,
            Type.JAVA_ERROR_THROW);

    private static final List<String> NEXT_STEPS = List.of(
            "A high total with no types named means jdk.JavaExceptionThrow was not recorded — the "
                    + "count is real, the attribution is missing, and only a new recording fixes that.",
            "Constructing an exception walks the stack, so a hot throw site shows up as "
                    + "fillInStackTrace in an on-CPU flamegraph: flamegraph_export with eventType "
                    + "jdk.ExecutionSample and a search for fillInStackTrace finds it.",
            "Exceptions crossing a traced boundary are counted per operation in traces_operations, "
                    + "which says which request they belong to.");

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
        ExceptionsOverview overview = profileManager.exceptionsManager().overview();
        return new ExceptionsDashboard(
                overview.totalThrowables(),
                overview.sampledThrowCount(),
                overview.errorCount(),
                overview.distinctTypes(),
                overview.hasExceptionThrowEvents(),
                overview.hasErrorThrowEvents(),
                topTypes());
    }

    private List<ThrownType> topTypes() {
        return profileManager.exceptionsManager().topTypes().stream()
                .limit(TOP_TYPES_LIMIT)
                .map(ExceptionsSection::thrownType)
                .toList();
    }

    private static ThrownType thrownType(ExceptionTypeStat stat) {
        return new ThrownType(
                stat.thrownClass(),
                stat.count(),
                stat.error(),
                stat.threadCount(),
                stat.messages().stream()
                        .limit(MESSAGES_LIMIT)
                        .map(message -> new Message(message.message(), message.count()))
                        .toList());
    }

    /**
     * @param sampledThrows how many throws were actually captured, which is what the type list is
     *                      built from — well below {@code totalThrowables} whenever the throw events
     *                      were not recorded
     */
    private record ExceptionsDashboard(
            long totalThrowables,
            long sampledThrows,
            long errors,
            int distinctTypes,
            boolean exceptionThrowsRecorded,
            boolean errorThrowsRecorded,
            List<ThrownType> topTypes) {
    }

    private record ThrownType(
            String thrownClass, long count, boolean error, int threadCount, List<Message> messages) {
    }

    private record Message(String message, long count) {
    }
}
