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

/**
 * Every configuration path the provisioner reads. Named once so the environment binding table and
 * the code that reads the merged configuration cannot drift apart — a renamed setting is a compile
 * error on both sides rather than a variable that silently stops working.
 */
public abstract class ConfigPaths {

    public static final String JEFFREY_HOME = "jeffrey-home";
    public static final String WORKSPACES_DIR = "workspaces-dir";
    public static final String PROFILER_PATH = "profiler-path";
    public static final String PROFILER_COMMAND = "profiler-command";
    public static final String HEARTBEAT_ENABLED = "heartbeat.enabled";
    public static final String REPOSITORY_TYPE = "repository-type";
    public static final String ARG_FILE = "arg-file";
    public static final String ENV_FILE = "env-file";
    public static final String PRINT_ENV = "print-env";
    public static final String PROVISIONER_VERBOSE = "provisioner-verbose";
    public static final String ADDITIONAL_JVM_OPTIONS = "additional-jvm-options";
    public static final String ATTRIBUTES = "attributes";

    public static final String PROJECT_NAME = "project.name";
    public static final String PROJECT_LABEL = "project.label";
    public static final String PROJECT_WORKSPACE_REF_ID = "project.workspace-ref-id";
    public static final String PROJECT_INSTANCE_NAME = "project.instance-name";

    public static final String PERF_COUNTERS_ENABLED = "perf-counters.enabled";
    public static final String TRACING_ENABLED = "tracing.enabled";
    public static final String TRACING_JFR_EVENT_SETTINGS = "tracing.jfr-event-settings";
    public static final String JDK_JAVA_OPTIONS_ENABLED = "jdk-java-options.enabled";
    public static final String DEBUG_NON_SAFEPOINTS_ENABLED = "debug-non-safepoints.enabled";
    public static final String HEAP_DUMP_ENABLED = "heap-dump.enabled";
    public static final String HEAP_DUMP_TYPE = "heap-dump.type";
}
