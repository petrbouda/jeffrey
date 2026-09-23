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

package cafe.jeffrey.provisioner.config;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * The {@code JEFFREY_*} environment variables as a HOCON configuration layer.
 *
 * <p>This is the whole environment contract in one table: which variable feeds which setting, and
 * how its text is read. Precedence is expressed by where the layer sits in the merge rather than by
 * a check at each read site: this layer sits on top, so it wins over a configuration file, which in
 * turn wins over the built-in defaults ({@link cafe.jeffrey.provisioner.InitConfig}).
 *
 * <p>Every setting is reachable from the environment, so a container never needs to mount a file.
 * Each variable is named after the HOCON key it feeds, so {@code JEFFREY_PROFILER_COMMAND} carries
 * {@code profiler-command}. The resolved flags the run produces are an output and are never read
 * back in: they reach the JVM through the argfile, or through {@code JDK_JAVA_OPTIONS}.
 */
public abstract class EnvironmentLayer {

    static final String ORIGIN_DESCRIPTION = "JEFFREY_* environment variables";

    /**
     * Spelled exactly as the HOCON key it switches ({@code tracing.enabled},
     * {@link ConfigPaths#TRACING_ENABLED}), so the two names match rather than needing a
     * translation table. The UI feature and its REST path stay "method-tracing".
     */
    private static final String ENV_TRACING_ENABLED = "JEFFREY_TRACING_ENABLED";

    private static final List<EnvBinding> BINDINGS = List.of(
            new EnvBinding.Value("JEFFREY_HOME", ConfigPaths.JEFFREY_HOME),
            new EnvBinding.Value("JEFFREY_WORKSPACES_DIR", ConfigPaths.WORKSPACES_DIR),
            new EnvBinding.Value("JEFFREY_PROFILER_PATH", ConfigPaths.PROFILER_PATH),
            new EnvBinding.Value("JEFFREY_PROFILER_COMMAND", ConfigPaths.PROFILER_COMMAND),
            new EnvBinding.Flag("JEFFREY_HEARTBEAT_ENABLED", ConfigPaths.HEARTBEAT_ENABLED),
            new EnvBinding.Value("JEFFREY_REPOSITORY_TYPE", ConfigPaths.REPOSITORY_TYPE),
            new EnvBinding.Value("JEFFREY_ARG_FILE", ConfigPaths.ARG_FILE),
            new EnvBinding.Value("JEFFREY_ENV_FILE", ConfigPaths.ENV_FILE),
            new EnvBinding.Value("JEFFREY_ADDITIONAL_JVM_OPTIONS", ConfigPaths.ADDITIONAL_JVM_OPTIONS),
            new EnvBinding.Value("JEFFREY_PROJECT_NAME", ConfigPaths.PROJECT_NAME),
            new EnvBinding.Value("JEFFREY_PROJECT_LABEL", ConfigPaths.PROJECT_LABEL),
            new EnvBinding.Value("JEFFREY_WORKSPACE_REF_ID", ConfigPaths.PROJECT_WORKSPACE_REF_ID),
            new EnvBinding.Value("JEFFREY_INSTANCE_NAME", ConfigPaths.PROJECT_INSTANCE_NAME),
            new EnvBinding.Flag("JEFFREY_PRINT_ENV", ConfigPaths.PRINT_ENV),
            new EnvBinding.Flag("JEFFREY_PROVISIONER_VERBOSE", ConfigPaths.PROVISIONER_VERBOSE),
            new EnvBinding.Flag("JEFFREY_PERF_COUNTERS", ConfigPaths.PERF_COUNTERS_ENABLED),
            new EnvBinding.Flag(ENV_TRACING_ENABLED, ConfigPaths.TRACING_ENABLED),
            new EnvBinding.Value("JEFFREY_TRACING_JFR_EVENT_SETTINGS", ConfigPaths.TRACING_JFR_EVENT_SETTINGS),
            new EnvBinding.Flag("JEFFREY_JDK_JAVA_OPTIONS", ConfigPaths.JDK_JAVA_OPTIONS_ENABLED),
            new EnvBinding.Flag("JEFFREY_DEBUG_NON_SAFEPOINTS", ConfigPaths.DEBUG_NON_SAFEPOINTS_ENABLED),
            new EnvBinding.Attributes("JEFFREY_ATTRIBUTES", ConfigPaths.ATTRIBUTES),
            new EnvBinding.HeapDump("JEFFREY_HEAP_DUMP", ConfigPaths.HEAP_DUMP_ENABLED, ConfigPaths.HEAP_DUMP_TYPE));
    /**
     * Reads every bound variable that is set. A variable that is absent, blank, or carries a value
     * its binding cannot read contributes nothing, leaving the setting to the layer below.
     */
    public static Config of(Function<String, String> envLookup) {
        // Declaration order, so a generated config reads like the binding table above.
        Map<String, Object> entries = new LinkedHashMap<>();
        for (EnvBinding binding : BINDINGS) {
            String rawValue = envLookup.apply(binding.envName());
            if (rawValue != null && !rawValue.isBlank()) {
                binding.bind(rawValue, entries);
            }
        }
        return ConfigFactory.parseMap(entries, ORIGIN_DESCRIPTION);
    }
}
