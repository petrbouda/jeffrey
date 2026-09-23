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

package cafe.jeffrey.provisioner;

import cafe.jeffrey.provisioner.config.ConfigPaths;
import cafe.jeffrey.provisioner.feature.JvmFeature;
import cafe.jeffrey.shared.common.CliConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Predicate;

/**
 * Decides which async-profiler library a session loads and with which options.
 *
 * <p>{@code profiler-command} (or {@code JEFFREY_PROFILER_COMMAND}) takes two forms:
 * <ul>
 *   <li>{@code -agentpath:<library>=<options>} names its own library, used as given;</li>
 *   <li>{@code <options>} alone runs on the library {@code profiler-path}
 *       ({@code JEFFREY_PROFILER_PATH}) names.</li>
 * </ul>
 * Left unset, the {@code profiler-path} library runs with
 * {@link CliConstants#DEFAULT_PROFILER_OPTIONS}. An {@code -agentpath:} naming
 * {@code <<JEFFREY:PROFILER_PATH>>} means that library too.
 *
 * <p>{@code profiler-path} is how the library is normally located: jeffrey-jib bakes
 * async-profiler into the image and sets {@code JEFFREY_PROFILER_PATH} to it, and a deployment
 * that ships its own async-profiler points the same setting at that copy. Whenever that library
 * is the one needed and it is not there, profiling is switched off with a warning rather than
 * handing the JVM an {@code -agentpath} it cannot load, which would stop the application from
 * starting. A library named inside the command is not checked: whoever named it owns that path.
 */
public class AsyncProfilerResolver {

    private static final Logger LOG = LoggerFactory.getLogger(AsyncProfilerResolver.class);

    /** The profiler feature to render together with the source it came from. */
    public record ResolvedProfiler(JvmFeature.AsyncProfiler feature, ProfilerSource source) {

        private static final ResolvedProfiler DISABLED =
                new ResolvedProfiler(JvmFeature.AsyncProfiler.disabled(), ProfilerSource.DISABLED);
    }

    private final Predicate<Path> libraryAvailable;

    public AsyncProfilerResolver() {
        this(Files::isRegularFile);
    }

    AsyncProfilerResolver(Predicate<Path> libraryAvailable) {
        this.libraryAvailable = libraryAvailable;
    }

    /**
     * @param profilerCommand the configured command, or {@code null} when none is
     * @param profilerPath    the {@code libasyncProfiler.so} {@code profiler-path} names, or
     *                        {@code null} when nothing names one
     */
    public ResolvedProfiler resolve(String profilerCommand, String profilerPath) {
        ResolvedProfiler resolved = resolveCommand(profilerCommand, profilerPath);
        LOG.info("Profiler resolved: source={}", resolved.source());
        return resolved;
    }

    private ResolvedProfiler resolveCommand(String profilerCommand, String profilerPath) {
        if (profilerCommand == null || profilerCommand.isBlank()) {
            return atProfilerPath(profilerPath, CliConstants.DEFAULT_PROFILER_OPTIONS, ProfilerSource.BUILT_IN);
        }

        String command = profilerCommand.trim();
        if (!command.startsWith(JvmFeature.AsyncProfiler.AGENT_PATH_OPTION)) {
            return atProfilerPath(profilerPath, command, ProfilerSource.CONFIGURED_OPTIONS);
        }

        String agent = command.substring(JvmFeature.AsyncProfiler.AGENT_PATH_OPTION.length());
        int separator = agent.indexOf(JvmFeature.AsyncProfiler.OPTIONS_SEPARATOR);
        String library = separator < 0 ? agent : agent.substring(0, separator);
        String options = separator < 0 ? null : agent.substring(separator + 1);

        if (CliConstants.PROFILER_PATH.equals(library)) {
            return atProfilerPath(profilerPath, options, ProfilerSource.CONFIGURED_OPTIONS);
        }
        return new ResolvedProfiler(new JvmFeature.AsyncProfiler(library, options), ProfilerSource.AGENT_PATH);
    }

    private ResolvedProfiler atProfilerPath(String library, String options, ProfilerSource source) {
        if (library == null || library.isBlank()) {
            LOG.warn("No async-profiler library is configured, profiling is switched off: setting={}",
                    ConfigPaths.PROFILER_PATH);
            return ResolvedProfiler.DISABLED;
        }
        if (!libraryAvailable.test(Path.of(library))) {
            LOG.warn("async-profiler library not found, profiling is switched off: profiler_path={}", library);
            return ResolvedProfiler.DISABLED;
        }
        return new ResolvedProfiler(new JvmFeature.AsyncProfiler(library, options), source);
    }
}
